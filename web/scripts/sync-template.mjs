// Prepares web/public before every build, test and dev run:
//  - copies the form template and icon from the Android project (one source for both apps)
//  - renders PRIVACY.md into a static, read-only privacy.html for the published site
//    (Google Play needs a non-editable policy URL; the repository's file view has an edit button)
import { copyFileSync, mkdirSync, readFileSync, statSync, writeFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { marked } from 'marked';

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

// ---- privacy policy page
const source = readFileSync(resolve(root, 'PRIVACY.md'), 'utf8');
const body = marked.parse(source, { gfm: true, async: false });
const title = (/^#\s+(.+)$/m.exec(source)?.[1] ?? '2642 Easy Privacy Policy').trim();
const html = `<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8" />
<meta name="viewport" content="width=device-width, initial-scale=1" />
<meta http-equiv="Content-Security-Policy" content="default-src 'none'; style-src 'unsafe-inline'; img-src 'self'; base-uri 'none'; form-action 'none'" />
<meta name="referrer" content="no-referrer" />
<meta name="description" content="Privacy policy for 2642 Easy" />
<title>${title.replace(/</g, '&lt;')}</title>
<link rel="icon" type="image/png" href="./icon-512.png" />
<style>
  :root { color-scheme: light dark; --bg: #f6f8f6; --surface: #ffffff; --text: #17201b; --muted: #55625a; --line: #c3cdc6; --accent: #2f6b4c; }
  @media (prefers-color-scheme: dark) { :root { --bg: #121714; --surface: #1b221e; --text: #e6ece8; --muted: #a6b3ab; --line: #3c4841; --accent: #7fc79d; } }
  * { box-sizing: border-box; }
  body { margin: 0; background: var(--bg); color: var(--text); font: 16px/1.55 system-ui, -apple-system, "Segoe UI", Roboto, sans-serif; }
  main { max-width: 46rem; margin: 0 auto; padding: 1.5rem 1rem 3rem; }
  header { display: flex; align-items: center; gap: 0.6rem; margin-bottom: 1rem; color: var(--muted); }
  header img { border-radius: 8px; }
  h1 { font-size: 1.6rem; line-height: 1.25; margin: 0 0 0.5rem; }
  h2 { font-size: 1.2rem; margin: 2rem 0 0.5rem; color: var(--accent); }
  a { color: var(--accent); }
  table { width: 100%; border-collapse: collapse; margin: 1rem 0; font-size: 0.95rem; display: block; overflow-x: auto; }
  th, td { border: 1px solid var(--line); padding: 0.5rem 0.6rem; text-align: left; vertical-align: top; }
  th { background: var(--surface); }
  code { font-size: 0.9em; }
  ul { padding-left: 1.25rem; }
  li { margin: 0.35rem 0; }
  footer { margin-top: 2.5rem; padding-top: 1rem; border-top: 1px solid var(--line); color: var(--muted); font-size: 0.875rem; }
</style>
</head>
<body>
<main>
<header><img src="./icon-512.png" alt="" width="32" height="32" /> 2642 Easy</header>
${body}
<footer>2642 Easy is an independent document preparation tool. It is not affiliated with, endorsed by, or sponsored by TRICARE, the Defense Health Agency (DHA), or the Department of Defense (DoD).</footer>
</main>
</body>
</html>
`;
writeFileSync(resolve(root, 'web/public/privacy.html'), html);
// Keep the source's timestamp visible in build logs so a stale page is easy to spot.
console.log(`privacy.html generated from PRIVACY.md (${statSync(resolve(root, 'PRIVACY.md')).mtime.toISOString().slice(0, 10)})`);
