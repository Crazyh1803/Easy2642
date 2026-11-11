# 2642 Easy for the web

The same app as the Android version, running entirely in the browser, for PC, Mac, iPhone and iPad users. There is no server side: the site is static files, and nothing a user enters is uploaded.

## What it does

- **Family profiles and claim tracker**, with the same filters, search and Filed Online / Reimbursed toggles.
- **Claim wizard** in five steps, filling the same DD Form 2642 (SEP 2024) template.
- **Supporting documents** by camera or by upload:
  - *Use camera* opens the phone camera or a PC webcam in the page and captures pages.
  - *Upload files* takes photos, screenshots and PDFs from the device; on a computer, files can also be dragged in. On a phone the same button offers the camera and photo library.
- **Mandatory signature** drawn with a mouse, finger or pen; no file is produced unsigned. Signatures can be saved per signer.
- **One combined PDF or separate form / bill / receipt files**, each kept under 10 MB.
- **Saving** by download, by the share sheet where the browser has one, or straight into a chosen folder in Chromium-based desktop browsers.
- **Backup and restore** in the same file format as the Android app, so a backup from one restores on the other.

## How it differs from the Android app

| | Android | Web |
| --- | --- | --- |
| Unlocking | Opens directly | Passcode chosen on first use, asked each time the page is opened and after 15 minutes idle |
| Encryption | SQLCipher database, key in the Android Keystore | One AES-256-GCM document in the browser's storage, key derived from the passcode (PBKDF2, 600,000 rounds) and held only in memory |
| Scanning | ML Kit scanner: edge detection, deskew, clean-up | Plain photo from the camera; no automatic cropping or straightening |
| Saved file location | Remembered, "Open packet" from the tracker | Not remembered; the browser cannot reopen a downloaded file |
| Forgotten passcode | n/a | Unrecoverable. Erase and restore from a backup file |

Data lives in the browser profile it was entered in. Clearing site data, or using a different browser or device, starts empty; use Export backup to move it.

## Privacy properties

- The page makes no requests after loading its own files. A Content-Security-Policy in the built page (`connect-src 'self'`, no third-party scripts) enforces this, and the browser test fails if any request leaves the site.
- Camera frames are processed in the page and the camera is released when the dialog closes.
- Nothing readable is written to storage: the browser test checks the stored data for names, numbers and the passcode.

## Develop

Requires Node 22 or later.

```bash
cd web
npm install
npm run dev
```

The form template and icon are copied from the Android project (`app/src/main/assets/forms/`, `docs/store/`) by `scripts/sync-template.mjs` before each run, so both apps always use the same template. `src/core/fieldMap.ts` mirrors `Dd2642FieldMap.kt`; a unit test fails if the template and the map drift apart.

## Test

```bash
npm test
```

```bash
npm run build
```

```bash
npm run e2e
```

`npm test` covers the form mapping, PDF output, packet building, encryption and backup parsing. `npm run e2e` drives the built site in Chromium with a fake camera through the whole flow: passcode, profiles, a claim with a camera capture and uploads, signing, both output modes, the tracker, lock/unlock, backup, erase and restore. It needs Playwright (`npm install --no-save playwright && npx playwright install chromium`). Screenshots and the generated PDFs land in `e2e/out/`.

## Screenshots

`docs/store/web-screenshots-phone/` holds ten phone-sized screenshots (1170 x 2532) with made-up data. Regenerate them after UI changes:

```bash
npm run build
```

```bash
node e2e/screenshots.mjs ../docs/store/web-screenshots-phone
```

They come from a headless desktop Chromium emulating a phone, so the embedded PDF preview is hidden (that browser has no PDF viewer) and the save dialog shows a "Choose folder…" button that real phone browsers do not offer.

## Deploy

`.github/workflows/web.yml` tests and builds on every push that touches `web/`, and publishes `web/dist` to GitHub Pages from `main`. One-time setup: repository **Settings → Pages → Build and deployment → Source: GitHub Actions**. The site then appears at `https://crazyh1803.github.io/Easy2642/`.

The Source setting must be **GitHub Actions**, not "Deploy from a branch". Branch mode publishes the repository's files as they are, so the address shows the README instead of the app, and it blocks this workflow's deployment. To publish on demand, use **Actions → Web app → Run workflow** on `main`.

The camera only works over HTTPS (GitHub Pages provides it) or on `localhost`.

To host elsewhere, upload the contents of `web/dist` after `npm run build`; paths are relative, so any folder works.

## Not verified

- Tested in Chromium only. Safari (Mac, iPhone, iPad) and Firefox have not been run.
- The camera path was exercised with Chromium's fake camera, not a real phone camera or webcam.
- The in-page PDF preview does not render in the headless test browser, so it has not been seen; "Open in a new tab" and the downloads were checked instead.
