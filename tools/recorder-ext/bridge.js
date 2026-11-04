// Isolated world: forwards masked messages from hook.js to the background worker.
addEventListener('message', (e) => {
  if (e.source !== window || !e.data || e.data.__mr !== 1) return;
  try { chrome.runtime.sendMessage({ kind: 'page', data: e.data.data }); } catch {}
});
