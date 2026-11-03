# MyEHR for Meuhedet: plan (draft)

## Goal
A personal medical file for me and my family, built from our own Meuhedet accounts:
- Lab results with a graph over the years and normal ranges
- Medications
- Documents
- More features to be decided in the brainstorm

Data stays **only on the device**. There is no server, no tracking and no ads.

## Users
Family members. Adults log in with **their own** Meuhedet account (ID + SMS, to confirm).
A parent also sees their **children** through the parent's own login, using the site's family switcher (to confirm in the recording). Each person gets a separate profile in the app.

## Platforms
| Platform | Form | Install |
|---|---|---|
| Android | Native app (React Native / Expo) | APK sideload, free |
| iPhone | Web app (PWA) + "fetch" bookmark | Safari → Add to Home Screen |

One codebase builds both: Expo for Android, React Native Web for the PWA.

## How data is fetched
- **Android:** the app opens the real Meuhedet login page in a WebView. The user logs in there, so the app never sees the password. The app then runs the *fetch module* inside that page, which calls the same endpoints the site uses, and saves the results to encrypted local storage.
- **iPhone:** a web app can't call Meuhedet directly, because the browser blocks requests to another site's data. Instead:
  1. The user logs in to meuhedet.co.il in Safari.
  2. They tap a saved bookmark (a bookmarklet) that runs the **same fetch module** on the Meuhedet page.
  3. The bookmarklet opens our PWA and passes the data to it (`postMessage`).
  4. The PWA stores it in IndexedDB on the phone.
- The fetch module is shared code, so there is one place to fix when Meuhedet changes.

## Steps
1. **Map the site.** Run [`tools/recorder`](../tools/recorder/README.md) while logged in and export the masked file. ← *we are here*
2. Write the endpoint map (`docs/SITE_MAP.md`) from the recording.
3. Brainstorm features (Superpowers brainstorming skill).
4. Build the fetch module and test it with the user while logged in.
5. Build the app UI: tests and graphs, medications, documents, family profiles.
6. Android build (APK). PWA hosting (static files only, e.g. GitHub Pages under the user's account).
7. Backup/export: an encrypted file the user keeps.

## Risks / open questions
- Meuhedet can change its site at any time. If that happens, re-run the recorder and update the fetch module.
- Meuhedet's terms of use may not allow automated access. The risk for personal use is low.
- Safari may block bookmarklets on sites with a strict security policy (CSP). Test this early. The fallback is a manual "save page data" step.
- iOS may delete web-app data if the app goes unused for a long time. Mitigate with Add to Home Screen plus backup export.
