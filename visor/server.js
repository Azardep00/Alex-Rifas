// Visor de resultados de Alex-Rifas (contenedor Node.js).
// Muestra el estado de la API y los resultados de sorteos que deja el procesador en /reportes.
const express = require("express");
const fs = require("fs");
const path = require("path");

const PORT = process.env.PORT || 3000;
const API_URL = process.env.API_URL || "http://api:8080";
const REPORTES_DIR = process.env.REPORTES_DIR || "/reportes";

const app = express();
app.disable("x-powered-by");

app.use((req, res, next) => {
  res.set("X-Content-Type-Options", "nosniff");
  res.set("X-Frame-Options", "DENY");
  res.set("Content-Security-Policy", "default-src 'self'; style-src 'self' 'unsafe-inline'");
  next();
});

app.get("/salud", (req, res) => res.json({ estado: "UP" }));

// Estado de la API (consulta su health-check por la red interna)
app.get("/estado-api", async (req, res) => {
  try {
    const r = await fetch(`${API_URL}/actuator/health`, { signal: AbortSignal.timeout(3000) });
    const cuerpo = await r.json();
    res.json({ estado: cuerpo.status || "DESCONOCIDO" });
  } catch {
    res.json({ estado: "CAIDA" });
  }
});

// Resultados procesados: lee los .json que escribe el contenedor "procesador" en el volumen compartido.
app.get("/resultados", (req, res) => {
  let archivos = [];
  try {
    archivos = fs.readdirSync(REPORTES_DIR).filter((f) => f.endsWith(".json"));
  } catch {
    return res.json([]);
  }
  const resultados = [];
  for (const archivo of archivos) {
    try {
      resultados.push(JSON.parse(fs.readFileSync(path.join(REPORTES_DIR, archivo), "utf8")));
    } catch {
      // archivo a medio escribir o corrupto: se ignora
    }
  }
  resultados.sort((a, b) => String(b.procesado_en).localeCompare(String(a.procesado_en)));
  res.json(resultados);
});

app.get("/", (req, res) => {
  res.type("html").send(PAGINA);
});

const PAGINA = `<!doctype html>
<html lang="es">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Resultados - Alex-Rifas</title>
  <style>
    body { font-family: system-ui, sans-serif; max-width: 48rem; margin: 3rem auto; padding: 0 1rem; color: #1f2937; }
    table { width: 100%; border-collapse: collapse; margin-top: 1rem; }
    th, td { text-align: left; padding: .5rem; border-bottom: 1px solid #e5e7eb; }
    .vacio { color: #6b7280; }
    a { color: #2563eb; }
  </style>
</head>
<body>
  <h1>Resultados de sorteos</h1>
  <p>Estado de la API: <strong id="estado">...</strong></p>
  <table>
    <thead><tr><th>Rifa</th><th>Vendidos</th><th>% vendido</th><th>Ganadores</th></tr></thead>
    <tbody id="filas"><tr><td colspan="4" class="vacio">Cargando...</td></tr></tbody>
  </table>
  <p><a href="/">Volver al sitio</a></p>
  <script src="app.js"></script>
</body>
</html>`;

// El script va en un archivo propio (mismo origen) porque el CSP "default-src 'self'" bloquea los <script> en línea.
// Las rutas son relativas ("resultados") para funcionar bajo el prefijo /visor/ del proxy.
const SCRIPT = `
fetch("estado-api").then(r => r.json()).then(d => { document.getElementById("estado").textContent = d.estado; });
fetch("resultados").then(r => r.json()).then(lista => {
  const cuerpo = document.getElementById("filas");
  cuerpo.textContent = "";
  if (!lista.length) {
    const tr = document.createElement("tr");
    const td = document.createElement("td");
    td.colSpan = 4; td.className = "vacio"; td.textContent = "Aún no hay sorteos procesados.";
    tr.appendChild(td); cuerpo.appendChild(tr); return;
  }
  for (const r of lista) {
    const tr = document.createElement("tr");
    const ganadores = (r.ganadores || []).map(g => g.premio + ": #" + g.numero).join(", ");
    [r.titulo || r.rifa_codigo, r.boletos_vendidos + "/" + r.cantidad_boletos, r.porcentaje_vendido + "%", ganadores]
      .forEach(texto => { const td = document.createElement("td"); td.textContent = texto; tr.appendChild(td); });
    cuerpo.appendChild(tr);
  }
});
`;

app.get("/app.js", (req, res) => {
  res.type("application/javascript").send(SCRIPT);
});

app.listen(PORT, "0.0.0.0", () => console.log(`visor escuchando en :${PORT}`));
