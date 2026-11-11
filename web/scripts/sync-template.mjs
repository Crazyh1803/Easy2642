// Copies the single source-of-truth form template and icon from the Android project
// into the web app's public folder, so the two apps can never drift apart.
import { copyFileSync, mkdirSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const root = resolve(here, '..', '..');
const copies = [
  ['app/src/main/assets/forms/dd2642_template.pdf', 'web/public/forms/dd2642_template.pdf'],
  ['docs/store/icon-512.png', 'web/public/icon-512.png'],
];
for (const [from, to] of copies) {
  mkdirSync(dirname(resolve(root, to)), { recursive: true });
  copyFileSync(resolve(root, from), resolve(root, to));
}
