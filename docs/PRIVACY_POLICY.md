# Privacy Policy for PhotoLex

_Last updated: 2026-07-30_

PhotoLex is a photo gallery app that lets you search your photos by the text visible inside them (screenshots, ID cards, documents, signs, etc.). This policy explains, in plain language, what the app does and doesn't do with your data.

## The short version

PhotoLex does not collect, transmit, sell, or share any of your personal data, photos, or extracted text. Everything the app does happens on your own device. There are no user accounts, no ads, and no analytics or tracking of any kind built into this app.

## What PhotoLex accesses on your device

- **Your photos and videos** (via Android's media permissions), so it can display them in the gallery and scan them for text.
- **Storage**, to read and organize your media, and to save app data such as the search index.
- Optional permissions like **biometric unlock** (if you choose to lock a private folder), **notifications** (to show OCR indexing progress), and **wallpaper-setting** (if you choose to set a photo as wallpaper) — these are only used for the feature they're named after.

None of this data ever leaves your device. There is no server operated by us that your photos, extracted text, or search queries are sent to.

## About the on-device text recognition (OCR), classification, and translation

PhotoLex uses Google's ML Kit for several on-device features: reading text out of your photos (text recognition), organizing photos into Smart Albums (entity extraction, to spot dates/amounts/phone numbers in extracted text, and image labeling, to recognize general photo content), deciding whether a photo's text is likely Hindi/Devanagari before running the heavier Devanagari recognizer (language identification), and — for the optional cross-language search feature — translating a short search query between English and Hindi. The first time each of these features runs, ML Kit may download a small, generic model file via Google Play Services — this is a one-time technical download of the *model itself*, not your photos, your extracted text, or any personal data. After that, all recognition, classification, translation, and search happens fully offline, with no further network activity from these features.

**Why this app requests the INTERNET permission**: PhotoLex's own code never uses it to transmit anything — it's declared by the ML Kit libraries themselves, solely so they can perform the one-time model downloads described above. You can confirm this by inspecting the source code: PhotoLex has no server, no API calls, and no code path that sends your photos, extracted text, or search queries anywhere.

PhotoLex also scans photos for barcodes/QR codes (to make ticket and receipt codes searchable) using ML Kit's barcode scanning, which ships fully bundled in the app with no model download or network access at all.

## Google Lens integration (optional, user-initiated)

If you choose to tap the "Search with Google Lens" option on a photo, PhotoLex opens Google's own Lens app or website to handle that specific request. That handoff is subject to **Google's own privacy policy**, not this one — PhotoLex itself does not transmit anything in the background; it only opens Google's app when you explicitly tap that button.

## No accounts, no ads, no analytics

- No sign-up or login is required or possible.
- No advertising SDKs are included in this app.
- No analytics, crash-reporting, or tracking SDKs (e.g. Firebase, Google Analytics, Crashlytics) are included in this app.
- No data is sold or shared with third parties, because no data is collected in the first place.

## Data Safety (Google Play)

This policy reflects what should be declared in Google Play's "Data Safety" section: **no data collected, no data shared**. If future versions of this app add any feature that changes this (e.g. a real update-check service or optional cloud backup), this policy will be updated accordingly before that feature ships.

## Open source

PhotoLex is open source and built on Tulsi Gallery (github.com/AKS-Labs/Tulsi), itself a fork of LavenderPhotos — both licensed under the GNU General Public License v3.0. You (or anyone) can inspect the full source code to verify these claims yourself.

## Contact

If you have questions about this privacy policy, contact: **photolex.app@gmail.com**
