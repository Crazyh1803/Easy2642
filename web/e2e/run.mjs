// End-to-end check of the built site in a real browser (Chromium via Playwright).
//
//   npm run build && npm run e2e
//
// Uses Chromium's fake camera, so the camera path is exercised without hardware.
// Screenshots and downloaded PDFs land in e2e/out/.
import { spawn } from 'node:child_process';
import { mkdirSync, readFileSync, rmSync } from 'node:fs';
import { createRequire } from 'node:module';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const require = createRequire(import.meta.url);
const { chromium } = require('playwright');
const { PDFDocument } = require('pdf-lib');

const here = dirname(fileURLToPath(import.meta.url));
const root = resolve(here, '..');
const out = resolve(here, 'out');
const fixtures = resolve(root, 'tests', 'fixtures');
const PORT = 4173;
const URL_BASE = `http://localhost:${PORT}/`;
const PASSCODE = 'correct-horse-9';

rmSync(out, { recursive: true, force: true });
mkdirSync(out, { recursive: true });

let failures = 0;
const check = (ok, what) => {
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${what}`);
  if (!ok) failures++;
};
const pages = async (file) => (await PDFDocument.load(readFileSync(file))).getPageCount();

// Started with node directly (not through npx) so that stopping it really stops the server.
const server = spawn(process.execPath, ['node_modules/vite/bin/vite.js', 'preview', '--port', String(PORT), '--strictPort'], { cwd: root, stdio: 'pipe' });
await new Promise((ok, fail) => {
  const timer = setTimeout(() => fail(new Error('preview server did not start')), 20000);
  server.stdout.on('data', (d) => { if (String(d).includes('localhost')) { clearTimeout(timer); ok(); } });
  server.on('exit', (code) => fail(new Error(`preview server exited (${code})`)));
});

const browser = await chromium.launch({ args: ['--use-fake-ui-for-media-stream', '--use-fake-device-for-media-stream'] });
try {
  const context = await browser.newContext({ viewport: { width: 1280, height: 900 }, acceptDownloads: true, permissions: ['camera'] });
  const page = await context.newPage();
  const problems = [];
  const foreign = [];
  page.on('console', (m) => { if (m.type() === 'error') problems.push(m.text()); });
  page.on('pageerror', (e) => problems.push(String(e)));
  page.on('request', (r) => { const u = r.url(); if (!u.startsWith(URL_BASE) && !u.startsWith('blob:') && !u.startsWith('data:')) foreign.push(u); });

  const field = (label) => page.getByLabel(label, { exact: true });
  const button = (name) => page.getByRole('button', { name, exact: true });
  const shot = (name) => page.screenshot({ path: resolve(out, `${name}.png`), fullPage: false });
  const download = async (trigger, count = 1) => {
    const got = [];
    const onDownload = (d) => got.push(d);
    page.on('download', onDownload);
    await trigger();
    for (let i = 0; i < 100 && got.length < count; i++) await page.waitForTimeout(100);
    page.off('download', onDownload);
    const files = [];
    for (const d of got) {
      const path = resolve(out, d.suggestedFilename());
      await d.saveAs(path);
      files.push({ name: d.suggestedFilename(), path });
    }
    return files;
  };

  // ---- first run: disclaimer, then choose a passcode
  await page.goto(URL_BASE);
  await page.getByRole('heading', { name: 'Before you start' }).waitFor();
  await shot('01_first_run');
  await button('I understand').click();
  await field('New passcode').fill('abc');
  await field('Type it again').fill('abc');
  await button('Create passcode').click();
  check(await page.getByRole('alert').textContent().then((t) => t.includes('at least')), 'short passcode is rejected');
  await field('New passcode').fill(PASSCODE);
  await field('Type it again').fill(PASSCODE);
  await button('Create passcode').click();
  await page.getByRole('heading', { name: 'Claims' }).waitFor();
  check(true, 'passcode created and app unlocked');

  // ---- family profiles
  await button('Family').click();
  await field('Last name').fill('Sampleton');
  await field('First name').fill('Casey');
  await field('MI').fill('r');
  await field('DoD Benefits Number (DBN) or SSN').fill('0000000000');
  await field('Date of birth').fill('1986-01-02');
  await field('Street / APO / FPO address').fill('PSC 2 Box 1234');
  await field('City (or APO / FPO)').fill('APO');
  await field('State / country').fill('AE');
  await field('ZIP').fill('09012');
  await field('Phone (with area/country code)').fill('+49 631 555 0100');
  await button('Save sponsor').click();
  await page.getByText('Sponsor saved').waitFor();
  await button('Add dependent').click();
  const dlg = page.getByRole('dialog');
  check((await dlg.getByLabel('DoD Benefits Number (DBN)', { exact: true }).inputValue()) === '0000000000', "new dependent starts with the sponsor's DBN");
  await dlg.getByLabel('First name', { exact: true }).fill('Jordan');
  await dlg.getByLabel('MI', { exact: true }).fill('A');
  await dlg.getByRole('button', { name: 'Natural or adopted child' }).click();
  await dlg.getByLabel('Date of birth', { exact: true }).fill('2014-06-17');
  await dlg.getByRole('button', { name: 'Female' }).click();
  await dlg.getByRole('button', { name: 'Save', exact: true }).click();
  await page.getByText('Jordan Sampleton').waitFor();
  await shot('02_family');
  check(true, 'sponsor and dependent saved');

  // ---- new claim: patient and visit
  await button('Claims').click();
  await button('New claim').click();
  check(await button('Next').isDisabled(), 'Next is disabled until the visit details are filled');
  await button('Jordan').click();
  check((await field('Last name').inputValue()) === 'Sampleton', 'choosing a patient pre-fills the name');
  await field('Provider (doctor, clinic or pharmacy)').fill('Kinderklinik Beispiel');
  await field('Date of service').fill('2026-02-14');
  await field('Amount charged').fill('85.05');
  await button('EUR').click();
  await shot('03_visit');
  await button('Next').click();

  // ---- form details
  check((await field('5 Date of birth').inputValue()) === '2014-06-17', 'date of birth carried into block 5');
  check((await field("10 Sponsor's SSN or DoD Benefits Number").inputValue()) === '0000000000', 'sponsor number carried into block 10');
  await field('8a Illness, injury or symptoms treated').fill('Acute otitis media, right ear. Fever and ear pain for two days.');
  await button('Outpatient').click();
  await page.getByRole('group', { name: 'Accident related?' }).getByRole('button', { name: 'No' }).click();
  await page.getByRole('group', { name: 'Work related?' }).getByRole('button', { name: 'No' }).click();
  await shot('04_form');
  await button('Next').click();

  // ---- documents: camera, then file upload
  await button('Use camera').click();
  await page.locator('video.camera').waitFor();
  await page.waitForFunction(() => document.querySelector('video.camera')?.videoWidth > 0);
  await shot('05_camera');
  await button('Capture page').click();
  await page.getByText('1 page added').waitFor();
  await button('Done').click();
  await page.getByText('Photo #1').waitFor();
  check(true, 'camera opened and a page was captured');
  check(await page.evaluate(() => !document.querySelector('video')), 'camera stream released when the dialog closes');

  await page.getByLabel('Files to attach').setInputFiles([resolve(fixtures, 'receipt.jpg'), resolve(root, 'public/forms/dd2642_template.pdf')]);
  await page.getByText('PDF #3').waitFor();
  check(await page.getByText('Image #2').isVisible(), 'uploaded image and PDF were added');
  await page.getByLabel('Files to attach').setInputFiles([resolve(root, 'package.json')]);
  await page.getByText('could not be read').waitFor();
  check(true, 'an unreadable file is skipped with a message');
  await page.getByRole('group', { name: 'Type of Image #2' }).getByRole('button', { name: 'Receipt / proof of payment' }).click();
  await shot('06_documents');
  await button('Next: sign').click();

  // ---- sign: the packet cannot be built unsigned
  check(await button('Build packet').isDisabled(), 'Build packet is disabled before signing');
  const pad = page.locator('canvas.sigpad');
  const box = await pad.boundingBox();
  await page.mouse.move(box.x + 40, box.y + 110);
  await page.mouse.down();
  for (let i = 1; i <= 40; i++) await page.mouse.move(box.x + 40 + i * 8, box.y + 100 + 40 * Math.sin(i / 2.5));
  await page.mouse.up();
  await field("Signer's full name").fill('Casey Sampleton');
  await button('Use this signature').click();
  await page.getByText('Signed by Casey Sampleton').waitFor();
  check(await button('Build packet').isDisabled(), 'Build packet stays disabled until 12c is filled');
  await field("12c Signer's relationship to patient").fill('Parent');
  await shot('07_sign');
  await button('Build packet').click();

  // ---- review: combined packet
  await button('Save packet').waitFor({ timeout: 60000 });
  await shot('08_review');
  await button('Save packet').click();
  const [combined] = await download(() => page.getByRole('dialog').getByRole('button', { name: 'Download' }).click());
  check(combined?.name === 'Claim_DD2642_Sampleton_KinderklinikBeispiel_20260214.pdf', `combined file name (${combined?.name})`);
  check((await pages(combined.path)) === 1 + 1 + 1 + 2, 'combined packet = form + photo + image + 2-page PDF');
  const filled = await PDFDocument.load(readFileSync(combined.path));
  check(filled.getForm().getFields().length === 0, 'form in the packet is fully flattened (no open signature field)');
  check(readFileSync(combined.path).length < 10 * 1024 * 1024, 'combined packet is under 10 MB');

  // ---- review: separate files
  await button('Separate files').click();
  await button('Save files').waitFor({ timeout: 60000 });
  await button('Save files').click();
  const separate = await download(() => page.getByRole('dialog').getByRole('button', { name: 'Download' }).click(), 3);
  check(separate.map((f) => f.name).sort().join() === [
    'Claim_DD2642_Sampleton_KinderklinikBeispiel_20260214_Bill.pdf',
    'Claim_DD2642_Sampleton_KinderklinikBeispiel_20260214_Form.pdf',
    'Claim_DD2642_Sampleton_KinderklinikBeispiel_20260214_Receipt.pdf',
  ].join(), `three separate files (${separate.map((f) => f.name).join(', ')})`);
  const byPart = Object.fromEntries(await Promise.all(separate.map(async (f) => [f.name.match(/_([A-Za-z]+)\.pdf$/)[1], await pages(f.path)])));
  check(byPart.Form === 1 && byPart.Bill === 3 && byPart.Receipt === 1, `page counts per file (${JSON.stringify(byPart)})`);
  await button('Done').click();

  // ---- tracker
  await page.getByText('Kinderklinik Beispiel').waitFor();
  check(await page.getByText('Needs Filing', { exact: true }).first().isVisible(), 'claim is in the tracker under Needs Filing');
  await page.getByLabel(/^Filed Online/).check();
  await page.getByText('Awaiting Reimbursement').nth(1).waitFor();
  await shot('09_tracker');
  check(true, 'marking Filed Online moves the claim to Awaiting Reimbursement');

  // ---- what is stored, and the lock
  const stored = await page.evaluate(() => JSON.stringify(Object.fromEntries(Object.entries(localStorage))));
  check(!/Sampleton|0000000000|Kinderklinik|correct-horse/.test(stored), 'nothing readable is stored in the browser');
  await page.reload();
  await field('Passcode').waitFor();
  check(true, 'reloading the page locks the app');
  await field('Passcode').fill('wrong-passcode');
  await button('Unlock').click();
  await page.getByText('not correct').waitFor();
  check(true, 'wrong passcode is refused');
  await field('Passcode').fill(PASSCODE);
  await button('Unlock').click();
  await page.getByText('Kinderklinik Beispiel').waitFor();
  check(await page.getByLabel(/^Filed Online/).isChecked(), 'data and claim status survive lock and unlock');

  // ---- backup and restore
  await button('Settings').click();
  await shot('10_settings');
  await page.getByRole('button', { name: /^Export backup/ }).click();
  const [backup] = await download(() => page.getByRole('dialog').getByRole('button', { name: 'Download backup' }).click());
  const parsed = JSON.parse(readFileSync(backup.path, 'utf8'));
  check(parsed.formatVersion === 1 && parsed.sponsor.name.lastName === 'Sampleton' && parsed.claims.length === 1 && parsed.signatures.length === 1, 'backup holds sponsor, claim and signature');
  await page.getByRole('button', { name: /^Clear all local data/ }).click();
  await page.getByRole('dialog').getByRole('button', { name: 'Erase everything' }).click();
  await page.getByRole('heading', { name: 'Before you start' }).waitFor();
  check((await page.evaluate(() => localStorage.length)) === 0, 'Clear all local data leaves nothing in storage');
  await button('I understand').click();
  await field('New passcode').fill(PASSCODE);
  await field('Type it again').fill(PASSCODE);
  await button('Create passcode').click();
  await button('Settings').click();
  await page.getByLabel('Backup file to restore').setInputFiles(backup.path);
  await page.getByRole('dialog').getByRole('button', { name: 'Replace my data' }).click();
  await page.getByText('Backup restored').waitFor();
  await button('Claims').click();
  await page.getByText('Kinderklinik Beispiel').waitFor();
  check(true, 'backup restores into a fresh install');

  const current = await page.locator('nav.tabs button[aria-current="page"]').allTextContents();
  check(current.join() === 'Claims', `only the open tab is marked current (${current.join()})`);

  // ---- phone-sized viewport
  await page.setViewportSize({ width: 390, height: 844 });
  await shot('11_phone_tracker');
  await button('New claim').click();
  await shot('12_phone_wizard');
  const nextBox = await button('Next').boundingBox();
  check(nextBox && nextBox.y + nextBox.height <= 844, 'Next button is on screen at phone size');

  check(foreign.length === 0, `no requests left the site (${foreign.join(', ') || 'none'})`);
  check(problems.length === 0, `no console errors or CSP violations (${problems.join(' | ') || 'none'})`);
} catch (e) {
  failures++;
  console.log(`FAIL  stopped early: ${e.message.split('\n')[0]}`);
  try { await (await browser.contexts()[0].pages())[0].screenshot({ path: resolve(out, 'failure.png') }); } catch { /* ignore */ }
} finally {
  await browser.close();
  server.kill();
}
console.log(failures ? `\n${failures} check(s) failed` : '\nAll checks passed');
process.exit(failures ? 1 : 0);
