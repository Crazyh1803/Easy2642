import { defineConfig, Plugin } from 'vitest/config';
import preact from '@preact/preset-vite';

// The built site may only talk to itself: no third-party scripts, no outbound requests.
// (Applied to the production build only; the dev server needs inline scripts for hot reload.)
const CSP = [
  "default-src 'self'",
  "script-src 'self'",
  "style-src 'self' 'unsafe-inline'",
  "img-src 'self' blob: data:",
  "media-src 'self' blob: mediastream:",
  "connect-src 'self'",
  "frame-src blob:",
  "object-src 'none'",
  "base-uri 'none'",
  "form-action 'none'",
].join('; ');

const contentSecurityPolicy = (): Plugin => ({
  name: 'easy2642-csp',
  apply: 'build',
  transformIndexHtml: (html) =>
    html.replace('<meta charset="utf-8" />', `<meta charset="utf-8" />\n    <meta http-equiv="Content-Security-Policy" content="${CSP}" />`),
});

export default defineConfig({
  // Relative paths so the same build works at a domain root or under /Easy2642/ on GitHub Pages.
  base: './',
  plugins: [preact(), contentSecurityPolicy()],
  build: { target: 'es2022', sourcemap: false },
  test: { environment: 'node', include: ['tests/**/*.test.ts'] },
});
