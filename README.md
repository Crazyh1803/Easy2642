# 2642 Easy

An Android app that prepares, bundles and tracks DD Form 2642 medical claim packets for military families, entirely on the device.

> 2642 Easy is an independent document preparation tool. It is not affiliated with, endorsed by, or sponsored by TRICARE, the Defense Health Agency (DHA), or the Department of Defense (DoD). Users remain responsible for submitting completed claim packets to their respective claims processor or portal.

## What it does

1. **Fills the form.** Maps a claim onto the official DD Form 2642 (SEP 2024 edition) AcroForm template and flattens it, leaving the signature box in block 12a open for a wet or CAC signature.
2. **Bundles documents.** Scans pages with the ML Kit document scanner, imports images through the system photo picker and PDFs through the system file picker, then merges everything behind the form and recompresses images until the packet is under 10 MB.
3. **Exports without permissions.** Saves through the Storage Access Framework (`ACTION_CREATE_DOCUMENT`, or a designated folder via `ACTION_OPEN_DOCUMENT_TREE`) or the share sheet, named `Claim_DD2642_[PatientLastName]_[Provider]_[YYYYMMDD].pdf`.
4. **Remembers the family.** Sponsor and dependent profiles pre-fill blocks 1–6, 9 and 10. Stored in a SQLCipher database whose key is wrapped by the Android Keystore.
5. **Tracks claims.** Dashboard with patient and status filters, search, and Filed Online / Reimbursed toggles.

The app requests **no Android system permissions**: no internet, storage or camera. (The only manifest entry is AndroidX's app-private `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`.)

## Project layout

| Path | Contents |
| --- | --- |
| `core/` | Pure Kotlin: models, `Dd2642FieldMap` (all 61 fillable field keys), file naming, claim state and filters. No Android dependencies |
| `app/…/pdf/` | `PdfFormService` (fill, flatten, field audit), `TextFit`, `PacketBuilder` (image pages, PDF merge, size cap) |
| `app/…/scan/` | ML Kit document scanner wrapper |
| `app/…/data/` | Room entities and DAOs, `SecureDatabase` and `KeystorePassphrase`, repository, backup |
| `app/…/ui/` | Compose screens: dashboard, family profiles, claim wizard, settings, debug-only form harness |
| `app/src/main/assets/forms/dd2642_template.pdf` | The official form with its XFA layer and owner-password restrictions removed by `tools/prepare_template.py` |
| `docs/` | Privacy policy, Play Console answers and store text, device gate checklist |

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

### Release bundle

Create `keystore.properties` in the project root (it is git-ignored):

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

## What still needs a real device

See `docs/device-gate-checklist.md`. Camera scanning, saving to Drive or an SD card, Keystore-backed encryption and the R8-shrunk release build cannot be exercised by the JVM tests.

## Third-party components

PdfBox-Android (Apache 2.0), SQLCipher for Android (BSD-style), AndroidX and Jetpack Compose (Apache 2.0), Google ML Kit document scanner (Google APIs terms). DD Form 2642 is a U.S. Government form.
