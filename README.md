# 2642 Easy

An Android app that prepares, bundles and tracks DD Form 2642 medical claim packets for military families, entirely on the device.

> 2642 Easy is an independent document preparation tool. It is not affiliated with, endorsed by, or sponsored by TRICARE, the Defense Health Agency (DHA), or the Department of Defense (DoD). Users remain responsible for submitting completed claim packets to their respective claims processor or portal.

## What it does

1. **Fills the form.** Maps a claim onto the official DD Form 2642 (SEP 2024 edition) AcroForm template and flattens it.
2. **Requires a signature.** The claim cannot be built until block 12a is signed on screen. Signatures are vector strokes drawn into the block; they can be saved per signer (encrypted) and reused with one tap.
3. **Bundles documents.** Scans pages with the ML Kit document scanner, imports images through the system photo picker and PDFs through the system file picker, and tags each as a bill or a receipt / proof of payment.
4. **Writes one PDF or three.** Either a combined packet (form first, attachments after) or separate `_Form`, `_Bill` and `_Receipt` files. Images are recompressed until each file is under 10 MB.
5. **Exports without permissions.** Saves through the Storage Access Framework (`ACTION_CREATE_DOCUMENT`, or a folder via `ACTION_OPEN_DOCUMENT_TREE`) or the share sheet, named `Claim_DD2642_[PatientLastName]_[Provider]_[YYYYMMDD].pdf`.
6. **Remembers the family.** Sponsor and dependent profiles pre-fill blocks 1–6, 9 and 10. Stored in a SQLCipher database whose key is wrapped by the Android Keystore.
7. **Tracks claims.** Dashboard with patient and status filters, search, and Filed Online / Reimbursed toggles.

The app requests **no Android system permissions**: no internet, storage or camera. (The only manifest entry is AndroidX's app-private `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`.)

Block 12a of the form reads "Common Access Card or Physical signature required". The app draws an image of a handwritten signature; whether a given claims processor accepts that is their decision.

Settings links to the author's site and an optional tip page, and a tip reminder appears at most once a year during Oktoberfest (`TIP_REMINDER_ENABLED` in `ui/AppRoot.kt` turns it off). Links are handed to the browser; the app itself has no network access.

## Web version

`web/` holds a browser version for PC, Mac, iPhone and iPad with the same features: phone camera or webcam capture, file upload, signing, combined or separate PDFs, the tracker, and backups that are interchangeable with the Android app. It is a static site with no server side. See `web/README.md` for how it differs, how to run it and how it is deployed to GitHub Pages.

## Project layout

| Path | Contents |
| --- | --- |
| `core/` | Pure Kotlin: models, `Dd2642FieldMap` (all 61 fillable field keys), file naming, claim state and filters. No Android dependencies |
| `app/…/pdf/` | `PdfFormService` (fill, sign, flatten, field audit), `TextFit`, `PacketBuilder` (image pages, PDF merge, combined or separate output, size cap) |
| `app/…/scan/` | ML Kit document scanner wrapper |
| `app/…/data/` | Room entities and DAOs, `SecureDatabase` and `KeystorePassphrase`, repository, backup |
| `app/…/ui/` | Compose screens: dashboard, family profiles, claim wizard with signature pad, settings, debug-only form harness |
| `app/src/main/assets/forms/dd2642_template.pdf` | The official form with its XFA layer and owner-password restrictions removed by `tools/prepare_template.py` |
| `PRIVACY.md` | Privacy policy, linked from the Play listing and from Settings in the app |
| `docs/` | Play Console answers and store text, device gate checklist |

## About the form

The SEP 2024 edition differs from older descriptions of the form: it has no branch-of-service, duty-status, provider or charges boxes. Provider, date of service and amount are kept in the claim tracker and the file name; branch and duty status are optional profile fields that are not printed.

When DoD publishes a new edition, run `python3 tools/prepare_template.py <new.pdf> app/src/main/assets/forms/dd2642_template.pdf` and then the tests: `templateAndFieldMapAreInSync` fails and lists any field keys that changed.

## Build and test

Requires JDK 17 or later and the Android SDK (platform 35).

```bash
./gradlew :core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

To keep the PDFs the tests generate for a visual check:

```bash
./gradlew :app:testDebugUnitTest -PpdfOutDir=/tmp/2642-pdfs
```

Debug builds are signed with the tracked `app/debug.keystore`, so a debug APK from CI or any machine installs over an earlier one without uninstalling. That key is public by design and is never used for release.

### Release bundle for Google Play

After cloning or syncing the repo to your computer, double-click the launcher for your system in the project folder:

- **Windows:** `Setup Release Build (Windows).bat`
- **Mac:** `Setup Release Build (Mac).command`
- **Linux:** run `tools/setup-release.sh`

It is safe to run every time you want a new bundle. It:

1. finds Java (the one bundled with Android Studio, or any JDK 17+), offering to install Android Studio through winget or Homebrew if neither is present;
2. installs the Android SDK pieces the project needs if they are missing, and writes `local.properties`;
3. on the first run, creates your upload key (`upload-keystore.jks`) and `keystore.properties` after asking for a password;
4. builds and verifies the signed bundle at `app/build/outputs/bundle/release/app-release.aab`.

Then open the folder in Android Studio as usual if you want to work on the code.

Keep these safe:

- **Back up `upload-keystore.jks` and its password** outside the project. Both are git-ignored and exist only on your computer. If you lose them you must ask Google Play support to reset your upload key.
- **Raise `versionCode`** (and `versionName`) in `app/build.gradle.kts` before every upload after the first; Play rejects a repeated version code.

To build by hand instead, create `keystore.properties` in the project root:

```properties
storeFile=upload-keystore.jks
storePassword=…
keyAlias=upload
keyPassword=…
```

```bash
./gradlew :app:bundleRelease
```

Without that file the bundle is built unsigned.

### Store screenshots

`docs/store/screenshots/` holds eight 1080 x 1920 screenshots for the Play listing. They are rendered from the app's real screens with made-up data by the `StoreScreenshots` test, without a device, so they show the app content only (no status bar, tab bar or disclaimer strip). Regenerate them after UI changes:

```bash
./gradlew :app:testDebugUnitTest --tests '*StoreScreenshots*' -PscreenshotDir=docs/store/screenshots
```

## What still needs a real device

See `docs/device-gate-checklist.md`. Camera scanning, saving to Drive or an SD card, Keystore-backed encryption and the R8-shrunk release build cannot be exercised by the JVM tests.

## Third-party components

PdfBox-Android (Apache 2.0), SQLCipher for Android (BSD-style), AndroidX and Jetpack Compose (Apache 2.0), Google ML Kit document scanner (Google APIs terms). DD Form 2642 is a U.S. Government form.
