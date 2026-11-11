import { PDFDocument, PDFFont, PDFPage, PDFTextField, PDFCheckBox, StandardFonts, LineCapStyle, rgb } from 'pdf-lib';
import { ClaimForm, Signature, fitSignature, signatureIsBlank } from '../core/models';
import { ALL_FIELDS, F, ON, toFieldValues } from '../core/fieldMap';
import { fitText } from './textFit';

export interface FieldInfo { name: string; type: string; multiline: boolean; width: number; height: number }
export interface FillResult { bytes: Uint8Array; pageCount: number; warnings: string[] }

const SIG_PAD_X = 4;
const SIG_PAD_Y = 1;

const REPLACEMENTS: Record<string, string> = {
  '‘': "'", '’': "'", '“': '"', '”': '"', '–': '-', '—': '-', '…': '...', ' ': ' ', '\t': ' ',
};

/** Maps text onto what Helvetica/WinAnsi can draw; anything else becomes '?'. */
export function sanitize(raw: string, keepNewlines: boolean, supported: Set<number>): string {
  let out = '';
  for (const ch of raw.replace(/\r\n/g, '\n').replace(/\r/g, '\n')) {
    if (ch === '\n') out += keepNewlines ? '\n' : ' ';
    else if (ch in REPLACEMENTS) out += REPLACEMENTS[ch];
    else if (supported.has(ch.codePointAt(0)!) && ch >= ' ') out += ch;
    else out += '?';
  }
  return out;
}

/** Catalogue of every terminal field in the template (the "AcroForm key audit"). */
export async function auditTemplate(template: Uint8Array): Promise<FieldInfo[]> {
  const doc = await PDFDocument.load(template);
  return doc.getForm().getFields().map((f) => {
    const rect = f.acroField.getWidgets()[0]?.getRectangle();
    return {
      name: f.getName(),
      type: f.constructor.name.replace(/^PDF/, ''),
      multiline: f instanceof PDFTextField && f.isMultiline(),
      width: rect?.width ?? 0,
      height: rect?.height ?? 0,
    };
  });
}

/** Field names the template has that the app does not map, and vice versa. Both empty when in sync. */
export async function auditDrift(template: Uint8Array): Promise<{ unmapped: string[]; missing: string[] }> {
  const inTemplate = (await auditTemplate(template)).map((f) => f.name).filter((n) => n !== F.SIGNATURE);
  return {
    unmapped: inTemplate.filter((n) => !ALL_FIELDS.includes(n)),
    missing: ALL_FIELDS.filter((n) => !inTemplate.includes(n)),
  };
}

/**
 * Fills the DD Form 2642 template and returns a fully flattened PDF with the signature drawn
 * into block 12a. A signature is mandatory: a claim form is never produced unsigned.
 */
export async function fillForm(
  template: Uint8Array,
  claim: ClaimForm,
  signature: Signature,
  options: { includeInstructions?: boolean } = {},
): Promise<FillResult> {
  if (signatureIsBlank(signature)) throw new Error('The form must be signed before it can be created.');
  const warnings: string[] = [];
  const doc = await PDFDocument.load(template);
  const form = doc.getForm();
  const font = await doc.embedFont(StandardFonts.Helvetica);
  const supported = new Set(font.getCharacterSet());
  const widthEm = (s: string) => font.widthOfTextAtSize(s, 1);

  for (const [name, value] of Object.entries(toFieldValues(claim))) {
    const field = form.getField(name);
    if (field instanceof PDFCheckBox) {
      if (value === ON) field.check(); else field.uncheck();
    } else if (field instanceof PDFTextField) {
      const rect = field.acroField.getWidgets()[0].getRectangle();
      const text = sanitize(value, field.isMultiline(), supported);
      const fit = fitText(text, rect.width, rect.height, field.isMultiline(), widthEm);
      if (!fit.fits) warnings.push(`"${shortName(name)}" is too long for its box and may be clipped.`);
      // Written directly: pdf-lib cannot parse this form's original appearance strings to edit them.
      field.acroField.setDefaultAppearance(`/Helv ${fit.fontSize} Tf 0 g`);
      field.setText(text);
    } else {
      throw new Error(`Template is missing field ${name}`);
    }
  }

  // Locate block 12a before the signature field is removed.
  const sigField = form.getField(F.SIGNATURE);
  const sigWidget = sigField.acroField.getWidgets()[0];
  const sigRect = sigWidget.getRectangle();
  const sigPage = doc.getPages().find((p) => p.ref === sigWidget.P()) ?? doc.getPage(doc.getPageCount() - 1);
  form.removeField(sigField);

  form.updateFieldAppearances(font);
  form.flatten();
  drawSignature(sigPage, sigRect, signature);

  if (!options.includeInstructions && doc.getPageCount() > 1) doc.removePage(0);
  const bytes = await doc.save();
  return { bytes, pageCount: doc.getPageCount(), warnings };
}

function shortName(fullName: string): string {
  return fullName.split('.').pop()!.replace(/\[\d+\]$/, '');
}

function drawSignature(page: PDFPage, rect: { x: number; y: number; width: number; height: number }, signature: Signature) {
  const left = rect.x + SIG_PAD_X;
  const top = rect.y + rect.height - SIG_PAD_Y;
  const strokes = fitSignature(signature, rect.width - 2 * SIG_PAD_X, rect.height - 2 * SIG_PAD_Y);
  const style = { thickness: 0.7, color: rgb(0, 0, 0), lineCap: LineCapStyle.Round };
  for (const stroke of strokes) {
    if (stroke.length === 1) {
      const p = stroke[0];
      page.drawLine({ start: { x: left + p.x, y: top - p.y }, end: { x: left + p.x + 0.01, y: top - p.y }, ...style });
      continue;
    }
    for (let i = 1; i < stroke.length; i++) {
      page.drawLine({
        start: { x: left + stroke[i - 1].x, y: top - stroke[i - 1].y },
        end: { x: left + stroke[i].x, y: top - stroke[i].y },
        ...style,
      });
    }
  }
}

export type { PDFFont };
