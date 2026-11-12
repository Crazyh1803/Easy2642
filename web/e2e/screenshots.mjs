// Renders phone-sized screenshots of the web app with made-up data.
//
//   npm run build && node e2e/screenshots.mjs [output-folder]
//
// 390 x 844 CSS pixels at 3x = 1170 x 2532 px images. Nothing shown is a real person's data.
import { spawn } from 'node:child_process';
import { mkdirSync, writeFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const require = createRequire(import.meta.url);
const { chromium } = require('playwright');

const here = dirname(fileURLToPath(import.meta.url));
const root = resolve(here, '..');
const out = resolve(process.argv[2] ?? resolve(here, 'out', 'phone'));
const fixtures = resolve(root, 'tests', 'fixtures');
const PORT = 4174;
const URL_BASE = `http://localhost:${PORT}/`;
const PASSCODE = 'sample-passcode';
mkdirSync(out, { recursive: true });

const name = (lastName, firstName, middleInitial) => ({ lastName, firstName, middleInitial });
const claim = (id, patientId, patientName, provider, dateOfService, amountMinor, currency, extra = {}) => ({
  id, patientId, patientName, provider, dateOfService, amountMinor, currency, dossierUri: '', isPacketGenerated: true,
  isFiled: false, filedDate: null, isReimbursed: false, reimbursedDate: null, notes: '', ...extra,
});
const backup = {
  formatVersion: 1,
  sponsor: {
    name: name('Sampleton', 'Casey', 'R'), dodIdOrSsn: '0000000000', branchOfService: 'Army', dutyStatus: 'Active duty',
    streetAddress: 'PSC 2 Box 1234', city: 'APO', state: 'AE', zipCode: '09012', phoneNumber: '+49 631 555 0100', dob: '1986-01-02', sex: 'M',
  },
  dependents: [
    { id: 'd-jordan', relationship: 'CHILD', name: name('Sampleton', 'Jordan', 'A'), dob: '2014-06-17', sex: 'F', dodId: '0000000000' },
    { id: 'd-riley', relationship: 'SPOUSE', name: name('Sampleton', 'Riley', 'M'), dob: '1987-09-23', sex: 'F', dodId: '0000000000' },
  ],
  claims: [
    claim('c1', 'd-jordan', 'Jordan Sampleton', 'Kinderklinik Beispiel', '2026-02-14', 8505, 'EUR', { notes: 'Ear infection, urgent care visit' }),
    claim('c2', 'd-riley', 'Riley Sampleton', 'Zahnarztpraxis Muster', '2026-01-28', 21000, 'EUR', { isFiled: true, filedDate: '2026-02-02' }),
    claim('c3', 'sponsor', 'Casey Sampleton', 'Apotheke am Markt', '2026-01-09', 3460, 'EUR', { isFiled: true, filedDate: '2026-01-12', isReimbursed: true, reimbursedDate: '2026-02-20' }),
  ],
  signatures: [{
    id: 's1', signerName: 'Casey Sampleton',
    signature: { strokes: [Array.from({ length: 41 }, (_, i) => ({ x: 20 + i * 9, y: 80 + 45 * Math.sin(i / 2.5) })), Array.from({ length: 13 }, (_, i) => ({ x: 60 + i * 25, y: 140 - i * 2 }))] },
  }],
};
const backupFile = resolve(out, '.sample-backup.json');
writeFileSync(backupFile, JSON.stringify(backup));

const server = spawn(process.execPath, ['node_modules/vite/bin/vite.js', 'preview', '--port', String(PORT), '--strictPort'], { cwd: root, stdio: 'pipe' });
await new Promise((ok, fail) => {
  const timer = setTimeout(() => fail(new Error('preview server did not start')), 20000);
  server.stdout.on('data', (d) => { if (String(d).includes('localhost')) { clearTimeout(timer); ok(); } });
  server.on('exit', (code) => fail(new Error(`preview server exited (${code})`)));
});

const browser = await chromium.launch();
try {
  const context = await browser.newContext({ viewport: { width: 390, height: 844 }, deviceScaleFactor: 3, isMobile: true, hasTouch: true });
  const page = await context.newPage();
  const field = (label) => page.getByLabel(label, { exact: true });
  const button = (label) => page.getByRole('button', { name: label, exact: true });
  let n = 0;
  const shot = async (label) => {
    await page.evaluate(() => { document.activeElement?.blur?.(); window.scrollTo(0, 0); });
    await page.waitForTimeout(350);
    const file = `${String(++n).padStart(2, '0')}_${label}.png`;
    await page.screenshot({ path: resolve(out, file) });
    console.log(file);
  };

  await page.goto(URL_BASE);
  await button('I understand').click();
  await field('New passcode').fill(PASSCODE);
  await field('Type it again').fill(PASSCODE);
  await button('Create passcode').click();
  await button('Settings').click();
  await page.getByLabel('Backup file to restore').setInputFiles(backupFile);
  await page.getByRole('dialog').getByRole('button', { name: 'Replace my data' }).click();
  await page.getByText('Backup restored').waitFor();

  await button('Claims').click();
  await page.getByText('Kinderklinik Beispiel').waitFor();
  await shot('claim_tracker');

  await button('Family').click();
  await shot('family_profiles');

  await button('Claims').click();
  await button('New claim').click();
  await button('Jordan').click();
  await field('Provider (doctor, clinic or pharmacy)').fill('Kinderklinik Beispiel');
  await field('Date of service').fill('2026-02-14');
  await field('Amount charged').fill('85.05');
  await button('EUR').click();
  await shot('new_claim_patient_and_visit');
  await button('Next').click();

  await field('3b Overseas only: state/country where care was given').fill('Kaiserslautern, Germany');
  await field('8a Illness, injury or symptoms treated').fill('Acute otitis media, right ear. Fever and ear pain for two days; seen at the paediatric urgent care clinic.');
  await button('Outpatient').click();
  await page.getByRole('group', { name: 'Accident related?' }).getByRole('button', { name: 'No' }).click();
  await page.getByRole('group', { name: 'Work related?' }).getByRole('button', { name: 'No' }).click();
  await shot('form_details');
  await button('Next').click();

  await page.getByLabel('Files to attach').setInputFiles([resolve(fixtures, 'receipt.jpg'), resolve(fixtures, 'wide.jpg')]);
  await page.getByText('Image #2').waitFor();
  await page.getByRole('group', { name: 'Type of Image #2' }).getByRole('button', { name: 'Receipt / proof of payment' }).click();
  await shot('supporting_documents');
  await button('Next: sign').click();

  await button('Casey Sampleton').click();
  await field("12c Signer's relationship to patient").fill('Parent');
  await shot('sign');
  await button('Build packet').click();

  await button('Save packet').waitFor({ timeout: 60000 });
  // The headless browser has no PDF viewer, so the embedded preview frame would be an empty box.
  await page.addStyleTag({ content: '.preview { display: none !important; }' });
  await shot('review_and_save');
  await button('Save packet').click();
  await page.getByRole('dialog').waitFor();
  await page.waitForTimeout(300);
  await page.screenshot({ path: resolve(out, `${String(++n).padStart(2, '0')}_review_before_saving.png`) });
  console.log(`${String(n).padStart(2, '0')}_review_before_saving.png`);
  await page.getByRole('dialog').getByRole('button', { name: 'Cancel' }).click();
  await button('Done').click();

  await button('Settings').click();
  await shot('settings');

  await page.reload();
  await field('Passcode').waitFor();
  await shot('passcode_lock');
} finally {
  await browser.close();
  server.kill();
}
