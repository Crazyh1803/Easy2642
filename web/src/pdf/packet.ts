import { PDFDocument } from 'pdf-lib';

export type AttachmentKind = 'IMAGE' | 'PDF';
export type AttachmentCategory = 'BILL' | 'RECEIPT';
export const CATEGORY_LABELS: Record<AttachmentCategory, string> = { BILL: 'Bill', RECEIPT: 'Receipt / proof of payment' };
export const CATEGORY_FILE_PART: Record<AttachmentCategory, string> = { BILL: 'Bill', RECEIPT: 'Receipt' };

/** A supporting document held in memory for the claim being prepared. */
export interface Attachment {
  id: string;
  kind: AttachmentKind;
  label: string;
  category: AttachmentCategory;
  /** Original bytes: an image in any format the browser can decode, or a PDF. */
  data: Uint8Array;
}

export interface PacketResult {
  label: string;
  fileName: string;
  bytes: Uint8Array;
  pageCount: number;
  withinLimit: boolean;
  /** JPEG quality finally used for image pages, or null if there were none. */
  jpegQuality: number | null;
}

export interface Level { maxEdgePx: number; quality: number }
export interface EncodedImage { jpeg: Uint8Array; width: number; height: number }
/** Re-encodes an image attachment as JPEG at the given level. Supplied by the browser layer. */
export type ImageEncoder = (data: Uint8Array, level: Level) => Promise<EncodedImage>;

/** Common claims-portal upload limit. */
export const MAX_BYTES = 10 * 1024 * 1024;
const MARGIN = 18;
const LETTER = { width: 612, height: 792 };
export const LEVELS: Level[] = [
  { maxEdgePx: 2200, quality: 80 }, { maxEdgePx: 2000, quality: 70 }, { maxEdgePx: 1700, quality: 60 },
  { maxEdgePx: 1400, quality: 50 }, { maxEdgePx: 1200, quality: 40 }, { maxEdgePx: 1000, quality: 30 },
];

/** Largest centred rectangle with the image's aspect ratio inside the page margins. */
export function fitInside(imgW: number, imgH: number, pageW: number, pageH: number, margin: number) {
  const scale = Math.min((pageW - 2 * margin) / imgW, (pageH - 2 * margin) / imgH);
  const width = imgW * scale;
  const height = imgH * scale;
  return { x: (pageW - width) / 2, y: (pageH - height) / 2, width, height };
}

async function assemble(formPdf: Uint8Array | null, attachments: Attachment[], level: Level, encode: ImageEncoder) {
  const doc = formPdf ? await PDFDocument.load(formPdf) : await PDFDocument.create();
  for (const a of attachments) {
    if (a.kind === 'IMAGE') {
      const img = await encode(a.data, level);
      const embedded = await doc.embedJpg(img.jpeg);
      const landscape = img.width > img.height;
      const size = landscape ? { width: LETTER.height, height: LETTER.width } : LETTER;
      const page = doc.addPage([size.width, size.height]);
      page.drawImage(embedded, fitInside(img.width, img.height, size.width, size.height, MARGIN));
    } else {
      const src = await PDFDocument.load(a.data);
      const pages = await doc.copyPages(src, src.getPageIndices());
      pages.forEach((p) => doc.addPage(p));
    }
  }
  return { bytes: await doc.save(), pageCount: doc.getPageCount() };
}

/**
 * Writes the form (if any) followed by the attachments. Image pages are recompressed with
 * progressively stronger settings until the file fits maxBytes.
 */
export async function buildPacket(
  formPdf: Uint8Array | null,
  attachments: Attachment[],
  fileName: string,
  encode: ImageEncoder,
  options: { maxBytes?: number; label?: string } = {},
): Promise<PacketResult> {
  if (!formPdf && !attachments.length) throw new Error('Nothing to write');
  const maxBytes = options.maxBytes ?? MAX_BYTES;
  const hasImages = attachments.some((a) => a.kind === 'IMAGE');
  let last: PacketResult | null = null;
  for (const level of hasImages ? LEVELS : LEVELS.slice(0, 1)) {
    const { bytes, pageCount } = await assemble(formPdf, attachments, level, encode);
    last = {
      label: options.label ?? 'Claim packet', fileName, bytes, pageCount,
      withinLimit: bytes.length <= maxBytes, jpegQuality: hasImages ? level.quality : null,
    };
    if (last.withinLimit) break;
  }
  return last!;
}

/** The form, the bill and the receipt / proof of payment as separate PDFs. Empty categories produce no file. */
export async function buildSeparate(
  formPdf: Uint8Array,
  attachments: Attachment[],
  nameFor: (part: string) => string,
  encode: ImageEncoder,
  maxBytes: number = MAX_BYTES,
): Promise<PacketResult[]> {
  const out = [await buildPacket(formPdf, [], nameFor('Form'), encode, { maxBytes, label: 'DD Form 2642' })];
  for (const category of ['BILL', 'RECEIPT'] as AttachmentCategory[]) {
    const items = attachments.filter((a) => a.category === category);
    if (items.length) {
      out.push(await buildPacket(null, items, nameFor(CATEGORY_FILE_PART[category]), encode, { maxBytes, label: CATEGORY_LABELS[category] }));
    }
  }
  return out;
}

/** Page count of a PDF attachment; throws if it is encrypted or damaged. */
export async function pdfPageCount(data: Uint8Array): Promise<number> {
  return (await PDFDocument.load(data)).getPageCount();
}

export const formatSize = (bytes: number): string =>
  bytes >= 1024 * 1024 ? `${(bytes / 1048576).toFixed(1)} MB` : `${Math.round(bytes / 1024)} KB`;
