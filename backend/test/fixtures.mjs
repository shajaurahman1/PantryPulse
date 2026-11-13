// Captures real API responses as fixtures for the Android client's parser tests.
// Usage: node test/fixtures.mjs <output-dir>
import { createServer } from 'node:http';
import { mkdtempSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

const out = process.argv[2];
process.env.TURSO_DATABASE_URL = `file:${join(mkdtempSync(join(tmpdir(), 'pp-fx-')), 'fx.db')}`;
const { default: handler } = await import('../src/app.js');
const server = createServer(handler);
await new Promise((r) => server.listen(0, r));
const base = `http://127.0.0.1:${server.address().port}`;
const call = async (method, path, token, body) => (await fetch(base + path, {
  method, headers: { 'content-type': 'application/json', ...(token ? { authorization: `Bearer ${token}` } : {}) },
  body: body ? JSON.stringify(body) : undefined,
})).json();
const save = (name, json) => writeFileSync(join(out, `${name}.json`), JSON.stringify(json, null, 2));

const shop = await call('POST', '/auth/register', null, { email: 'shop@example.com', password: 'secret12', name: 'Asha Rao', accountType: 'shop', shopName: 'Fresh Mart', shopAddress: '5 Market St', openingHours: '8am–9pm', lat: 12.9716, lon: 77.5946 });
save('auth', shop);
const taker = await call('POST', '/auth/register', null, { email: 'taker@example.com', password: 'secret12', name: 'Ravi Kumar' });
const deadline = Date.now() + 6 * 3600 * 1000;
const created = await call('POST', '/listings', shop.token, { title: 'Sourdough loaf', category: 'BAKERY', quantityText: '1 loaf', notes: 'Unopened', itemExpiry: '2026-11-14', pickupDeadline: deadline, areaLabel: 'Fresh Mart', pickupInstructions: 'Ask at the till', claimMode: 'approval', clientProductId: 42 });
await call('POST', '/listings', shop.token, { title: 'Bananas', quantityText: '1 kg', pickupDeadline: deadline + 3600000, claimMode: 'direct' });
save('nearby', await call('GET', '/listings/nearby?lat=12.975&lon=77.5946&radiusKm=5', taker.token));
await call('POST', `/listings/${created.listing.id}/requests`, taker.token, { message: 'Can collect at 6' });
save('mine_with_request', await call('GET', '/listings/mine', shop.token));
const reqId = (await call('GET', `/listings/${created.listing.id}`, shop.token)).listing.requests[0].id;
await call('POST', `/listings/${created.listing.id}/requests/${reqId}/accept`, shop.token);
save('claims_reserved', await call('GET', '/claims/mine', taker.token));
save('events', await call('GET', '/events?after=0', taker.token));
save('stats', await call('GET', '/me/stats', shop.token));
save('error_already_claimed', { error: 'already_claimed', message: 'Sorry — someone else just claimed this' });
server.close();
console.log('fixtures written');
