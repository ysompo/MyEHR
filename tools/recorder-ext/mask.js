// Shared masking for the MyEHR recorder (page hook + background).
// Same rules as tools/recorder/recorder.js, plus redaction for login pages and secret-looking fields.
(() => {
  const MAX_STR = 80;
  const MAX_ARRAY_SAMPLES = 10;
  const MAX_DEPTH = 12;

  // Header values kept as-is (technical, not personal). Every other header: name only.
  const HEADER_VALUE_ALLOW = ['accept', 'content-type', 'x-requested-with', 'locale', 'x-platform', 'x-node-id'];

  // Field names whose values are never kept, not even masked.
  const SECRET_KEY = /pass|pwd|otp|sms|token|secret|verif|pin|captcha|^id$|idnum|identity|tz|mobile|phone|cellular|email|code$/i;

  const isLoginHost = (host) => /^login\./i.test(host || '');

  const maskStr = (s) => {
    const m = String(s)
      .replace(/[0-9]/g, '9')
      .replace(/[a-z]/g, 'a')
      .replace(/[A-Z]/g, 'A')
      .replace(/[א-ת]/g, 'א')
      .replace(/[֑-ׇ]/g, '');
    return m.length > MAX_STR ? m.slice(0, MAX_STR) + `…(len ${m.length})` : m;
  };

  const maskKey = (k) => (/\d{3,}/.test(k) || /[א-ת]/.test(k)) ? maskStr(k) : k;

  // redact=true: every scalar becomes "<redacted>" (used for login hosts).
  const shape = (v, redact = false, depth = 0, key = '') => {
    if (depth > MAX_DEPTH) return '<deep>';
    if (v === null) return null;
    if (v === undefined) return '<undefined>';
    const secret = redact || SECRET_KEY.test(key);
    switch (typeof v) {
      case 'string': return secret ? '<redacted>' : maskStr(v);
      case 'number': return secret ? '<redacted>' : '#' + maskStr(String(v));
      case 'boolean': return '<bool>';
      case 'object': {
        if (Array.isArray(v)) {
          const items = v.slice(0, MAX_ARRAY_SAMPLES).map((x) => shape(x, secret, depth + 1, key));
          return { __array_len: v.length, __item: items.reduce(merge, undefined) };
        }
        const out = {};
        for (const k of Object.keys(v)) out[maskKey(k)] = shape(v[k], secret, depth + 1, k);
        return out;
      }
      default: return `<${typeof v}>`;
    }
  };

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

  const maskUrl = (u, base) => {
    try {
      const url = new URL(u, base || 'https://invalid.local/');
      const redact = isLoginHost(url.hostname);
      const path = url.pathname.split('/').map((seg) => {
        let d = seg; try { d = decodeURIComponent(seg); } catch {}
        return (/\d{4,}/.test(d) || /[א-ת]/.test(d) || d.length > 40) ? maskStr(d) : seg;
      }).join('/');
      const q = [...url.searchParams.keys()].map((k) => {
        const v = url.searchParams.get(k);
        return `${maskKey(k)}=${(redact || SECRET_KEY.test(k)) ? '<redacted>' : maskStr(v)}`;
      });
      return url.origin + path + (q.length ? '?' + q.join('&') : '');
    } catch { return '<bad url>'; }
  };

  const formObj = (entries, redact) => {
    const o = {};
    for (const [k, v] of entries) {
      o[maskKey(k)] = (redact || SECRET_KEY.test(k)) ? '<redacted>' : (typeof v === 'string' ? maskStr(v) : '<file>');
    }
    return o;
  };

  const shapeText = (text, redact) => {
    try { return { json: shape(JSON.parse(text), redact) }; } catch {}
    if (/^[^=&\s]+=[^&]*(&[^=&\s]+=[^&]*)*$/.test(text)) return { form: formObj(new URLSearchParams(text), redact) };
    return { text_len: text.length };
  };

  const shapeBody = (body, redact = false) => {
    if (body == null) return null;
    if (typeof body === 'string') return shapeText(body, redact);
    if (typeof URLSearchParams !== 'undefined' && body instanceof URLSearchParams) return { form: formObj(body, redact) };
    if (typeof FormData !== 'undefined' && body instanceof FormData) return { multipart: formObj(body, redact) };
    return { type: Object.prototype.toString.call(body) };
  };

  // headers: array of [name, value]
  const shapeHeaders = (pairs) => pairs.map(([n, v]) => {
    const name = String(n);
    if (/^cookie$/i.test(name)) return [name, '<not recorded>'];
    return HEADER_VALUE_ALLOW.includes(name.toLowerCase()) ? [name, String(v)] : [name, null];
  });

  // Requests that are noise (analytics, monitoring, static files).
  const IGNORE = /\/glassbox\/|\/rb_[0-9a-f-]+|\.(woff2?|ttf|otf|png|jpe?g|gif|svg|ico|css|js|map)(\?|$)|configurationapi|google|facebook|taboola|outbrain|tiktok|algolia|nagich/i;

  globalThis.MRMask = { maskStr, maskKey, shape, merge, maskUrl, shapeBody, shapeText, shapeHeaders, formObj, isLoginHost, IGNORE, SECRET_KEY };
})();
