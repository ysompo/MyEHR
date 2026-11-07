# MyEHR developer guide

## Ground rules
- **The repo is public.** Never commit real or realistic personal values, recordings, HAR files or samples from them. Use field names and types only. Test fixtures use made-up values.
- Never store, type or replay passwords or SMS codes. The user always logs in on Meuhedet's own page.
- No server. Data lives only on the device, encrypted.

## Repository layout
```
android/
  common/src/main/kotlin/com/myehr/app/   shared by Android and desktop
    data/     Models, Parsers (tolerant JSON), Repo (read-time queries, RecordStore)
    sync/     SyncEngine (endpoints, incremental sync), SyncState
    ai/       AiAgent (Claude tool loop), AiTools (allow-listed tools, Guard), AskScreen
    ui/       Theme, Components, Dashboard, Labs, MedsDocs, Visits, Settings, Lock, App, Platform
  app/        Android app (Compose)
    data/     Store (SQLCipher + Keystore), Secrets
    sync/     SyncScreen (login WebView), SyncService (foreground), NativeCaller (OkHttp)
    update/   Updater (download + SHA-256 + PackageInstaller), UpdateUi
  desktop/    Compose for Desktop app (Windows / macOS)
    DesktopStore (SQLite + AES-GCM, key in OS keychain), DesktopSync (JavaFX login + HttpClient),
    DesktopUpdates, DesktopPlatform, Main (app password)
docs/         user guide, this guide, site map, original plan
tools/        recorders used to map the site (masked output)
.github/workflows/  android.yml, desktop.yml
```

## Architecture

### Shared code
`android/common` is a plain source folder added to both modules (`sourceSets ... srcDir("../common/src/main/kotlin")`).
Platform-specific things go through the `Platform` interface (`ui/Platform.kt`), provided with `LocalPlatform`:
storage (`repo`), secrets, AI log, calendar, icons, back handling, the sync screen and update UI.

### Storage
- Raw JSON answers from the site are stored per `(person, kind, key)` and parsed when shown (`Repo`). A parser fix never needs a re-sync.
- **Android:** SQLCipher database. The 32-byte passphrase is encrypted with an AES-GCM key in the Android Keystore. Backups are disabled.
- **Desktop:** SQLite, every value encrypted with AES-256-GCM. The key is in the OS keychain (`java-keyring`, service `MyEHR`). Data folder: `%APPDATA%\MyEHR`, `~/Library/Application Support/MyEHR`, or `~/.myehr`.

### Login and sync
1. The user logs in on `online.meuhedet.co.il` in a WebView (Android) or JavaFX WebView (desktop). Trackers are blocked; navigation stays on `*.meuhedet.co.il`.
2. When `GetCurrentMemberinfo` answers with JSON, the session cookies are handed to a native HTTP client:
   - Android: `SyncService` (foreground service, `dataSync`, wake lock) with `NativeCaller` (OkHttp, cookie jar backed by the WebView `CookieManager`). Runs with the screen off.
   - Desktop: `DesktopCaller` (`java.net.http.HttpClient`) sharing a `java.net.CookieManager` with the WebView.
3. `SyncEngine.syncFamily` syncs the logged-in person, then each family member. Switching person = setting cookie `meuhedet.current.member2` to the member's `Guid`; clearing it goes back.
4. Incremental: lab details only for new `StickerId`s; history only when there are new results. A `history_pending` state flag makes an interrupted sync resume (6 h window).
5. Session loss (401/403, login or signout URL, HTML instead of JSON) throws `SessionExpired`; the UI asks to log in again.
6. At the end, all cookies are cleared (signed out).

Endpoints and fields: [SITE_MAP.md](SITE_MAP.md). If the site changes, re-record with [tools/recorder-ext](../tools/recorder-ext/README.md) and update `SyncEngine` / `Parsers`.

### Questions (AI)
- `AiAgent` calls the Anthropic Messages API (beta) with model `claude-opus-5-5`, server-side fallback enabled, and up to 12 tool rounds.
- Tools run locally: `list_lab_tests`, `get_lab_history`, `list_medications`, `get_age`. They return allow-listed fields only.
- `Guard` scrubs personal strings (names, IDs, phones, doctors, clinics...) and any 8+ digit run from every outgoing message.
- Consent is asked the first time each `DataKind` is needed. Every exchange (question, what was sent, answer) is logged locally.
- The user's API key is stored encrypted (Keystore / OS keychain).

### Uploaded documents
- `ui/UploadScreens.kt`: pick pages (camera / file) → user OKs sending → `ai/DocReader` sends the pages (image or PDF blocks) with a forced `save_findings` tool → the user ticks what to keep.
- Stored as `Upload` (kind `upload`) and `Condition` (kind `condition`, also used for hand-added diagnoses, allergies and notes). Pages go to the encrypted `FileVault` (`AndroidVault` / `DesktopVault`) as `<id>_<n>`.
- `Repo.mergeUploads` adds uploaded lab values to the Meuhedet series with the same code or name; a value on a day Meuhedet already has is dropped. `LabPoint.source` / `MedSummary.source` mark where a value came from; the AI sees only "Meuhedet" or "uploaded document".
- Pages are viewed in-app: `PdfRenderer` on Android, PDFBox on desktop.

### App lock
- Android: `BiometricPrompt` (biometric or device credential) on every start; `FLAG_SECURE` blocks screenshots.
- Desktop: app password, stored as a salted PBKDF2 hash (210k iterations) in the keychain.

## Build and run
Requirements: JDK 17+; Android SDK for the Android app.
```
cd android
./gradlew assembleDebug            # Android APK
./gradlew :desktop:run             # desktop app
./gradlew :desktop:createDistributable   # desktop app folder for this OS
./gradlew :desktop:packageMsi      # on Windows
./gradlew :desktop:packageDmg      # on macOS
```

## Tests
```
./gradlew testDebugUnitTest        # parsers, sync engine (family + incremental), AI tool loop (MockWebServer)
./gradlew :desktop:test            # encrypted store round trip, home screen render
./gradlew testDebugUnitTest -Pdemo # renders demo screenshots (Roborazzi) with made-up data
```

## Releases and updates
| Workflow | Trigger | Output |
|---|---|---|
| `android.yml` | push changing `android/**` | Signed APK + `version.json` (`versionCode` = run number, `sha256`) on release `android-latest` |
| `desktop.yml` | push changing `android/common`, `android/desktop` or the root Gradle files | `MyEHR-Windows.msi`, `MyEHR-Mac-AppleSilicon.dmg`, `MyEHR-Mac-Intel.dmg` + `desktop-version.json` on release `desktop-latest` |

- The Android app checks `version.json` daily, downloads the APK, verifies the SHA-256 and installs it with `PackageInstaller` (the user confirms). Android also refuses an APK signed with a different key.
- The desktop app checks `desktop-version.json` daily and links to the release page.

### Android signing key (once)
Every release must be signed with the same key, or updates won't install over the old app.
```
keytool -genkeypair -v -keystore myehr.jks -alias myehr -keyalg RSA -keysize 4096 -validity 10000
base64 -w0 myehr.jks > myehr.jks.b64
```
Add repository secrets (Settings → Secrets and variables → Actions):
`MYEHR_KEYSTORE_BASE64`, `MYEHR_KEYSTORE_PASSWORD`, `MYEHR_KEY_ALIAS`, `MYEHR_KEY_PASSWORD`.
Keep a safe copy of the keystore. Never commit it. Without the secrets, CI builds a debug-signed APK.

The desktop installers are not code-signed, so Windows and macOS show an "unknown developer" warning.

## Not done yet
- Opening PDFs inside the app (file URLs are in the site map).
- Encrypted backup export / import.
- Prescription file URL, diagnoses and sensitivities (not found on the site yet).
