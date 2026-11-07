# MyEHR for Meuhedet: plan (Android only, draft)

> Original plan, kept for history. What was actually built is in [DEVELOPER.md](DEVELOPER.md).

## Goal
A personal medical file for me and my family, pulled from our Meuhedet accounts.
Data stays **only on the phone**. There is no server, no tracking and no ads.

## Users
- **One login** (any family member) is enough: Meuhedet's family link shows spouse and children from the same account.
- After login the app pulls each family member in turn through the family link.
- Each person gets a separate profile in the app.
- Runs on **two phones** (mine and my wife's). Each phone logs in and syncs on its own; no data passes between phones.

## Tech
| Part | Choice | Why |
|---|---|---|
| Language/UI | Kotlin + Jetpack Compose, Hebrew RTL | Native Android, no cross-platform overhead |
| Login | WebView with the real meuhedet.co.il page | User types ID/SMS into Meuhedet itself; app never stores the password |
| Fetch | After login, call the same endpoints the site uses (cookies/token from WebView) | Details come from the recording |
| Storage | Room + SQLCipher; key in Android Keystore | Encrypted at rest |
| Documents | PDFs saved encrypted; shown with Android PdfRenderer | No external viewer |
| Charts | Vico (Compose charts library) | Test value over time + normal-range band |
| App lock | Fingerprint / phone PIN on **every open** (BiometricPrompt) | Medical data |
| Build | GitHub Actions → signed APK | No Android Studio needed on your side |
| Install | Sideload APK ("install unknown apps") on both phones; same signing key so updates install over the old version | Free, no Play Store |

## Features (v1)
1. **Sync** (manual button, SMS code each time): log in → pull everything for all family members → log out. Show what changed since last sync.
2. **Lab results**: list by date and by test. Tap a test to see a graph over years with the normal range, out-of-range values marked.
3. **Medications**: current and past, with dates and prescriber.
4. **Documents**: list, open, share a single PDF on purpose.
5. **Profiles**: me, children, other adults. Switch at the top.
6. **Backup**: encrypted export file + import.

Later (brainstorm): visits/summaries, referrals, approvals, vaccines, appointments, reminders, search, notes, a summary to show a doctor.

## Steps
1. **Map the site.** Run [`tools/recorder`](../tools/recorder/README.md) and send the masked file. ← *we are here*
2. Write `docs/SITE_MAP.md` (endpoints, auth, how child switching works).
3. Brainstorm the final feature list.
4. Skeleton app + CI build of the APK.
5. Login + fetch (tested by you, logged in).
6. Storage, screens, graphs, profiles, app lock.
7. Backup, polish, install on family phones.

## Risks
- Meuhedet changes its site → re-run recorder, update fetch code.
- Meuhedet terms of use may not allow automated access. Low risk for personal use.
- The login may block WebViews or use extra checks. We'll know after step 1.
