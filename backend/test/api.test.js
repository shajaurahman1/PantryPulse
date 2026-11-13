import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import { createServer } from 'node:http';
import { mkdtempSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

const dir = mkdtempSync(join(tmpdir(), 'pp-api-'));
process.env.TURSO_DATABASE_URL = `file:${join(dir, 'test.db')}`;
process.env.ADMIN_KEY = 'test-admin-key';

const { default: handler, setClockForTests, publicDistance } = await import('../src/app.js');

let base;
let server;
let now = Date.UTC(2026, 10, 13, 9, 0, 0);
setClockForTests(() => now);
const HOUR = 3600 * 1000;

before(async () => {
  server = createServer((req, res) => handler(req, res));
  await new Promise((r) => server.listen(0, r));
  base = `http://127.0.0.1:${server.address().port}`;
});
after(() => { server.close(); rmSync(dir, { recursive: true, force: true }); });

async function call(method, path, { token, body, headers } = {}) {
  const res = await fetch(base + path, {
    method,
    headers: { 'content-type': 'application/json', ...(token ? { authorization: `Bearer ${token}` } : {}), ...headers },
    body: body ? JSON.stringify(body) : undefined,
  });
  return { status: res.status, body: await res.json() };
}

let n = 0;
async function newUser(extra = {}) {
  n += 1;
  const r = await call('POST', '/auth/register', { body: { email: `user${n}@example.com`, password: 'secret12', name: `User${n} Test`, ...extra } });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  return r.body;
}

// A pickup point in Bengaluru and viewers at known distances.
const HOME = { lat: 12.9716, lon: 77.5946 };
const near = (km) => ({ lat: HOME.lat + km / 111.32, lon: HOME.lon });

async function newListing(token, extra = {}) {
  const r = await call('POST', '/listings', {
    token,
    body: {
      title: 'Greek yogurt', category: 'DAIRY', quantityText: '2 tubs', notes: 'Unopened',
      itemExpiry: '2026-11-15', pickupDeadline: now + 24 * HOUR, ...HOME,
      areaLabel: 'Near MG Road metro', pickupAddress: '12 Residency Road, Flat 4B', pickupInstructions: 'Ring the bell',
      claimMode: 'direct', ...extra,
    },
  });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  return r.body.listing;
}

test('accounts: register, login, connect and wrong password', async () => {
  const u = await newUser();
  assert.ok(u.token);
  const login = await call('POST', '/auth/login', { body: { email: u.user.email.toUpperCase(), password: 'secret12' } });
  assert.equal(login.status, 200);
  const wrong = await call('POST', '/auth/login', { body: { email: u.user.email, password: 'nope123' } });
  assert.equal(wrong.status, 401);
  const dup = await call('POST', '/auth/register', { body: { email: u.user.email, password: 'secret12', name: 'X' } });
  assert.equal(dup.status, 409);
  const connectNew = await call('POST', '/auth/connect', { body: { email: 'fresh@example.com', password: 'secret12', name: 'Fresh' } });
  assert.equal(connectNew.body.created, true);
  const connectOld = await call('POST', '/auth/connect', { body: { email: 'fresh@example.com', password: 'secret12', name: 'Fresh' } });
  assert.equal(connectOld.body.created, false);
  const me = await call('GET', '/me', { token: u.token });
  assert.equal(me.body.user.accountType, 'person');
  assert.equal((await call('GET', '/me')).status, 401);
});

test('shop accounts need a shop name and can post a batch atomically', async () => {
  const noName = await call('POST', '/auth/register', { body: { email: 'shop0@example.com', password: 'secret12', name: 'Owner', accountType: 'shop' } });
  assert.equal(noName.status, 400);
  const shop = await newUser({ accountType: 'shop', shopName: 'Fresh Mart', shopAddress: '5 Market St', openingHours: '8am–9pm', ...HOME });
  const batch = await call('POST', '/listings/batch', {
    token: shop.token,
    body: { shared: { pickupDeadline: now + 6 * HOUR, claimMode: 'direct', areaLabel: 'Fresh Mart' }, items: [{ title: 'Bread loaf' }, { title: 'Bananas', quantityText: '1 kg' }] },
  });
  assert.equal(batch.status, 200);
  assert.equal(batch.body.listings.length, 2);
  assert.equal(batch.body.listings[0].pickup.address, '5 Market St', 'shop address is the default pickup address');
  const badBatch = await call('POST', '/listings/batch', {
    token: shop.token, body: { shared: { pickupDeadline: now + HOUR }, items: [{ title: 'Ok' }, { title: '' }] },
  });
  assert.equal(badBatch.status, 400);
  const viewer = await newUser();
  const feed = await call('GET', `/listings/nearby?lat=${HOME.lat}&lon=${HOME.lon}&radiusKm=2`, { token: viewer.token });
  const fromShop = feed.body.listings.filter((l) => l.donor.isShop);
  assert.ok(fromShop.some((l) => l.donor.name === 'Fresh Mart' && l.donor.openingHours === '8am–9pm'));
  assert.ok(!fromShop.some((l) => l.title === 'Ok'), 'failed batch inserted nothing');
});

test('nearby feed: radius filter, sorting, privacy and own listings hidden', async () => {
  const donor = await newUser();
  const viewer = await newUser();
  const close = await newListing(donor.token, { title: 'Close milk', ...near(1.3), pickupDeadline: now + 20 * HOUR });
  const urgent = await newListing(donor.token, { title: 'Urgent bread', ...near(4), pickupDeadline: now + 2 * HOUR });
  await newListing(donor.token, { title: 'Far rice', ...near(9) });
  const at = `lat=${HOME.lat}&lon=${HOME.lon}`;

  const two = await call('GET', `/listings/nearby?${at}&radiusKm=2`, { token: viewer.token });
  const titles2 = two.body.listings.map((l) => l.title);
  assert.ok(titles2.includes('Close milk'));
  assert.ok(!titles2.includes('Urgent bread') && !titles2.includes('Far rice'));

  const five = await call('GET', `/listings/nearby?${at}&radiusKm=5&sort=distance`, { token: viewer.token });
  const mine = five.body.listings.filter((l) => ['Close milk', 'Urgent bread'].includes(l.title)).map((l) => l.title);
  assert.deepEqual(mine, ['Close milk', 'Urgent bread']);
  const byUrgency = await call('GET', `/listings/nearby?${at}&radiusKm=5&sort=urgency`, { token: viewer.token });
  const order = byUrgency.body.listings.filter((l) => ['Close milk', 'Urgent bread'].includes(l.title)).map((l) => l.title);
  assert.deepEqual(order, ['Urgent bread', 'Close milk']);

  const item = five.body.listings.find((l) => l.id === close.id);
  assert.equal(item.pickup, undefined, 'no exact address or coordinates in the public feed');
  assert.equal(JSON.stringify(item).includes('Residency Road'), false);
  assert.equal(item.areaLabel, 'Near MG Road metro');
  assert.equal(item.distanceKm, 1.5);
  assert.equal(item.donor.name, donor.user.name.split(' ')[0]);

  const own = await call('GET', `/listings/nearby?${at}&radiusKm=10`, { token: donor.token });
  assert.ok(!own.body.listings.some((l) => l.id === urgent.id), 'donors do not see their own listings in the feed');
  assert.equal(publicDistance(0.3).distanceLabel, 'under 1 km');
});

test('direct claim: 30 people tapping Claim at the same instant -> exactly one wins', async () => {
  const donor = await newUser();
  const listing = await newListing(donor.token, { title: 'Contested paneer' });
  const people = await Promise.all(Array.from({ length: 30 }, () => newUser()));
  const results = await Promise.all(people.map((p) => call('POST', `/listings/${listing.id}/claim`, { token: p.token })));
  const winners = results.filter((r) => r.status === 200);
  const losers = results.filter((r) => r.status !== 200);
  assert.equal(winners.length, 1, 'exactly one claim succeeds');
  assert.ok(losers.every((r) => r.status === 409 && r.body.error === 'already_claimed'));

  const winner = people[results.indexOf(winners[0])];
  const won = winners[0].body.listing;
  assert.equal(won.status, 'reserved');
  assert.equal(won.pickup.address, '12 Residency Road, Flat 4B', 'claimer now sees the exact address');
  assert.match(won.pickup.code, /^\d{4}$/);

  const feed = await call('GET', `/listings/nearby?lat=${HOME.lat}&lon=${HOME.lon}&radiusKm=5`, { token: people[5].token });
  assert.ok(!feed.body.listings.some((l) => l.id === listing.id), 'reserved items leave the public feed');
  const outsider = await call('GET', `/listings/${listing.id}`, { token: people.find((p) => p !== winner).token });
  assert.equal(outsider.status, 404);

  const donorView = await call('GET', `/listings/${listing.id}`, { token: donor.token });
  assert.equal(donorView.body.listing.claimedBy.name, winner.user.name.split(' ')[0]);
  const ev = await call('GET', '/events', { token: donor.token });
  assert.ok(ev.body.events.some((e) => e.type === 'claimed' && e.listingId === listing.id));
});

test('approval mode: requests queue, simultaneous accepts -> one winner, others auto-closed', async () => {
  const donor = await newUser();
  const listing = await newListing(donor.token, { title: 'Homemade curry', claimMode: 'approval' });
  const [a, b, c] = await Promise.all([newUser(), newUser(), newUser()]);
  const direct = await call('POST', `/listings/${listing.id}/claim`, { token: a.token });
  assert.equal(direct.body.error, 'approval_required');
  for (const u of [a, b, c]) {
    const r = await call('POST', `/listings/${listing.id}/requests`, { token: u.token, body: { message: 'Can collect at 6' } });
    assert.equal(r.status, 200);
    assert.equal(r.body.listing.myRequestStatus, 'pending');
  }
  const donorView = (await call('GET', `/listings/${listing.id}`, { token: donor.token })).body.listing;
  assert.equal(donorView.requests.length, 3);
  const [ra, rb] = donorView.requests;
  // The donor taps Accept on two people at the same time (two devices / double tap).
  const accepts = await Promise.all([
    call('POST', `/listings/${listing.id}/requests/${ra.id}/accept`, { token: donor.token }),
    call('POST', `/listings/${listing.id}/requests/${rb.id}/accept`, { token: donor.token }),
  ]);
  assert.equal(accepts.filter((r) => r.status === 200).length, 1);
  assert.equal(accepts.filter((r) => r.status === 409).length, 1);
  const after = (await call('GET', `/listings/${listing.id}`, { token: donor.token })).body.listing;
  assert.equal(after.status, 'reserved');
  assert.equal(after.requests.filter((r) => r.status === 'accepted').length, 1);
  assert.equal(after.requests.filter((r) => r.status === 'rejected').length, 2);

  const winnerIdx = accepts[0].status === 200 ? 0 : 1;
  const winner = [a, b][winnerIdx];
  const loser = [a, b][1 - winnerIdx];
  const claims = await call('GET', '/claims/mine', { token: winner.token });
  assert.equal(claims.body.listings.find((l) => l.id === listing.id).pickup.address, '12 Residency Road, Flat 4B');
  const loserEvents = await call('GET', '/events', { token: loser.token });
  assert.ok(loserEvents.body.events.some((e) => e.type === 'rejected'));
  const loserView = await call('GET', `/listings/${listing.id}`, { token: loser.token });
  assert.equal(loserView.body.listing.pickup, undefined, 'rejected requesters never see the address');

  // Winner no-shows: donor releases, the auto-closed requests go back into the queue.
  const rel = await call('POST', `/listings/${listing.id}/release`, { token: donor.token, body: {} });
  assert.equal(rel.status, 200);
  assert.equal(rel.body.listing.status, 'available');
  const statuses = Object.fromEntries(rel.body.listing.requests.map((r) => [r.name, r.status]));
  assert.equal(statuses[winner.user.name.split(' ')[0]], 'rejected');
  assert.equal(Object.values(statuses).filter((s) => s === 'pending').length, 2);
  const requeued = await call('GET', '/events', { token: c.token });
  assert.ok(requeued.body.events.some((e) => e.type === 'requeued'));
});

test('lifecycle: release, cancel-claim, picked_up, expiry and reservation cap', async () => {
  const donor = await newUser();
  const taker = await newUser();
  const l = await newListing(donor.token, { title: 'Lifecycle eggs' });
  await call('POST', `/listings/${l.id}/claim`, { token: taker.token });
  const cancel = await call('POST', `/listings/${l.id}/cancel-claim`, { token: taker.token });
  assert.equal(cancel.status, 200);
  assert.equal((await call('GET', `/listings/${l.id}`, { token: donor.token })).body.listing.status, 'available');

  await call('POST', `/listings/${l.id}/claim`, { token: taker.token });
  const stranger = await newUser();
  assert.equal((await call('POST', `/listings/${l.id}/picked-up`, { token: stranger.token })).status, 404);
  const done = await call('POST', `/listings/${l.id}/picked-up`, { token: donor.token });
  assert.equal(done.body.listing.status, 'picked_up');
  assert.equal((await call('POST', `/listings/${l.id}/release`, { token: donor.token, body: {} })).status, 409);
  const stats = await call('GET', '/me/stats', { token: donor.token });
  assert.equal(stats.body.pickedUp, 1);

  const soon = await newListing(donor.token, { title: 'Expiring soup', pickupDeadline: now + HOUR });
  now += 2 * HOUR;
  const feed = await call('GET', `/listings/nearby?lat=${HOME.lat}&lon=${HOME.lon}&radiusKm=5`, { token: taker.token });
  assert.ok(!feed.body.listings.some((x) => x.id === soon.id));
  assert.equal((await call('GET', `/listings/${soon.id}`, { token: donor.token })).body.listing.status, 'expired');
  assert.equal((await call('POST', `/listings/${soon.id}/claim`, { token: taker.token })).status, 410);

  // A single person can hold at most 5 reservations.
  const hoarder = await newUser();
  const items = [];
  for (let i = 0; i < 6; i += 1) items.push(await newListing(donor.token, { title: `Item ${i}` }));
  const res = [];
  for (const it of items) res.push(await call('POST', `/listings/${it.id}/claim`, { token: hoarder.token }));
  assert.equal(res.filter((r) => r.status === 200).length, 5);
  assert.equal(res[5].body.error, 'too_many_reservations');

  const own = await call('POST', `/listings/${items[5].id}/claim`, { token: donor.token });
  assert.equal(own.body.error, 'own_listing');
  const cancelled = await call('POST', `/listings/${items[5].id}/cancel`, { token: donor.token });
  assert.equal(cancelled.body.listing.status, 'cancelled');
});

test('validation and admin access', async () => {
  const u = await newUser();
  const past = await call('POST', '/listings', { token: u.token, body: { title: 'x', pickupDeadline: now - 1, ...HOME } });
  assert.equal(past.body.error, 'invalid_deadline');
  const noLoc = await call('POST', '/listings', { token: u.token, body: { title: 'x', pickupDeadline: now + HOUR } });
  assert.equal(noLoc.body.error, 'invalid_location');
  assert.equal((await call('GET', '/admin/api/overview')).status, 401);
  const admin = await call('GET', '/admin/api/overview', { headers: { 'x-admin-key': 'test-admin-key' } });
  assert.equal(admin.status, 200);
  assert.ok(admin.body.users.total > 10);
  const page = await fetch(base + '/admin');
  assert.match(await page.text(), /PantryPulse Admin/);
  assert.equal((await call('GET', '/health')).body.ok, true);
  // Vercel rewrite form: /api?__route=<original path>&<original query>.
  const viaRewrite = await call('GET', `/api?__route=/listings/nearby&lat=${HOME.lat}&lon=${HOME.lon}&radiusKm=2`, { token: u.token });
  assert.equal(viaRewrite.status, 200);
  assert.ok(Array.isArray(viaRewrite.body.listings));
  assert.equal((await call('GET', '/api?__route=/health')).body.ok, true);
});
