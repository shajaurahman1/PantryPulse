// Single-page admin dashboard served at /admin. The admin key stays in the browser tab
// (sessionStorage) and is sent as the x-admin-key header.
export const adminPage = `<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>PantryPulse Admin</title>
<style>
  :root { --paper:#F2EFE9; --ink:#17140F; --pulse:#FF5A2C; --muted:#7A7268; --line:#E4DFD6; --card:#fff; }
  * { box-sizing: border-box; }
  body { margin:0; background:var(--paper); color:var(--ink); font:15px/1.45 system-ui, -apple-system, Segoe UI, sans-serif; }
  main { max-width: 1100px; margin: 0 auto; padding: 24px 16px 64px; }
  h1 { font-size: 30px; margin: 8px 0 4px; letter-spacing: -0.5px; }
  h2 { font-size: 18px; margin: 28px 0 10px; }
  .muted { color: var(--muted); }
  .card { background: var(--card); border: 1px solid var(--line); border-radius: 22px; padding: 18px; }
  .stats { display: grid; grid-template-columns: repeat(auto-fit, minmax(140px, 1fr)); gap: 12px; }
  .stat b { display:block; font-size: 30px; }
  table { width: 100%; border-collapse: collapse; font-size: 14px; }
  th, td { text-align: left; padding: 8px 6px; border-bottom: 1px solid var(--line); vertical-align: top; }
  th { font: 12px ui-monospace, monospace; letter-spacing: 1px; text-transform: uppercase; color: var(--muted); }
  .wrap { overflow-x: auto; }
  input { font: inherit; padding: 12px 14px; border-radius: 14px; border: 1px solid var(--line); width: min(360px, 100%); }
  button { font: inherit; font-weight: 700; border: 0; border-radius: 999px; padding: 12px 20px; background: var(--ink); color: var(--paper); cursor: pointer; }
  button.small { padding: 6px 12px; font-size: 13px; background: var(--pulse); color: var(--ink); }
  .pill { display:inline-block; padding: 2px 10px; border-radius: 999px; font: 12px ui-monospace, monospace; background: var(--paper); }
  .pill.available { background: #FFD6C7; } .pill.reserved { background: var(--pulse); }
  .pill.picked_up { background: #D9EEE3; } .pill.expired, .pill.cancelled { background: var(--line); }
  #error { color: #C8321E; margin-top: 8px; }
</style>
</head>
<body>
<main>
  <div class="muted">PANTRYPULSE</div>
  <h1>Community admin</h1>
  <p class="muted">Users, shops and giveaways stored in the Turso database. Removing a listing cancels it for everyone.</p>
  <form id="login" class="card">
    <label for="key"><b>Admin key</b></label><br><br>
    <input id="key" type="password" autocomplete="current-password" placeholder="Value of ADMIN_KEY in Vercel">
    <button type="submit">Open dashboard</button>
    <div id="error" role="alert"></div>
  </form>
  <section id="dash" hidden>
    <div class="stats" id="stats"></div>
    <h2>Latest giveaways</h2>
    <div class="card wrap"><table id="listings"></table></div>
    <h2>Newest accounts</h2>
    <div class="card wrap"><table id="users"></table></div>
  </section>
</main>
<script>
  const $ = (id) => document.getElementById(id);
  const esc = (s) => String(s ?? '').replace(/[&<>"']/g, (c) => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
  const when = (ms) => new Date(ms).toLocaleString();
  let key = '';
  try { key = sessionStorage.getItem('pp_admin_key') || ''; } catch {}
  async function api(path, opts = {}) {
    const r = await fetch(path, { ...opts, headers: { 'x-admin-key': key, ...(opts.headers || {}) } });
    const body = await r.json();
    if (!r.ok) throw new Error(body.message || r.statusText);
    return body;
  }
  async function load() {
    const d = await api('/admin/api/overview');
    $('login').hidden = true; $('dash').hidden = false;
    const s = d.listingsByStatus;
    const tiles = [['Accounts', d.users.total], ['Shops', d.users.shops], ['Available', s.available || 0],
      ['Reserved', s.reserved || 0], ['Picked up', s.picked_up || 0], ['Expired', s.expired || 0]];
    $('stats').innerHTML = tiles.map(([k, v]) => '<div class="card stat"><span class="muted">' + k + '</span><b>' + v + '</b></div>').join('');
    $('listings').innerHTML = '<tr><th>Item</th><th>Status</th><th>Mode</th><th>Area</th><th>Donor</th><th>Claimed by</th><th>Deadline</th><th></th></tr>' +
      d.listings.map((l) => '<tr><td>' + esc(l.title) + '</td><td><span class="pill ' + l.status + '">' + l.status + '</span></td><td>' + l.claim_mode +
        '</td><td>' + esc(l.area_label) + '</td><td>' + esc(l.donor_email) + (l.donor_type === 'shop' ? ' (shop)' : '') + '</td><td>' + esc(l.claimer_email || '') +
        '</td><td>' + when(l.pickup_deadline) + '</td><td>' + (['available','reserved'].includes(l.status) ? '<button class="small" data-id="' + l.id + '">Remove</button>' : '') + '</td></tr>').join('');
    $('users').innerHTML = '<tr><th>Name</th><th>Email</th><th>Type</th><th>Joined</th></tr>' +
      d.recentUsers.map((u) => '<tr><td>' + esc(u.name) + '</td><td>' + esc(u.email) + '</td><td>' + (u.account_type === 'shop' ? 'Shop · ' + esc(u.shop_name) : 'Person') + '</td><td>' + when(u.created_at) + '</td></tr>').join('');
  }
  $('listings').addEventListener('click', async (e) => {
    const id = e.target.dataset && e.target.dataset.id;
    if (!id || !confirm('Remove this giveaway for everyone?')) return;
    try { await api('/admin/api/listings/' + id + '/remove', { method: 'POST' }); load(); } catch (err) { alert(err.message); }
  });
  $('login').addEventListener('submit', async (e) => {
    e.preventDefault(); key = $('key').value.trim(); $('error').textContent = '';
    try { await load(); try { sessionStorage.setItem('pp_admin_key', key); } catch {} } catch (err) { $('error').textContent = err.message; }
  });
  if (key) load().catch(() => {});
</script>
</body>
</html>`;
