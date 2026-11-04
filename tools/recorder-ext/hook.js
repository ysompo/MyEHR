// Runs in the page (MAIN world) at document_start, before the site's scripts.
// Sends masked request/response shapes to bridge.js via window.postMessage.
(() => {
  if (window.__mrHook) return;
  window.__mrHook = true;
  const M = globalThis.MRMask;
  const host = location.hostname;
  const redact = M.isLoginHost(host);
  let seq = 0;
  const nextId = () => `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 8)}-${seq++}`;
  const post = (data) => { try { window.postMessage({ __mr: 1, data: { ...data, page: M.maskUrl(location.href), frame: window === top ? 'top' : 'sub' } }, location.origin); } catch {} };

  const headerPairs = (h) => {
    if (!h) return [];
    if (h instanceof Headers) return [...h.entries()];
    if (Array.isArray(h)) return h;
    return Object.entries(h);
  };

  const shapeRes = (ctype, text) => {
    if (/html/i.test(ctype)) return { html_len: text.length };
    return M.shapeText(text, redact);
  };

  // ---- fetch ----
  const origFetch = window.fetch;
  window.fetch = function (input, init = {}) {
    const id = nextId();
    let method = 'GET', url = '', hdrs = [];
    try {
      method = (init.method || (input instanceof Request ? input.method : 'GET')).toUpperCase();
      url = input instanceof Request ? input.url : String(input);
      hdrs = [...headerPairs(input instanceof Request ? input.headers : null), ...headerPairs(init.headers)];
      if (!M.IGNORE.test(url)) {
        post({ ev: 'req', via: 'fetch', id, method, url: M.maskUrl(url, location.href), reqHeaders: M.shapeHeaders(hdrs), reqBody: M.shapeBody(init.body, redact), credentials: init.credentials });
      }
    } catch {}
    const p = origFetch.apply(this, arguments);
    if (!M.IGNORE.test(url)) {
      p.then((res) => {
        const ctype = res.headers.get('content-type') || '';
        if (/pdf|image|octet-stream|zip/i.test(ctype)) { post({ ev: 'res', id, status: res.status, ctype, res: { binary: true } }); return; }
        res.clone().text().then((t) => post({ ev: 'res', id, status: res.status, ctype, redirected: res.redirected, res: shapeRes(ctype, t) })).catch(() => {});
      }, (err) => post({ ev: 'res', id, error: String(err && err.name) }));
    }
    return p;
  };

  // ---- XHR ----
  const XP = XMLHttpRequest.prototype;
  const XO = XP.open, XS = XP.send, XH = XP.setRequestHeader;
  XP.open = function (method, url) { this.__mr = { id: nextId(), method: String(method).toUpperCase(), url: String(url), h: [] }; return XO.apply(this, arguments); };
  XP.setRequestHeader = function (n, v) { if (this.__mr) this.__mr.h.push([n, v]); return XH.apply(this, arguments); };
  XP.send = function (body) {
    const m = this.__mr;
    if (m && !M.IGNORE.test(m.url)) {
      post({ ev: 'req', via: 'xhr', id: m.id, method: m.method, url: M.maskUrl(m.url, location.href), reqHeaders: M.shapeHeaders(m.h), reqBody: M.shapeBody(body, redact) });
      this.addEventListener('loadend', () => {
        try {
          const ctype = this.getResponseHeader('content-type') || '';
          let res;
          if (this.responseType === '' || this.responseType === 'text') res = shapeRes(ctype, this.responseText || '');
          else if (this.responseType === 'json') res = { json: M.shape(this.response, redact) };
          else res = { binary: true, responseType: this.responseType };
          post({ ev: 'res', id: m.id, status: this.status, ctype, responseURL: M.maskUrl(this.responseURL || ''), res });
        } catch {}
      });
    }
    return XS.apply(this, arguments);
  };

  // ---- sendBeacon (some sites switch context this way) ----
  if (navigator.sendBeacon) {
    const SB = navigator.sendBeacon.bind(navigator);
    navigator.sendBeacon = (url, data) => {
      if (!M.IGNORE.test(String(url))) post({ ev: 'beacon', url: M.maskUrl(String(url), location.href), reqBody: M.shapeBody(typeof data === 'string' ? data : null, redact) });
      return SB(url, data);
    };
  }

  // ---- cookies written by page scripts: name + masked shape only ----
  try {
    const desc = Object.getOwnPropertyDescriptor(Document.prototype, 'cookie');
    Object.defineProperty(document, 'cookie', {
      configurable: true,
      get() { return desc.get.call(document); },
      set(v) {
        try { const [pair] = String(v).split(';'); const i = pair.indexOf('=');
          post({ ev: 'cookie-write', name: pair.slice(0, i).trim(), valueShape: M.maskStr(pair.slice(i + 1)).slice(0, 60) }); } catch {}
        desc.set.call(document, v);
      },
    });
  } catch {}

  // ---- forms: field names and types only, never values ----
  const describeForm = (form, how) => {
    try {
      const fields = [...form.elements].filter((el) => el.name).map((el) => ({ name: M.maskKey(el.name), type: (el.type || el.tagName || '').toLowerCase() }));
      post({ ev: 'form', how, method: (form.method || 'get').toUpperCase(), action: M.maskUrl(form.action || location.href, location.href), fields });
    } catch {}
  };
  addEventListener('submit', (e) => describeForm(e.target, 'submit-event'), true);
  const FS = HTMLFormElement.prototype.submit;
  HTMLFormElement.prototype.submit = function () { describeForm(this, 'form.submit()'); return FS.apply(this, arguments); };

  // ---- navigation ----
  for (const fn of ['pushState', 'replaceState']) {
    const o = history[fn];
    history[fn] = function () { const r = o.apply(this, arguments); post({ ev: 'nav', kind: fn }); return r; };
  }
  addEventListener('popstate', () => post({ ev: 'nav', kind: 'popstate' }));
  addEventListener('hashchange', () => post({ ev: 'nav', kind: 'hashchange' }));
  addEventListener('beforeunload', () => post({ ev: 'unload' }));

  // ---- page load + storage key names ----
  const names = (s) => { try { return Object.keys(s); } catch { return []; } };
  const snap = () => {
    let navType = null;
    try { navType = performance.getEntriesByType('navigation')[0]?.type || null; } catch {}
    post({ ev: 'load', navType, title_len: document.title.length, localStorageKeys: names(localStorage), sessionStorageKeys: names(sessionStorage),
      frames: [...document.querySelectorAll('iframe')].map((f) => M.maskUrl(f.src || 'about:blank', location.href)) });
  };
  if (document.readyState === 'loading') addEventListener('DOMContentLoaded', snap, { once: true }); else snap();

  // Console helper (top frame): MR.note("...")
  window.MR = { note: (text) => { post({ ev: 'note', text: String(text) }); console.log('[MR] note:', text); } };
})();
