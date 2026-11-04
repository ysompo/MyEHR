// Stores every entry under its own key so nothing is lost if the worker restarts.
importScripts('mask.js');
const M = globalThis.MRMask;
const FILTER = { urls: ['*://*.meuhedet.co.il/*'], types: ['main_frame', 'sub_frame', 'xmlhttprequest', 'other'] };

let seqReady = chrome.storage.local.get('seq').then((r) => r.seq || 0);
let writeChain = Promise.resolve();

function add(entry) {
  writeChain = writeChain.then(async () => {
    const seq = (await seqReady) + 1;
    seqReady = Promise.resolve(seq);
    await chrome.storage.local.set({ seq, ['e' + String(seq).padStart(7, '0')]: { t: new Date().toISOString(), ...entry } });
  }).catch((e) => console.warn('[MR] store failed', e));
  return writeChain;
}

const skip = (d) => M.IGNORE.test(d.url) || d.tabId < 0 && d.type !== 'main_frame';
const redactFor = (url) => { try { return M.isLoginHost(new URL(url).hostname); } catch { return true; } };

function rawBody(details) {
  const rb = details.requestBody;
  if (!rb) return null;
  const redact = redactFor(details.url);
  if (rb.formData) return { form: M.formObj(Object.entries(rb.formData).map(([k, v]) => [k, v[0]]), redact) };
  if (rb.raw && rb.raw[0] && rb.raw[0].bytes) {
    try { return M.shapeText(new TextDecoder().decode(rb.raw[0].bytes), redact); } catch { return { raw: true }; }
  }
  return null;
}

chrome.webRequest.onBeforeRequest.addListener((d) => {
  if (skip(d)) return;
  add({ src: 'net', ev: 'start', rid: d.requestId, type: d.type, method: d.method, url: M.maskUrl(d.url), frame: d.frameId === 0 ? 'top' : 'sub', reqBody: rawBody(d) });
}, FILTER, ['requestBody']);

chrome.webRequest.onSendHeaders.addListener((d) => {
  if (skip(d)) return;
  add({ src: 'net', ev: 'headers', rid: d.requestId, reqHeaders: M.shapeHeaders((d.requestHeaders || []).map((h) => [h.name, h.value])) });
}, FILTER, ['requestHeaders']);

chrome.webRequest.onBeforeRedirect.addListener((d) => {
  if (skip(d)) return;
  add({ src: 'net', ev: 'redirect', rid: d.requestId, status: d.statusCode, to: M.maskUrl(d.redirectUrl) });
}, FILTER);

chrome.webRequest.onCompleted.addListener((d) => {
  if (skip(d)) return;
  const h = d.responseHeaders || [];
  const get = (n) => (h.find((x) => x.name.toLowerCase() === n) || {}).value || '';
  add({ src: 'net', ev: 'done', rid: d.requestId, type: d.type, method: d.method, url: M.maskUrl(d.url), status: d.statusCode,
    ctype: get('content-type').split(';')[0], resHeaderNames: h.map((x) => x.name) });
}, FILTER, ['responseHeaders']);

chrome.webRequest.onErrorOccurred.addListener((d) => {
  if (skip(d)) return;
  add({ src: 'net', ev: 'error', rid: d.requestId, url: M.maskUrl(d.url), error: d.error });
}, FILTER);

chrome.runtime.onMessage.addListener((msg, sender, reply) => {
  if (msg.kind === 'page') { add({ src: 'page', tab: sender.tab && sender.tab.id, ...msg.data }); return; }
  if (msg.kind === 'note') { add({ src: 'popup', ev: 'note', text: String(msg.text) }).then(() => reply({ ok: true })); return true; }
  if (msg.kind === 'count') { chrome.storage.local.get('seq').then((r) => reply({ count: r.seq || 0 })); return true; }
  if (msg.kind === 'export') {
    writeChain.then(() => chrome.storage.local.get(null)).then((all) => {
      const entries = Object.keys(all).filter((k) => k[0] === 'e').sort().map((k) => all[k]);
      reply({ tool: 'MyEHR recorder ext v2', exported: new Date().toISOString(), entries });
    });
    return true;
  }
  if (msg.kind === 'clear') { writeChain.then(() => chrome.storage.local.clear()).then(() => { seqReady = Promise.resolve(0); reply({ ok: true }); }); return true; }
});
