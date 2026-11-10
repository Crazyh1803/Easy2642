# Device gate checklist

Automated tests cover the form mapping, PDF output, merging, compression, file naming, the database schema, backup and the no-permissions manifest. The checks below need a real phone and have **not** been run.

Install a debug build: `./gradlew :app:installDebug`

## Gate 1: form engine
- [ ] Settings → Form engine test harness → Fill & preview shows the filled form; boxes and text sit inside their blocks.
- [ ] "Long text" wraps inside block 8a without clipping.
- [ ] "Audit field keys" reports 62 fields, 0 unmapped, 0 missing.
- [ ] Airplane mode on: everything above still works.
- [ ] Open an exported packet in Adobe Acrobat Reader and in the browser's PDF viewer: values are visible in both, and block 12a can still be signed.

## Gate 2: scanning and attachments
- [ ] "Scan pages" opens the scanner, detects edges and returns pages (needs Google Play services).
- [ ] A long thermal receipt is legible in the packet.
- [ ] A dark-mode banking screenshot added with "Add images" is legible.
- [ ] A multi-page PDF bill added with "Add PDFs" keeps all pages in order.
- [ ] A typical 4-page claim reports under 10 MB on the review step.
- [ ] On a device without Google Play services, "Scan pages" shows the "Scanner unavailable" message and images/PDFs still work.

## Gate 3: export
- [ ] Save packet → Choose location… → internal storage. File opens.
- [ ] Same to an SD card or USB drive.
- [ ] Same to Google Drive (Drive app installed and signed in).
- [ ] Settings → Choose claims folder, then Save packet → Save to "<folder>" creates the file there.
- [ ] Revoke the folder (delete it), then save again: the app explains the folder is unavailable.
- [ ] Share → email app receives the PDF attachment.
- [ ] Repeat a save on Android 11, 13 and 15 or later.
- [ ] System Settings → Apps → 2642 Easy → Permissions shows "No permissions requested".

## Gate 4: profiles and tracker
- [ ] Save a sponsor and two dependents; start a claim and switch patients: name, birth date, sex, relationship, sponsor name and number update each time.
- [ ] Toggle Filed Online / Reimbursed, force-stop the app, reopen: states and dates persisted.
- [ ] Filters and search narrow the list as expected.
- [ ] Encryption at rest (debuggable build): `adb shell run-as com.easy2642.app cat databases/easy2642.db | head -c 16 | xxd` must **not** start with `SQLite format 3`.
- [ ] `adb logcat` while entering a profile and building a packet shows no names, SSN/DBN or addresses.
- [ ] After leaving a claim, `adb shell run-as com.easy2642.app ls cache` shows no `packets` or `attachments` files.
- [ ] Export Backup, Clear All Local Data (app restarts empty, first-run notice shows again), Restore from backup brings everything back.
- [ ] After Clear All Local Data, `run-as com.easy2642.app ls databases no_backup` shows no database or passphrase file.

## Gate 5: release
- [ ] Create an upload keystore and `keystore.properties` (see README), run `./gradlew :app:bundleRelease`, upload the `.aab` to the internal testing track.
- [ ] Smoke-test the release build end to end: R8 shrinking is enabled and only the debug build has been exercised by automated tests.
- [ ] Pre-launch report shows no policy or stability issues.
- [ ] Privacy policy is published at a public URL and linked in the listing.
- [ ] Data safety and Health apps declarations entered as in `docs/play-console.md`.
- [ ] Screenshots reviewed: synthetic data only, no official logos or insignia.
