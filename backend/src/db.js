import { createClient } from '@libsql/client';
import { readFileSync } from 'node:fs';

const schema = readFileSync(new URL('./schema.sql', import.meta.url), 'utf8');

let client;
let ready;

/** One shared client per process; the schema is applied once (idempotent). */
export function db() {
  if (!client) {
    const url = process.env.TURSO_DATABASE_URL;
    if (!url) throw new Error('TURSO_DATABASE_URL is not set');
    client = createClient({ url, authToken: process.env.TURSO_AUTH_TOKEN });
  }
  return client;
}

export async function ensureSchema() {
  if (!ready) {
    ready = db().executeMultiple(schema).then(migrate).catch((e) => { ready = undefined; throw e; });
  }
  return ready;
}

/** Columns added after the first release; CREATE TABLE IF NOT EXISTS won't add them to an existing table. */
async function migrate() {
  try {
    await db().execute('ALTER TABLE users ADD COLUMN notify_radius_km REAL NOT NULL DEFAULT 0');
  } catch (e) {
    if (!/duplicate column/i.test(String(e.message))) throw e;
  }
}

/** Test helper: point the module at a different database. */
export function resetForTests() {
  client = undefined;
  ready = undefined;
}
