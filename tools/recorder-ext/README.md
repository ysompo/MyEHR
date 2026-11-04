# MyEHR recorder: Chrome extension (v2)

Records the **structure** of Meuhedet site requests automatically on every page of `*.meuhedet.co.il`. You don't paste anything into the console.

What it records:
- **Page requests (fetch/XHR):** the address, method, header names and the shape of each request and response. Values are masked the same way as `tools/recorder` (`9`, `a`, `א`, `<bool>`, `#…`).
- **Browser-level requests:** page loads, form posts, redirects and status codes. Requests are saved the moment they start, so a page reload can't drop them.
- **Forms:** field names and types only, never values.
- **On `login.meuhedet.co.il`:** every value is `<redacted>`. Fields that look secret (password, code, phone, ID, email, token) are `<redacted>` on every host.
- **Header values:** only `accept`, `content-type`, `x-requested-with`, `locale`, `x-platform` and `x-node-id`.
- **Cookies:** name, flags and the masked shape (`aaaa-9999`) only, never the value. Cookie headers are never recorded.
- **Ignored:** analytics, monitoring, fonts and images.

Everything stays inside the extension until you press **Export**.

## Install (once)
1. Download this folder (`tools/recorder-ext`) to your computer.
2. Open `chrome://extensions` and turn on **Developer mode** (top right).
3. Click **Load unpacked** and choose the folder.
4. Pin "MyEHR recorder" from the puzzle-piece icon.

## Record
Click the extension icon, type a label, press **Add label**, then do that step on the site:

1. `login` → log in yourself (ID, phone, SMS code).
2. `switch to family member 1` → switch through the account menu. Wait for the page to load.
3. `switch back` → switch back to yourself.
4. `lab pdf` → open lab results, then open **one PDF/file** of a result.
5. `prescription file` → medications → prescriptions → open one prescription file.
6. `medication approval file` → medications → approvals → open one file (if there is one).
7. `ecg institute file` → ECG / institute results → open one file.
8. `diagnoses sensitivities` → the page that shows diagnoses or sensitivities (if it exists).
9. `session expired` → leave the site idle until it signs you out (or ~20 min), then click any section.
10. Click **Export JSON**. **Open the file and check it**, then send it to the MyEHR session.
11. Click **Clear recording**, then remove the extension in `chrome://extensions`.
