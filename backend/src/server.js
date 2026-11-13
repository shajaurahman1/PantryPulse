// Local development server: TURSO_DATABASE_URL=file:local.db node src/server.js
import { createServer } from 'node:http';
import handler from './app.js';

process.env.TURSO_DATABASE_URL ||= 'file:local.db';
const port = Number(process.env.PORT || 8787);
createServer((req, res) => handler(req, res)).listen(port, () => {
  console.log(`PantryPulse API on http://localhost:${port}`);
});
