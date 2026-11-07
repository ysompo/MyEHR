# MyEHR user guide

## 1. Install

### Android phone
1. On the phone, download [MyEHR.apk](https://github.com/ysompo/MyEHR/releases/download/android-latest/MyEHR.apk).
2. Open it. If asked, allow "install unknown apps" for your browser or Files app.
3. Tap **Install**.

### Windows
1. Download `MyEHR-Windows.msi` from the [desktop-latest release](https://github.com/ysompo/MyEHR/releases/tag/desktop-latest) and open it.
2. If you see "Windows protected your PC", click **More info → Run anyway**.
3. Open MyEHR from the Start menu.

### Mac
1. From the same release, download `MyEHR-Mac-AppleSilicon.dmg` (M1 or newer) or `MyEHR-Mac-Intel.dmg` (older Macs).
2. Open it and drag MyEHR into Applications.
3. The first time, right-click MyEHR → **Open** → **Open**.

## 2. Unlock
- **Phone:** fingerprint, face or phone PIN, every time the app opens.
- **Computer:** choose an app password on first run. You need it every time the app opens.
  There is no password reset. If you forget it, delete the app's data folder and sync again (see [Troubleshooting](#8-troubleshooting)).

## 3. Sync from Meuhedet
1. Tap **Sync** (סנכרון). On a computer, then click **פתיחת חלון ההתחברות** (open login window).
2. Meuhedet's own login page opens. Log in yourself (ID, phone, SMS code).
3. The app then downloads data for you **and every family member** linked to your account (spouse, children).
   - **Phone:** it runs in the background. You can lock the phone or leave the app. A notification shows progress.
   - **Computer:** the login window closes and the download runs in the app.
4. When it finishes, the app signs out of Meuhedet.

The first sync takes a while. Later syncs only fetch what is new.
If Meuhedet ends the session in the middle, log in again; the sync continues from where it stopped.

## 4. Tabs
| Tab | What it shows |
|---|---|
| **Home** (בית) | Family members, results out of range, latest results, active medications, and a question box. |
| **Labs** (בדיקות) | By date (full results of each test day) or by test (graph over the years with the normal range, and markers where a medication started). |
| **Question** (שאלה) | Free questions about one person's data. See [Questions](#5-questions-optional). |
| **Medications** (תרופות) | Active medications, purchases, approvals and prescriptions. |
| **Visits** (ביקורים) | Upcoming and past appointments. **הוספה ליומן** (add to calendar) opens Google Calendar with the details filled in. |
| **Documents** (מסמכים) | Visit summaries, vaccines, ECG and other tests. |

Tap a family member's picture at the top to switch person.

## 5. Questions (optional)
Ask in Hebrew or English, for example "how did my cholesterol change since I started X?".

- Needs your own Anthropic API key. Add it in **Settings**. It is stored encrypted on the device.
- Only what the question needs is sent: test names, values, units, normal ranges, dates, medication names with purchase dates, and age.
- **Never sent:** name, ID, phone, email, birth date, doctors, clinics, pharmacies or notes.
- The first time each kind of data is needed, the app shows what will be sent and asks for OK.
- Tap **"מה נשלח ל-Claude?"** under an answer to see exactly what was sent.
- Answers describe trends and timing. They are not medical advice.

## 5a. Add a document from a private doctor or hospital
1. **Documents** tab → **הוספת מסמך** (add document).
2. Take a photo of each page (phone) or choose a PDF or picture file.
3. Choose whose document it is, then **אישור ושליחה לקריאה** (OK and send for reading).
   The whole document goes to Claude to be read, including any name or ID on it. Needs your API key.
4. Tick what to add, then **שמירה** (save).

What happens:
- Lab values join the same graphs as Meuhedet's, marked with the document's source. If Meuhedet has the same test on the same day, Meuhedet's value is kept.
- Medications appear under **ממסמכים שהעליתם** (from your documents) in the Medications tab.
- Diagnoses and allergies appear on the Home screen.
- The document itself is kept encrypted on the device. Open it from the Documents tab, or delete it there (this also removes what was added from it).

## 5b. Diagnoses, allergies and notes
On the Home screen, under **אבחנות, רגישויות והערות**: tap **+ הוספה** to add one, or tap an item to edit or delete it.
The question tab can use them (it asks first). Your notes are treated as your own words, not as medical records.

## 6. Settings
- **Text size:** normal, large, extra large.
- **Colors:** Pine, Ocean, Olive or High contrast. Light/dark follows your device.
- **API key** for questions.
- **Updates:** **בדיקת עדכונים** (check for updates).

## 7. Updates
- The app checks once a day and shows a banner when a new version is available.
- **Phone:** tap **התקנת גרסה …** (install version), then confirm the install screen. Your data stays.
- **Computer:** **הורדה והתקנה** (download and install) opens the download page. Install the new version over the old one.

## 8. Troubleshooting
| Problem | What to do |
|---|---|
| "Log in again" during sync | Meuhedet ended the session. Phone: tap **התחברות מחדש** (log in again). Computer: open the login window again. The sync continues. |
| Sync failed | Check the internet connection and try again. |
| Phone: no progress notification | Allow notifications for MyEHR in the phone's settings (the sync works without them). |
| Phone: "WebView too old" | Update **Android System WebView** from the Play Store. |
| Phone: update won't install | Allow "install unknown apps" for MyEHR. |
| Computer: forgot the app password | Delete the data folder and MyEHR entries in the keychain, then sync again. Folder: Windows `%APPDATA%\MyEHR`, Mac `~/Library/Application Support/MyEHR`. |

## 9. Privacy in short
- Data is stored only on the device, encrypted. No backups to the cloud and no server.
- The app talks only to Meuhedet (to sync), GitHub (to check for updates) and, if you use questions, Anthropic.
- Each device syncs on its own. Nothing passes between devices.
