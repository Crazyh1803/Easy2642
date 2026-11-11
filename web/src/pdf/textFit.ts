// Chooses a font size so a value fits its form box, shrinking from the form's native 8 pt.
export const DEFAULT_SIZE = 8;
export const MIN_SIZE = 5;
const PADDING = 4;
const LINE_FACTOR = 1.2;

export interface Fit { fontSize: number; fits: boolean; lines: number }

/** Greedy word wrap; counts the lines needed at a given line width in em. */
export function wrapCount(text: string, lineWidthEm: number, widthEm: (s: string) => number): number {
  if (!text) return 1;
  let lines = 0;
  const space = widthEm(' ');
  for (const paragraph of text.split('\n')) {
    let current = 0;
    lines++;
    for (const word of paragraph.split(' ').filter(Boolean)) {
      let w = widthEm(word);
      if (current > 0 && current + space + w > lineWidthEm) {
        lines++;
        current = 0;
      }
      // A single word longer than the line is broken across lines.
      while (w > lineWidthEm) {
        lines++;
        w -= lineWidthEm;
      }
      current += (current > 0 ? space : 0) + w;
    }
  }
  return lines;
}

/** @param widthEm width of a string in em units (multiply by font size for points). */
export function fitText(text: string, boxWidth: number, boxHeight: number, multiline: boolean, widthEm: (s: string) => number): Fit {
  const usable = Math.max(boxWidth - PADDING, 1);
  let size = DEFAULT_SIZE;
  for (;;) {
    const lines = multiline ? wrapCount(text, usable / size, widthEm) : 1;
    const fits = multiline ? lines * size * LINE_FACTOR + 2 <= boxHeight : widthEm(text) * size <= usable;
    if (fits || size <= MIN_SIZE) return { fontSize: size, fits, lines };
    size -= 0.5;
  }
}
