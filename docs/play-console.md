# Google Play Console declarations

Answers to enter in the Play Console for 2642 Easy. They describe version 1.0.0 as built from this repository; re-check them whenever a dependency or permission changes.

## Data safety form

| Question | Answer |
| --- | --- |
| Does your app collect or share any of the required user data types? | **No** |
| Data collected | **None** |
| Data shared | **None** |
| Is all of the user data collected by your app encrypted in transit? | Not applicable: no data is transmitted. The app has no `INTERNET` permission |
| Do you provide a way for users to request that their data is deleted? | Not applicable (nothing collected). On-device data is erased with Settings → Clear All Local Data |

Reasoning: Play defines "collected" as data transmitted off the device by the app or its SDKs. Profiles and claims stay in an encrypted on-device database. Files the user saves through the system picker or share sheet are user-initiated transfers handled by other apps. The ML Kit document scanner is declared by Google as on-device processing.

Before each release, confirm with `aapt2 dump permissions` on the release artifact that no `android.permission.*` entry has been merged in by a library update. The only expected entry is AndroidX's app-private `com.easy2642.app.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`.

## Health apps declaration

- **Category:** Health → *Medical* is **not** selected. Select **"Health insurance / benefits management"** (administrative) under the health features list; if the current form offers no exact match, choose **Other** and describe: "Prepares and tracks insurance reimbursement claim paperwork. Administrative only."
- **Diagnostic, treatment or telemedicine functionality:** **None.** The app does not provide medical advice, read medical devices, or connect users with clinicians.
- **Health data:** the free-text illness description, provider names and amounts a user types stay on the device and are not collected.
- **Regulated medical device:** No.

## App content

| Item | Answer |
| --- | --- |
| Privacy policy URL | Public URL where `docs/privacy-policy.md` is hosted |
| Ads | No ads |
| App access | All functionality available without login |
| Target audience | 18 and over |
| Government app | **No**: not a government app and not affiliated with one |
| Financial features | None |
| Content rating | Complete the questionnaire: utility, no user-generated content shared, no objectionable content |

## Store listing

**App name:** 2642 Easy

**Short description (80 max):** Fill, bundle and track DD Form 2642 claim packets. Private and offline.

**Full description:**

> 2642 Easy helps military families prepare medical claim packets without the paperwork headache.
>
> • Fill in DD Form 2642 from saved family profiles in a couple of minutes
> • Scan itemized bills, receipts and proof of payment with your camera, or import screenshots and PDFs
> • Get one tidy PDF, form first and attachments after, kept under 10 MB for portal uploads
> • Save it wherever you choose: device storage, SD card or your cloud drive app
> • Track every claim: needs filing, awaiting reimbursement, completed
>
> Private by design. No account, no ads, no internet permission. Your information is encrypted on your device and never sent to us.
>
> DISCLAIMER: 2642 Easy is an independent document preparation tool. It is not affiliated with, endorsed by, or sponsored by TRICARE, the Defense Health Agency (DHA), or the Department of Defense (DoD). Users remain responsible for submitting completed claim packets to their respective claims processor or portal.

**Listing rules**

- Do not use the TRICARE, DHA, DoD or service-branch logos, seals or insignia in the icon, feature graphic or screenshots.
- "TRICARE" and "DD Form 2642" may appear only as plain descriptive text, and the disclaimer above must stay in the full description.
- Do not put "official", "government" or "TRICARE" in the app name or developer name.
- Screenshots must show synthetic data only. Use a debug build (screenshots are blocked in release builds) and the sample data in Settings → Form engine test harness; never a real profile.
