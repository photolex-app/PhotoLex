# Play Store Listing Draft for PhotoLex

## Short description (max 80 characters)

`Find any photo by the text inside it — 100% offline & private.` (63 chars)

## Full description (Play Store allows up to 4000 characters)

Ever taken a photo of your ID card, a receipt, a whiteboard, or a screenshot — and then lost it in thousands of other photos?

**PhotoLex** finds it for you. Just type a word you remember seeing in the photo (a name, a number, anything), and PhotoLex searches the *actual text inside your photos* to find it — instantly, and entirely on your device.

**How it works**
PhotoLex uses on-device text recognition (OCR) to scan your gallery once in the background, then builds a private, searchable index — all stored locally. No photo, no extracted text, and no search query ever leaves your phone.

**Why PhotoLex**
- 🔍 **Search photos by their text** — find ID cards, documents, screenshots, signs, whiteboards, anything with visible text
- 🔒 **100% offline and private** — no internet connection required, no cloud, no account, no ads, no analytics
- ⚡ **Background indexing** — scanning continues even when the app is closed, so it's ready when you need it
- 🌍 **Multi-language OCR** — supports Latin-script languages and Devanagari (Hindi and related scripts)
- 📖 **Full gallery experience** — PhotoLex is also a complete, modern photo gallery app, not just a search tool
- 🆓 **Free and open source** — the full source code is publicly available for anyone to inspect

If you've ever scrolled through hundreds of photos trying to find "that one screenshot," PhotoLex is built for exactly that moment.

## Suggested category

**Photography** (alternative: Tools)

## Data Safety section (Google Play) — answers based on actual codebase inspection

- **Data collected: None.** The app requests no analytics, advertising, or tracking SDKs (verified: no Firebase, Crashlytics, Google Analytics, AdMob, or similar in the build dependencies).
- **Data shared with third parties: None.**
- **Data encrypted in transit: Not applicable** — the app does not transmit user data.
- **Users can request data deletion: Not applicable** — no data is collected or stored outside the user's own device.
- **Note on ML Kit**: on first use, Google's ML Kit may download a generic (non-personal) language-recognition model via Google Play Services. This is a one-time technical download of the recognition model itself, not user data, and should be mentioned for transparency even though it doesn't count as "data collected" about the user.
- **Note on Google Lens integration**: an optional, user-initiated button hands off to Google's own Lens app/website via an Android Intent — this is governed by Google's own privacy policy, not PhotoLex's, and involves no background data transmission by PhotoLex itself.

## Follow-up items for the developer to decide

1. **Contact email**: the privacy policy has a `[YOUR EMAIL HERE]` placeholder — needs a real address before publishing.
2. **INTERNET permission**: the app's own manifest does not declare the `INTERNET` permission directly, and the in-app auto-updater (which used Fuel/HTTP calls) is being disabled in a parallel change. Worth double-checking, once that's finalized, whether `INTERNET` still ends up in the final merged manifest (it may be pulled in automatically by the ML Kit / Google Play Services libraries for their one-time model download) — if it's genuinely unused after that, it can potentially be excluded, though ML Kit's model download likely still needs it.
3. **Screenshots and feature graphic**: Play Store listings require actual screenshots and a feature graphic image, not covered by this document — will need to be created separately once the app's UI is finalized.
