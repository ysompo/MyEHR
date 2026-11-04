const send = (m) => chrome.runtime.sendMessage(m);
const refresh = async () => { const r = await send({ kind: 'count' }); document.getElementById('count').textContent = `(${r.count} entries)`; };
document.getElementById('add').onclick = async () => {
  const el = document.getElementById('note');
  if (!el.value.trim()) return;
  await send({ kind: 'note', text: el.value.trim() }); el.value = ''; refresh();
};
document.getElementById('note').addEventListener('keydown', (e) => { if (e.key === 'Enter') document.getElementById('add').click(); });
document.getElementById('export').onclick = async () => {
  const data = await send({ kind: 'export' });
  const a = document.createElement('a');
  a.href = URL.createObjectURL(new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' }));
  a.download = `meuhedet-map-ext-${new Date().toISOString().slice(0, 16).replace(/[:T]/g, '-')}.json`;
  a.click();
};
document.getElementById('clear').onclick = async () => { if (confirm('Delete the recording?')) { await send({ kind: 'clear' }); refresh(); } };
refresh();
