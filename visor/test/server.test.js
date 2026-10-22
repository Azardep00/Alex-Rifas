const { test, before, after } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { once } = require('node:events');

const reportes = fs.mkdtempSync(path.join(os.tmpdir(), 'alex-rifas-resultados-'));
process.env.REPORTES_DIR = reportes;
process.env.API_URL = 'http://127.0.0.1:1';
const app = require('../server');
let server;
let baseUrl;

before(async () => {
  server = app.listen(0, '127.0.0.1');
  await once(server, 'listening');
  baseUrl = `http://127.0.0.1:${server.address().port}`;
});

after(async () => {
  await new Promise((resolve, reject) => server.close((error) => error ? reject(error) : resolve()));
  fs.rmSync(reportes, { recursive: true, force: true });
});

test('health endpoint returns UP', async () => {
  const response = await fetch(`${baseUrl}/salud`);
  assert.equal(response.status, 200);
  assert.deepEqual(await response.json(), { estado: 'UP' });
});

test('results endpoint reads and sorts JSON report files', async () => {
  fs.writeFileSync(path.join(reportes, 'ABC123.json'), JSON.stringify({ rifa_codigo: 'ABC123', titulo: 'Prueba', procesado_en: '2026-01-01T00:00:00Z' }));
  fs.writeFileSync(path.join(reportes, 'ignorar.txt'), 'no es un reporte');
  const response = await fetch(`${baseUrl}/resultados`);
  assert.equal(response.status, 200);
  const data = await response.json();
  assert.equal(data.length, 1);
  assert.equal(data[0].rifa_codigo, 'ABC123');
});

test('the viewer returns the dashboard page and its script', async () => {
  const page = await fetch(`${baseUrl}/`);
  assert.equal(page.status, 200);
  assert.match(await page.text(), /Resultados de sorteos/);
  const script = await fetch(`${baseUrl}/app.js`);
  assert.equal(script.status, 200);
  assert.match(await script.text(), /addEventListener/);
});
