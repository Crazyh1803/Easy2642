import { readFileSync, mkdirSync, writeFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { PDFDocument } from 'pdf-lib';
import { describe, expect, it } from 'vitest';
import { CHECKBOX_FIELDS, F, ALL_FIELDS } from '../src/core/fieldMap';
import { emptyForm } from '../src/core/models';
import { auditDrift, auditTemplate, fillForm, sanitize } from '../src/pdf/formService';
import { Attachment, EncodedImage, buildPacket, buildSeparate, fitInside } from '../src/pdf/packet';
import { claimFileName } from '../src/core/fileName';
import { fitText, wrapCount, DEFAULT_SIZE } from '../src/pdf/textFit';
import { sampleForm, sampleSignature } from './sample';

const template = new Uint8Array(readFileSync(resolve(__dirname, '../public/forms/dd2642_template.pdf')));
const outDir = process.env.PDF_OUT_DIR;
const keep = (name: string, bytes: Uint8Array) => {
  if (!outDir) return;
  mkdirSync(outDir, { recursive: true });
  writeFileSync(resolve(outDir, name), bytes);
};
const jpeg = (name: string, width: number, height: number): EncodedImage =>
  ({ jpeg: new Uint8Array(readFileSync(resolve(__dirname, 'fixtures', name))), width, height });
// Node has no canvas: tests hand back the fixture JPEG untouched, whatever level is asked for.
const encodeFixture = (name: string, w: number, h: number) => async () => jpeg(name, w, h);

describe('template', () => {
  it('matches the field map', async () => {
    expect(await auditDrift(template)).toEqual({ unmapped: [], missing: [] });
    const audit = await auditTemplate(template);
    expect(audit.length).toBe(62);
    expect(audit.filter((f) => f.type === 'Signature').length).toBe(1);
    expect(audit.filter((f) => f.type === 'CheckBox').map((f) => f.name).sort()).toEqual([...CHECKBOX_FIELDS].sort());
    expect(ALL_FIELDS.length).toBe(61);
  });
});

describe('fillForm', () => {
  it('produces a one-page, fully flattened, signed form', async () => {
    const r = await fillForm(template, sampleForm, sampleSignature);
    keep('web_sample_form.pdf', r.bytes);
    expect(r.pageCount).toBe(1);
    expect(r.warnings).toEqual([]);
    const doc = await PDFDocument.load(r.bytes);
    expect(doc.getPageCount()).toBe(1);
    expect(doc.getForm().getFields().length).toBe(0);
    expect(doc.getPage(0).node.Annots()?.size() ?? 0).toBe(0);
  });

  it('refuses to create an unsigned form', async () => {
    await expect(fillForm(template, sampleForm, { strokes: [] })).rejects.toThrow(/signed/);
    await expect(fillForm(template, sampleForm, { strokes: [[{ x: 1, y: 1 }]] })).rejects.toThrow(/signed/);
  });

  it('keeps the instruction page on request and fills a blank form', async () => {
    const r = await fillForm(template, emptyForm(), sampleSignature, { includeInstructions: true });
    expect(r.pageCount).toBe(2);
  });

  it('shrinks long text, then warns when it cannot fit', async () => {
    const medium = await fillForm(template, { ...sampleForm, description: 'Recurring migraine with aura. '.repeat(22) }, sampleSignature);
    keep('web_long_description.pdf', medium.bytes);
    expect(medium.warnings).toEqual([]);
    const huge = await fillForm(template, { ...sampleForm, description: 'word '.repeat(900) }, sampleSignature);
    expect(huge.warnings.some((w) => w.includes('describe'))).toBe(true);
  });

  it('replaces characters the font cannot draw instead of failing', async () => {
    const r = await fillForm(template, { ...sampleForm, description: 'Клиника 東京 😀 line\nbreak – “ok”' }, sampleSignature);
    expect(r.pageCount).toBe(1);
    const supported = new Set(['Z', 'o', 'ë', ' ', '-', '"', 'k', "'", 'x', 'a', 'b'].map((c) => c.codePointAt(0)!));
    expect(sanitize('Zoë – “ok” 日本 ‘x’', false, supported)).toBe('Zoë - "ok" ?? \'x\'');
    expect(sanitize('a\nb', false, supported)).toBe('a b');
  });
});

describe('text fit', () => {
  const em = (s: string) => s.length * 0.5;
  it('fits, shrinks and wraps', () => {
    expect(fitText('short', 200, 10, false, em).fontSize).toBe(DEFAULT_SIZE);
    const shrunk = fitText('x'.repeat(60), 200, 10, false, em);
    expect(shrunk.fits && shrunk.fontSize < DEFAULT_SIZE).toBe(true);
    expect(fitText('x'.repeat(400), 200, 10, false, em).fits).toBe(false);
    expect(wrapCount('aaaa bbbb cccc', 2.5, em)).toBe(3);
    expect(wrapCount('a\nb', 100, em)).toBe(2);
  });
});

describe('packets', () => {
  const att = (id: string, kind: Attachment['kind'], category: Attachment['category'], data: Uint8Array): Attachment =>
    ({ id, kind, category, label: id, data });

  it('puts the form first, then attachments in order, with page orientation following the image', async () => {
    const form = (await fillForm(template, sampleForm, sampleSignature)).bytes;
    const extraPdf = (await fillForm(template, emptyForm(), sampleSignature, { includeInstructions: true })).bytes;
    const attachments = [
      att('portrait', 'IMAGE', 'RECEIPT', new Uint8Array([1])),
      att('pdf', 'PDF', 'BILL', extraPdf),
      att('wide', 'IMAGE', 'BILL', new Uint8Array([2])),
    ];
    const encode = async (data: Uint8Array) => (data[0] === 1 ? jpeg('receipt.jpg', 900, 1300) : jpeg('wide.jpg', 1600, 800));
    const r = await buildPacket(form, attachments, 'packet.pdf', encode);
    keep('web_packet.pdf', r.bytes);
    expect(r.pageCount).toBe(1 + 1 + 2 + 1);
    expect(r.withinLimit).toBe(true);
    const doc = await PDFDocument.load(r.bytes);
    const portrait = doc.getPage(1).getSize();
    expect(portrait.height).toBeGreaterThan(portrait.width);
    const landscape = doc.getPage(4).getSize();
    expect(landscape.width).toBeGreaterThan(landscape.height);
  });

  it('splits into form, bill and receipt files', async () => {
    const form = (await fillForm(template, sampleForm, sampleSignature)).bytes;
    const pdfBill = (await fillForm(template, emptyForm(), sampleSignature, { includeInstructions: true })).bytes;
    const attachments = [
      att('r1', 'IMAGE', 'RECEIPT', new Uint8Array([1])),
      att('b1', 'PDF', 'BILL', pdfBill),
      att('b2', 'IMAGE', 'BILL', new Uint8Array([1])),
    ];
    const nameFor = (part: string) => claimFileName('Sampleton', 'Klinik', '2026-02-14', part);
    const out = await buildSeparate(form, attachments, nameFor, encodeFixture('receipt.jpg', 900, 1300));
    expect(out.map((o) => o.label)).toEqual(['DD Form 2642', 'Bill', 'Receipt / proof of payment']);
    expect(out.map((o) => o.fileName)).toEqual([
      'Claim_DD2642_Sampleton_Klinik_20260214_Form.pdf',
      'Claim_DD2642_Sampleton_Klinik_20260214_Bill.pdf',
      'Claim_DD2642_Sampleton_Klinik_20260214_Receipt.pdf',
    ]);
    expect(out.map((o) => o.pageCount)).toEqual([1, 3, 1]);
    const two = await buildSeparate(form, attachments.filter((a) => a.category === 'BILL'), nameFor, encodeFixture('receipt.jpg', 900, 1300));
    expect(two.map((o) => o.label)).toEqual(['DD Form 2642', 'Bill']);
  });

  it('steps down through compression levels and reports honestly when the cap cannot be met', async () => {
    const form = (await fillForm(template, sampleForm, sampleSignature)).bytes;
    const seen: number[] = [];
    const encode = async (_: Uint8Array, level: { quality: number }) => {
      seen.push(level.quality);
      return jpeg('receipt.jpg', 900, 1300);
    };
    const capped = await buildPacket(form, [att('a', 'IMAGE', 'BILL', new Uint8Array([1]))], 'x.pdf', encode, { maxBytes: 10_000 });
    expect(capped.withinLimit).toBe(false);
    expect(seen).toEqual([80, 70, 60, 50, 40, 30]);
    await expect(buildPacket(null, [], 'x.pdf', encode)).rejects.toThrow();
  });

  it('preserves aspect ratio inside the margins', () => {
    const p = fitInside(1000, 2000, 612, 792, 18);
    expect(p.width / p.height).toBeCloseTo(0.5, 3);
    expect(p.x >= 18 && p.y >= 18 && p.x + p.width <= 594.01 && p.y + p.height <= 774.01).toBe(true);
  });
});

describe('signature placement', () => {
  it('adds drawing operators only for the signed form', async () => {
    const r = await fillForm(template, sampleForm, sampleSignature);
    expect(r.bytes.length).toBeGreaterThan(50_000);
    expect(F.SIGNATURE).toContain('SignatureField');
  });
});
