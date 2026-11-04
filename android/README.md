# MyEHR Android app

Native Kotlin + Jetpack Compose. Hebrew, right-to-left.

## What works now
- **App lock:** fingerprint or phone PIN every time the app opens. Screenshots are blocked.
- **Sync:** opens the real Meuhedet site in the app. You log in yourself, and the app then pulls data for the **logged-in person**: lab results (list, details, history for graphs), medications (active, purchased, approvals, prescriptions), documents list, vaccines, ECG/institutes and the medical summary. When it's done, the app logs out of the site (clears cookies).
- **Storage:** an encrypted database (SQLCipher). Its key is protected by the Android Keystore. No backups, no server.
- **Screens:** lab results by test (graph with normal range, out-of-range values in red) and by date (full detail), medications, documents.

## Free questions (שאלה tab)
Ask in Hebrew or English: "what is my last hemoglobin?", "how did my cholesterol change since I started X?".
Answered by Claude (Anthropic API, model `claude-opus-5-5`) using **your own API key**, stored encrypted on the phone.

What leaves the phone is only what the question needs, fetched through 4 local tools:
test names, values, units, normal ranges and dates, medication names with first and last purchase dates, and age.
**Never sent:** name, ID, phone, email, birth date, doctors, labs, clinics, pharmacies, or free text such as notes.
A guard also scrubs any of those strings, and any 8+ digit number, from every outgoing message.
- The first time each kind of data is needed, the app shows what will be sent and asks for OK.
- "מה נשלח ל-Claude?" under each answer shows exactly what was sent. The log is kept in the encrypted database.
- Server-side fallback is on: if Opus 5.5 declines a question, Anthropic retries it on another Claude model.
- Answers describe trends and timing only. They are not medical advice.

## Not yet
- Family members (waiting for the family-switch recording).
- Opening PDFs.
- Encrypted backup file.

## Build
GitHub Actions builds the APK on every push that changes `android/`. Download it from the run's **Artifacts** (`myehr-apk`).

Locally: `cd android && ./gradlew assembleDebug` (needs JDK 17+ and the Android SDK).

### Same signing key on every build (needed so updates install over the old app)
Create a keystore once on your computer and keep a copy safe:
```
keytool -genkeypair -v -keystore myehr.jks -alias myehr -keyalg RSA -keysize 4096 -validity 10000
base64 -w0 myehr.jks > myehr.jks.b64
```
In GitHub, go to Settings → Secrets and variables → Actions, and add:
`MYEHR_KEYSTORE_BASE64` (the content of `myehr.jks.b64`), `MYEHR_KEYSTORE_PASSWORD`, `MYEHR_KEY_ALIAS` (`myehr`), `MYEHR_KEY_PASSWORD`.
Never commit the keystore. The repo is public.

## Install on a phone
1. Download the APK on the phone.
2. Allow "install unknown apps" for your browser or Files app when asked.
3. Open the APK and install.
