# Meuhedet site recorder

Records **which data requests** the Meuhedet site makes and the **shape** of each answer, so the app can be built. It does not keep personal data:

- Every value is masked: `ישראל ישראלי` → `אאאאא אאאאאא`, `012345678` → `999999999`, `98.5` → `#99.9`.
- It keeps only header **names**, never values (no tokens or cookies).
- Everything stays in your browser until you export it.

## Steps (Chrome on a computer)

1. Open https://www.meuhedet.co.il and log in to the personal area.
2. Press `F12` to open DevTools, then open the **Console** tab.
3. Paste the whole content of [`recorder.js`](recorder.js) and press Enter. You should see a green line `[MR] recording on ...`.
   - If Chrome blocks pasting, type `allow pasting` first and press Enter.
4. Before each section, label it, then open it in the site and wait for it to load. For example:
   ```js
   MR.note("lab results")
   ```
   Sections to visit (whatever exists):
   - lab results, plus opening **one test's history/graph**
   - medications, current and past
   - documents/letters, plus **opening one PDF**
   - visits/summaries, referrals, approvals (התחייבויות), vaccines, imaging, diagnoses/sensitivities
   - appointments
   - **family:** switch to each family member via the family link, labelled first (`MR.note("switch to wife")`, `MR.note("switch to child")`), then open their lab results, medications and documents (`MR.note("child lab results")` etc.)
5. **If the page fully reloads** (the console clears), paste `recorder.js` again. The log continues.
6. If the site moves to a different address (for example `my.meuhedet.co.il`), run the recorder there too, and run `MR.export()` on each address.
7. Run `MR.export()`. A file `meuhedet-map-<site>.json` downloads.
8. **Open the file and check it.** You should see only masks like `999` / `אאאא`, field names and addresses. If you find anything real, tell me and don't send it.
9. Send me the file(s). Then run `MR.clear()`.

Also note down, in words: how login works (ID + SMS? password?) and the address shown after login.
