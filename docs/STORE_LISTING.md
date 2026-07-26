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
- 🧭 **Find similar documents** — pick one photo (like an ID card or bill) and PhotoLex finds other photos of the same kind, so you can group them into an album
- 🗂️ **Smart Albums** — photos are automatically sorted into browsable categories like Bills & Receipts, ID Cards, Screenshots, and Documents
- 🌐 **Cross-language search** — a search in English can also surface matching results in Hindi, and vice versa
- 🔒 **100% offline and private** — no cloud storage, no account, no ads, no analytics; on-device recognition models are downloaded once via Google Play Services, and after that everything works fully offline
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
- **Note on ML Kit**: on first use, Google's ML Kit may download generic (non-personal) models via Google Play Services — a text-recognition model for OCR, and a translation model for the cross-language search feature. These are one-time technical downloads of the models themselves, not user data, and should be mentioned for transparency even though they don't count as "data collected" about the user.
- **Note on Google Lens integration**: an optional, user-initiated button hands off to Google's own Lens app/website via an Android Intent — this is governed by Google's own privacy policy, not PhotoLex's, and involves no background data transmission by PhotoLex itself.
- **INTERNET permission — confirmed present, and why**: verified directly in the built app's merged manifest that `android.permission.INTERNET` is present, pulled in transitively by the ML Kit libraries (not declared by PhotoLex's own manifest). It exists solely for the one-time OCR/translation model downloads described above — there is no code path anywhere in the app that transmits photos, extracted text, or search queries. Worth calling out explicitly in the Data Safety form/description so it doesn't read as contradicting the "100% offline and private" claim.

## Follow-up items for the developer to decide

1. ~~Contact email~~ — done, `photolex.app@gmail.com` is filled in throughout.
2. ~~INTERNET permission~~ — resolved, see the note above; no action needed, just make sure the Data Safety form and listing copy account for it.
3. **Screenshots and feature graphic**: Play Store listings require actual screenshots and a feature graphic image (1024x500), not covered by this document — next thing to prepare.
