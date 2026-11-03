/*
 * MyEHR site recorder (structure only, no personal data).
 *
 * Paste into Chrome DevTools console (or save as a DevTools Snippet) while
 * logged in to the Meuhedet site. It records which data requests the site
 * makes and the SHAPE of each answer. Values are masked:
 *   digits -> 9, Latin letters -> a/A, Hebrew letters -> א, booleans -> <bool>.
 * Header VALUES, cookie values and storage values are never recorded.
 *
 * Commands:
 *   MR.note("lab results")  - label what you are about to click
 *   MR.status()             - how many requests were recorded
 *   MR.export()             - download the recording (JSON) for review
 *   MR.clear()              - delete the recording from this browser
 *
 * After a full page reload, paste/run it again; the log continues.
 */
(() => {
  if (window.MR && window.MR.__installed) { console.log('[MR] already running'); return; }

  const KEY = '__mr_log_v1';
  const MAX_ENTRIES = 3000;
  const MAX_STR = 80;
  const MAX_ARRAY_SAMPLES = 10;
  const MAX_DEPTH = 12;

  // ---------- masking ----------
  const maskStr = (s) => {
    const m = String(s)
      .replace(/[0-9]/g, '9')
      .replace(/[a-z]/g, 'a')
      .replace(/[A-Z]/g, 'A')
      .replace(/[א-ת]/g, 'א')
      .replace(/[֑-ׇ]/g, ''); // Hebrew diacritics
    return m.length > MAX_STR ? m.slice(0, MAX_STR) + `…(len ${m.length})` : m;
  };

  const maskKey = (k) => (/\d{3,}/.test(k) || /[א-ת]/.test(k)) ? maskStr(k) : k;

  const shape = (v, depth = 0) => {
    if (depth > MAX_DEPTH) return '<deep>';
    if (v === null) return null;
    if (v === undefined) return '<undefined>';
    switch (typeof v) {
      case 'string': return maskStr(v);
      case 'number': return '#' + maskStr(String(v));
      case 'boolean': return '<bool>';
      case 'object': {
        if (Array.isArray(v)) {
          const items = v.slice(0, MAX_ARRAY_SAMPLES).map((x) => shape(x, depth + 1));
          return { __array_len: v.length, __item: items.reduce(merge, undefined) };
        }
        const out = {};
        for (const k of Object.keys(v)) out[maskKey(k)] = shape(v[k], depth + 1);
        return out;
      }
      default: return `<${typeof v}>`;
    }
  };

  // Merge two shapes so one array sample shows every key seen.
  const merge = (a, b) => {
    if (a === undefined) return b;
    if (b === undefined) return a;
    if (a && b && typeof a === 'object' && typeof b === 'object' && !Array.isArray(a) && !Array.isArray(b)) {
      if ('__array_len' in a && '__array_len' in b) {
        return { __array_len: Math.max(a.__array_len, b.__array_len), __item: merge(a.__item, b.__item) };
      }
      const out = { ...a };
      for (const k of Object.keys(b)) out[k] = k in out ? merge(out[k], b[k]) : b[k];
      return out;
    }
    return a === null ? b : a;
  };

  const maskUrl = (u) => {
    try {
      const url = new URL(u, location.href);
      const path = url.pathname.split('/').map((seg) => {
        const d = decodeURIComponent(seg);
        return (/\d{4,}/.test(d) || /[א-ת]/.test(d) || d.length > 40) ? maskStr(d) : seg;
      }).join('/');
      const q = [...url.searchParams.keys()].map((k) => `${maskKey(k)}=${maskStr(url.searchParams.get(k))}`);
      return url.origin + path + (q.length ? '?' + q.join('&') : '');
    } catch { return '<bad url>'; }
  };

  const shapeBody = (body) => {
    if (body == null) return null;
    if (typeof body === 'string') {
      try { return { json: shape(JSON.parse(body)) }; } catch {}
      if (/^[^=&\s]+=[^&]*(&[^=&\s]+=[^&]*)*$/.test(body)) {
        const p = new URLSearchParams(body); const o = {};
        for (const [k, v] of p) o[maskKey(k)] = maskStr(v);
        return { form: o };
      }
      return { text_len: body.length };
    }
    if (body instanceof URLSearchParams) { const o = {}; for (const [k, v] of body) o[maskKey(k)] = maskStr(v); return { form: o }; }
    if (body instanceof FormData) { const o = {}; for (const [k, v] of body) o[maskKey(k)] = typeof v === 'string' ? maskStr(v) : '<file>'; return { multipart: o }; }
    return { type: Object.prototype.toString.call(body) };
  };

  const shapeResponseText = (ctype, text) => {
    if (/json/i.test(ctype) || /^\s*[\[{]/.test(text)) {
      try { return { json: shape(JSON.parse(text)) }; } catch {}
    }
    if (/html/i.test(ctype)) return { html_len: text.length };
    return { text_len: text.length };
  };

  const headerNames = (h) => {
    if (!h) return [];
    if (h instanceof Headers) return [...h.keys()];
    if (Array.isArray(h)) return h.map((x) => x[0]);
    return Object.keys(h);
  };

  // ---------- storage ----------
  const load = () => { try { return JSON.parse(localStorage.getItem(KEY)) || []; } catch { return []; } };
  const save = (log) => { try { localStorage.setItem(KEY, JSON.stringify(log.slice(-MAX_ENTRIES))); } catch (e) { console.warn('[MR] save failed', e); } };
  const add = (entry) => { const log = load(); log.push({ t: new Date().toISOString(), page: maskUrl(location.href), ...entry }); save(log); };

  // ---------- fetch ----------
  const origFetch = window.fetch;
  window.fetch = async function (input, init = {}) {
    const method = (init.method || (input instanceof Request ? input.method : 'GET')).toUpperCase();
    const url = input instanceof Request ? input.url : String(input);
    const reqHeaders = [...new Set([...headerNames(init.headers), ...(input instanceof Request ? headerNames(input.headers) : [])])];
    const reqBody = shapeBody(init.body);
    const res = await origFetch.apply(this, arguments);
    try {
      const ctype = res.headers.get('content-type') || '';
      const entry = { via: 'fetch', method, url: maskUrl(url), status: res.status, ctype, reqHeaders, reqBody, credentials: init.credentials };
      if (/pdf|image|octet-stream|zip/i.test(ctype)) {
        add({ ...entry, res: { binary: true, size: res.headers.get('content-length') } });
      } else {
        res.clone().text().then((t) => add({ ...entry, res: shapeResponseText(ctype, t) })).catch(() => add(entry));
      }
    } catch (e) { console.warn('[MR] fetch record error', e); }
    return res;
  };

  // ---------- XHR ----------
  const XO = XMLHttpRequest.prototype.open, XS = XMLHttpRequest.prototype.send, XH = XMLHttpRequest.prototype.setRequestHeader;
  XMLHttpRequest.prototype.open = function (method, url) { this.__mr = { method: String(method).toUpperCase(), url: String(url), h: [] }; return XO.apply(this, arguments); };
  XMLHttpRequest.prototype.setRequestHeader = function (name) { if (this.__mr) this.__mr.h.push(name); return XH.apply(this, arguments); };
  XMLHttpRequest.prototype.send = function (body) {
    const m = this.__mr;
    if (m) {
      const reqBody = shapeBody(body);
      this.addEventListener('loadend', () => {
        try {
          const ctype = this.getResponseHeader('content-type') || '';
          const entry = { via: 'xhr', method: m.method, url: maskUrl(m.url), status: this.status, ctype, reqHeaders: m.h, reqBody, withCredentials: this.withCredentials };
          let res;
          if (this.responseType === '' || this.responseType === 'text') res = shapeResponseText(ctype, this.responseText || '');
          else if (this.responseType === 'json') res = { json: shape(this.response) };
          else res = { binary: true, responseType: this.responseType };
          add({ ...entry, res });
        } catch (e) { console.warn('[MR] xhr record error', e); }
      });
    }
    return XS.apply(this, arguments);
  };

  // ---------- navigation ----------
  const nav = (kind) => add({ nav: kind });
  for (const fn of ['pushState', 'replaceState']) {
    const o = history[fn];
    history[fn] = function () { const r = o.apply(this, arguments); nav(fn); return r; };
  }
  addEventListener('popstate', () => nav('popstate'));
  addEventListener('hashchange', () => nav('hashchange'));

  // ---------- snapshot of this page load ----------
  const names = (store) => { try { return Object.keys(store).filter((k) => k !== KEY); } catch { return []; } };
  add({
    snapshot: {
      cookieNames: document.cookie.split(';').map((c) => c.split('=')[0].trim()).filter(Boolean),
      localStorageKeys: names(localStorage),
      sessionStorageKeys: names(sessionStorage),
      earlierRequests: performance.getEntriesByType('resource')
        .filter((e) => e.initiatorType === 'fetch' || e.initiatorType === 'xmlhttprequest')
        .map((e) => maskUrl(e.name)),
      frames: [...document.querySelectorAll('iframe')].map((f) => maskUrl(f.src || 'about:blank')),
    },
  });

  window.MR = {
    __installed: true,
    note: (text) => { add({ note: String(text) }); console.log('[MR] note:', text); },
    status: () => { const l = load(); console.log(`[MR] ${l.filter((e) => e.url).length} requests, ${l.length} entries on ${location.host}`); },
    export: () => {
      const data = { tool: 'MyEHR recorder v1', host: location.host, exported: new Date().toISOString(), entries: load() };
      const a = document.createElement('a');
      a.href = URL.createObjectURL(new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' }));
      a.download = `meuhedet-map-${location.host}.json`;
      a.click();
      console.log('[MR] exported. Open the file and check it before sending.');
    },
    clear: () => { localStorage.removeItem(KEY); console.log('[MR] cleared'); },
  };
  console.log('%c[MR] recording on ' + location.host + '. Use MR.note("..."), MR.export()', 'color:green;font-weight:bold');
})();
