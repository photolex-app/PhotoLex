# Play Store Listing Draft — Hindi (hi-IN) Localization for PhotoLex

This is a **culturally adapted draft, not a literal machine translation** of `docs/STORE_LISTING.md`. It mixes
Devanagari with common English loanwords the way real Indian Hindi tech writing does (OTP, UPI, PDF, Wi-Fi,
screenshot stay in Roman script — that's how Hindi speakers actually read these terms, not transliterated).
It also keeps a few Romanized-Hindi/Hinglish phrases inside the **English** default listing, since most Indian
users type Play Store search queries in Roman script even when thinking in Hindi — a Devanagari-only listing
would miss that traffic entirely. Have a native Hindi speaker proofread before publishing; tone/idiom matters
a lot on a public store page and this wasn't proofed by one.

**Before publishing**: paste each field into Play Console and let it show the live character count — Devanagari
conjuncts can count as more than one UTF-16 unit, so the counts below are estimates, not guaranteed exact.

---

## Title (hi-IN) — Play Console limit: 30 characters

Three options, pick one (or A/B test — Play Console supports listing experiments):

- **A (recommended):** `PhotoLex: हिंदी फोटो खोज`
  *(PhotoLex: Hindi photo search — leads with the differentiator)*
- **B (shorter, safer on char count):** `PhotoLex फोटो खोज`
- **C (leads with action verb):** `PhotoLex: टेक्स्ट खोजें`

## Short description (hi-IN) — Play Console limit: 80 characters

`फ़ोटो में लिखा टेक्स्ट खोजें — आधार, बिल, स्क्रीनशॉट। हिंदी + English, 100% ऑफलाइन।`

*(Search text hidden in your photos — Aadhaar, bills, screenshots. Hindi + English, 100% offline.)*

## Full description (hi-IN) — Play Console limit: 4000 characters

आपके फ़ोन में कहीं आधार कार्ड की फ़ोटो पड़ी है, वो वाई-फ़ाई पासवर्ड जो स्टिकी नोट पर लिखा था, मीटिंग वाला
व्हाइटबोर्ड, डॉक्टर की पर्ची, या बैंक ट्रांसफ़र का स्क्रीनशॉट — और अब वो मिल नहीं रहा। पता है कि है कहीं,
बस 10,000 फ़ोटो में ढूंढना मुश्किल है।

**PhotoLex हर फ़ोटो के अंदर लिखा टेक्स्ट पढ़ लेता है, ताकि आप उसे किसी डॉक्यूमेंट की तरह खोज सकें।** कोई भी
शब्द टाइप करें — नाम, नंबर, कंपनी का नाम, कुछ भी — और PhotoLex फ़ोटो के अंदर की *असली लिखावट* में खोजकर तुरंत
सही फ़ोटो ढूंढ देता है, सिर्फ़ फ़ाइल के नाम या तारीख़ से नहीं। यह सब कुछ आपके फ़ोन पर ही होता है — न कोई क्लाउड,
न अकाउंट, न अपलोड।

**हिंदी में भी पूरा सपोर्ट** — PhotoLex अंग्रेज़ी और हिंदी (देवनागरी) दोनों में लिखा टेक्स्ट पढ़ सकता है, तो
आधार कार्ड, राशन कार्ड, या हाथ से लिखे हिंदी नोट्स भी उतनी ही आसानी से खोजे जा सकते हैं जितनी अंग्रेज़ी वाली
फ़ोटो।

**यह किन चीज़ों को ढूंढने में मदद करता है:**
- 🪪 **पहचान पत्र** — आधार कार्ड, पैन कार्ड, पासपोर्ट, ड्राइविंग लाइसेंस, वोटर आईडी, राशन कार्ड, एम्प्लॉई/स्टूडेंट आईडी
- 🧾 **बिल, रसीद और इनवॉइस** — बिजली का बिल, रिचार्ज, ग्रोसरी रसीद, रेस्टोरेंट बिल, GST इनवॉइस, मेडिकल बिल
- 💳 **पेमेंट और बैंकिंग** — UPI/PhonePe/GPay ट्रांज़ैक्शन स्क्रीनशॉट, बैंक स्टेटमेंट, चेक की फ़ोटो, अकाउंट नंबर
- 📱 **स्क्रीनशॉट** — WhatsApp चैट, ऑर्डर कन्फ़र्मेशन, OTP, बोर्डिंग पास, मूवी/ट्रेन/फ़्लाइट टिकट, एडमिट कार्ड
- 📝 **हाथ से लिखे और व्हाइटबोर्ड नोट्स** — मीटिंग नोट्स, टू-डू लिस्ट, लेक्चर नोट्स, कागज़ पर लिखा फ़ोन नंबर
- 💊 **मेडिकल रिकॉर्ड** — डॉक्टर की पर्ची, दवाई की स्ट्रिप, लैब रिपोर्ट, वैक्सीनेशन सर्टिफ़िकेट, इंश्योरेंस कार्ड
- 🏠 **घर से जुड़ी जानकारी** — वाई-फ़ाई पासवर्ड, वारंटी कार्ड, सीरियल नंबर, अप्लायंस लेबल
- 💼 **बिज़नेस कार्ड और कॉन्टैक्ट** — किसी कार्ड की फ़ोटो से नाम, कंपनी, या फ़ोन नंबर
- 📄 **फ़ॉर्म और एग्रीमेंट** — रेंट एग्रीमेंट, एप्लिकेशन फ़ॉर्म, सरकारी नोटिस, सर्टिफ़िकेट
- 🎫 **टिकट और QR/बारकोड** — इवेंट टिकट, कूपन, और स्कैन करने योग्य कोड वाली कोई भी रसीद

**यह कैसे काम करता है**
PhotoLex आपकी गैलरी को एक बार बैकग्राउंड में स्कैन करके, फ़ोटो के अंदर का टेक्स्ट (OCR) पढ़ लेता है, और एक
प्राइवेट, सर्च करने लायक इंडेक्स बना लेता है — सब कुछ आपके फ़ोन में ही, लोकल तौर पर। कोई फ़ोटो, कोई निकाला गया
टेक्स्ट, या कोई सर्च क्वेरी कभी आपके फ़ोन से बाहर नहीं जाती।

**मुख्य फ़ीचर्स**
- 🔍 **पूरी गैलरी में फ़ुल-टेक्स्ट सर्च** — फ़ज़ी-मैच के साथ, तो स्पेलिंग थोड़ी गलत होने पर भी सही फ़ोटो मिल जाती है
- 🧭 **मिलती-जुलती फ़ोटो ढूंढें (Find Similar)** — एक आईडी कार्ड या बिल चुनें, PhotoLex वैसी ही बाकी फ़ोटो ढूंढ देगा
- 🗂️ **स्मार्ट एल्बम** — फ़ोटो अपने आप बिल, आईडी कार्ड, स्क्रीनशॉट जैसी कैटेगरी में बंट जाती हैं
- 📇 **बारकोड और QR कोड सर्च** — रसीद, टिकट, कूपन अब उनके स्कैन कोड से भी खोजे जा सकते हैं
- 🌐 **क्रॉस-लैंग्वेज सर्च (अंग्रेज़ी ⇄ हिंदी)** — अंग्रेज़ी में खोजें तो हिंदी वाले नतीजे भी मिलें, और उल्टा भी
- 🌍 **मल्टी-लैंग्वेज OCR** — अंग्रेज़ी, स्पैनिश, फ़्रेंच, जर्मन जैसी भाषाओं के साथ-साथ देवनागरी (हिंदी, मराठी, नेपाली, संस्कृत)
- ⚡ **बैकग्राउंड इंडेक्सिंग** — ऐप बंद होने पर भी स्कैनिंग चलती रहती है
- 🔒 **100% ऑफलाइन और प्राइवेट** — कोई क्लाउड, अकाउंट, विज्ञापन, ट्रैकिंग नहीं
- 📖 **पूरा गैलरी ऐप** — PhotoLex सिर्फ़ सर्च टूल नहीं, एक पूरा मॉडर्न फ़ोटो गैलरी ऐप भी है (एल्बम, फ़ेवरेट, ट्रैश, सिक्योर फ़ोल्डर)
- 🆓 **फ़्री और ओपन सोर्स** — पूरा सोर्स कोड सबके देखने के लिए पब्लिक है

अगर कभी सोचा है "कहीं तो होगी वो फ़ोटो," तो PhotoLex बिल्कुल इसी पल के लिए बना है।

---

## Suggested category

Same as English listing: **Photography** (alternative: Tools)

## Recommended addition to the English (default) listing

Add one line near the top of the existing English full description to catch Romanized-Hindi/Hinglish search
traffic (most Indian users type Play Store queries in Roman script even when thinking in Hindi, so a
Devanagari-only listing misses this entirely):

> **Hindi photo search built in** — PhotoLex reads Devanagari (Hindi, Marathi, Nepali, Sanskrit) text inside
> photos too, not just English. Search "aadhar card", "hindi text photo search", or any Hindi word and find it.

This keeps the existing English listing intact while adding a few Romanized-Hindi phrases a Hinglish-typing
searcher is likely to use, without needing a second full translation pass on the English listing itself.

---

## Localized (hi-IN) screenshot set — plan

Play Console supports uploading a different screenshot set per language, shown only to users browsing in that
locale. Same privacy rule as the existing English screenshots (`docs/store_assets/`): **no real personal photo
content ever** — every screenshot below uses synthetic/mock demo data (fake ID-card-style mockups, placeholder
bill text), same as how the current 4 screenshots were produced. Each gets a short Hindi caption graphically
overlaid on top of the real screenshot (standard ASO technique — not just a raw screenshot), so the *message*
lands even for someone who wouldn't read the Play Store description at all.

| # | What it shows | Hindi overlay caption | Why this one |
|---|---|---|---|
| 1 | Search bar with a Hindi query typed in (e.g. "आधार") and matching result thumbnails appearing | **"हिंदी में भी खोजें"** (Search in Hindi too) | Leads with the single most important differentiator, in the first screenshot anyone sees |
| 2 | Photo viewer open on a mock document, with the matched Hindi text highlighted on the image itself (reuses the existing search-highlight feature) | **"फ़ोटो में लिखा टेक्स्ट तुरंत मिले"** (Instantly find text written in a photo) | Shows *how* the search actually feels — highlighting is a strong, self-explanatory visual, no reading required to get it |
| 3 | Smart Albums grid (Bills, ID Cards, Screenshots categories) | **"अपने आप बंटी फ़ोटो, अलग-अलग कैटेगरी में"** (Photos auto-sorted into categories) | Communicates the "gallery app" breadth, not just search |
| 4 | OCR Language Models settings screen showing both Latin and Devanagari indexing toggles/progress | **"हिंदी + English, दोनों में काम करे"** (Works in both Hindi and English) | Makes the dual-language claim concrete/visible, not just a marketing line |
| 5 | Cross-language search demo: English query typed, Hindi-text result surfaced (or vice versa) | **"अंग्रेज़ी में खोजें, हिंदी नतीजे पाएं"** (Search in English, get Hindi results) | A genuinely unusual feature among competitors — worth its own screenshot rather than burying it in feature #1's caption |
| 6 | A simple "100% offline" badge/screen (e.g. Settings privacy section) | **"पूरी तरह ऑफ़लाइन — कोई डेटा शेयर नहीं होता"** (Fully offline — no data ever shared) | Addresses the trust/privacy objection directly, in the language of the audience most likely to be wary of ID-card photos leaving their phone |

**Feature graphic (1024x500, hi-IN)**: same icon + "PhotoLex" wordmark as the English one, swap the tagline for
`फ़ोटो में छुपा टेक्स्ट खोजें` (Find text hidden in your photos) — this is the banner shown before anyone
scrolls to the screenshots, so it should carry the Hindi hook on its own.

**Production note**: the existing English screenshots and feature graphic were made via ADB screen captures
(app UI) composited with a small Java/Graphics2D program for text/graphics overlay (see project memory) — same
pipeline works here, just swap in Hindi caption text and the mock Hindi-text demo content. This is a build
task, not yet done — this table is the spec to build against next.

