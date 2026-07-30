# Play Store Listing Draft for PhotoLex

## Short description (max 80 characters)

`Search photos by text inside them — IDs, bills, screenshots. 100% offline.` (76 chars)

## Full description (Play Store allows up to 4000 characters)

Somewhere in your camera roll is a photo of your Aadhaar card, that WiFi password written on a sticky note, the whiteboard from a meeting, a prescription your doctor wrote out, or a screenshot of a bank transfer — and you cannot find it. You know it's in there. You just can't scroll through 10,000 photos to get to it.

**PhotoLex reads the text inside every photo, so you can search for it like a document.** Type any word you remember seeing — a name, a number, a company, anything — and PhotoLex finds the photo instantly, searching the *actual text in the image*, not just the filename or date. Everything happens on your device. No cloud, no account, no upload, ever.

**Perfect for finding:**
- 🪪 **ID documents** — Aadhaar card, PAN card, passport, driving license, voter ID, ration card, employee ID, student ID
- 🧾 **Bills, receipts & invoices** — electricity bill, phone/DTH recharge, grocery receipts, restaurant bills, GST invoices, medical bills
- 💳 **Payments & banking** — UPI/PhonePe/GPay transaction screenshots, bank statements, cheque photos, account numbers
- 📱 **Screenshots** — WhatsApp chats, order confirmations, OTPs, boarding passes, movie/train/flight tickets, exam admit cards, appointment confirmations
- 📝 **Handwritten & whiteboard notes** — meeting notes, to-do lists, lecture notes, recipe cards, phone numbers jotted on paper
- 💊 **Medical records** — prescriptions, medicine strips, lab reports, vaccination certificates, insurance cards
- 🏠 **Household info** — WiFi passwords, warranty cards, serial numbers, product manuals, appliance labels
- 💼 **Business cards & contacts** — a name, company, or phone number from a card you photographed instead of saving
- 📄 **Forms & contracts** — rental agreements, application forms, government notices, certificates
- 🎫 **Tickets & QR/barcodes** — event tickets, coupons, and any receipt or pass with a scannable code

**How it works**
PhotoLex uses on-device text recognition (OCR) to scan your gallery once in the background, then builds a private, searchable index — all stored locally on your phone. No photo, no extracted text, and no search query ever leaves your device.

**Key features**
- 🔍 **Full-text search across your whole gallery** — search photos by the text visible inside them, with a fuzzy-match fallback that still finds results even if you misremember the exact word or spelling
- 🧭 **Find Similar** — pick one photo (like an ID card or bill) and PhotoLex finds other photos of the same kind, so you can group them into an album with one tap
- 🗂️ **Smart Albums** — photos are automatically sorted into browsable categories like Bills & Receipts, ID Cards, Screenshots, and Documents, no manual sorting needed
- 📇 **Barcode & QR code search** — receipts, tickets, and coupons become searchable by their scanned code, not just their text
- 🌐 **Cross-language search (English ⇄ Hindi)** — a search in English can also surface matching results in Hindi, and vice versa
- 🌍 **Multi-language OCR** — supports Latin-script languages (English, Spanish, French, German, Italian, Portuguese, and more) plus Devanagari (Hindi, Marathi, Nepali, Sanskrit)
- ⚡ **Background indexing** — scanning continues even when the app is closed, so search is ready whenever you need it
- 🔒 **100% offline and private** — no cloud storage, no account, no ads, no analytics, no tracking; on-device recognition models download once via Google Play Services, and after that everything works fully offline
- 📖 **Full gallery experience** — PhotoLex is also a complete, modern photo gallery app (albums, favorites, trash, secure folder), not just a search tool
- 🆓 **Free and open source** — the full source code is publicly available for anyone to inspect

If you've ever thought "I know I have a photo of that somewhere," PhotoLex is built for exactly that moment.

## Suggested category

**Photography** (alternative: Tools)

## Data Safety section (Google Play) — answers based on actual codebase inspection

- **Data collected: None.** The app requests no analytics, advertising, or tracking SDKs (verified: no Firebase, Crashlytics, Google Analytics, AdMob, or similar in the build dependencies).
- **Data shared with third parties: None.**
- **Data encrypted in transit: Not applicable** — the app does not transmit user data.
- **Users can request data deletion: Not applicable** — no data is collected or stored outside the user's own device.
- **Note on ML Kit**: on first use, Google's ML Kit may download generic (non-personal) models via Google Play Services — text recognition (OCR), translation (cross-language search), entity extraction and image labeling (Smart Albums categorization), and language identification (deciding whether a photo's text is likely Hindi before running the Devanagari recognizer). These are one-time technical downloads of the models themselves, not user data, and should be mentioned for transparency even though they don't count as "data collected" about the user. Barcode/QR scanning is a separate exception — it ships fully bundled in the app with no download or network access at all.
- **Note on Google Lens integration**: an optional, user-initiated button hands off to Google's own Lens app/website via an Android Intent — this is governed by Google's own privacy policy, not PhotoLex's, and involves no background data transmission by PhotoLex itself.
- **INTERNET permission — confirmed present, and why**: verified directly in the built app's merged manifest that `android.permission.INTERNET` is present, pulled in transitively by the ML Kit libraries (not declared by PhotoLex's own manifest). It exists solely for the one-time OCR/translation model downloads described above — there is no code path anywhere in the app that transmits photos, extracted text, or search queries. Worth calling out explicitly in the Data Safety form/description so it doesn't read as contradicting the "100% offline and private" claim.

## Follow-up items for the developer to decide

1. ~~Contact email~~ — done, `photolex.app@gmail.com` is filled in throughout.
2. ~~INTERNET permission~~ — resolved, see the note above; no action needed, just make sure the Data Safety form and listing copy account for it.
3. **Screenshots and feature graphic**: Play Store listings require actual screenshots and a feature graphic image (1024x500), not covered by this document — next thing to prepare.
