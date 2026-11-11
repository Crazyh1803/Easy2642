# 2642 Easy Privacy Policy

_Effective date: 11 November 2026_

2642 Easy is published by Apps by Dan ("the developer").

2642 Easy is an independent document preparation tool. It is not affiliated with, endorsed by, or sponsored by TRICARE, the Defense Health Agency (DHA), or the Department of Defense (DoD).

## Summary

**2642 Easy collects no data.** Everything you enter is processed and stored only on your device. The developer has no servers for this app and never receives your information.

## What the app stores, and where

| Data | Where it lives | Protection |
| --- | --- | --- |
| Sponsor and dependent profiles (names, dates of birth, SSN or DoD Benefits Number, address, phone) | An on-device database inside the app's private storage | Encrypted (SQLCipher, AES-256). The database key is itself encrypted by a key held in the Android Keystore |
| Saved signatures (only if you choose to save one) | Same encrypted database | Same. Delete them under Family, or with Clear all local data |
| Claim tracker entries (patient name, provider, date of service, amount, notes, filed/reimbursed status, and the location you saved the file to) | Same encrypted database | Same |
| Working copies of scanned pages, imported images/PDFs and the generated PDF files | The app's private cache, only while you are preparing a claim | Deleted when you leave the claim and again each time the app starts |
| App preferences (disclaimer acknowledged, chosen claims folder, default output format, the year the tip reminder was last shown) | App-private preferences | Contain no personal information |

## What leaves your device

Nothing leaves your device unless **you** send it:

- **Saving claim files or a backup.** You choose the destination in Android's system file picker. If you choose a cloud provider's folder (for example Google Drive), that provider's app handles the upload under its own privacy policy.
- **Sharing claim files.** The Android share sheet hands the PDF or PDFs to the app you pick.
- **Backup files** are readable, unencrypted JSON and include profile details such as the sponsor's SSN or DBN, and any saved signatures. Store them only somewhere you trust.
- **Links.** Settings contains links to this policy, the developer's website and an optional tip page, and a tip reminder may appear once a year. Tapping a link opens it in your browser; the app sends nothing itself, and those sites have their own privacy policies.

## Permissions and network

- The app requests **no Android system permissions**. It has no internet permission and cannot make network requests.
- It does not request camera, storage, contacts or location access. Document scanning is performed by the Google Play services document scanner, which runs outside this app, processes images on the device and returns only the pages you accept. Google Play services may download the scanner component itself; that is governed by Google's terms.
- No analytics, advertising, crash reporting or tracking libraries are included.
- App data is excluded from Android cloud backup and device-to-device transfer.

## Deleting your data

**Settings → Clear all local data** erases the database (profiles, claims and saved signatures), the encryption key, preferences and cached files. Uninstalling the app does the same. Files you saved or shared elsewhere are outside the app's control and must be deleted where you put them.

## Children

The app is intended for adult sponsors and family members preparing claims. Information about children is entered by a parent or guardian and stays on the device as described above.

## Changes and contact

Changes to this policy will be published at this address with a new effective date.

Questions about this policy or the app: contact Apps by Dan through <https://appsbydan.com/>.
