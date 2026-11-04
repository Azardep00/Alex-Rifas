# Despliegue a producción (CP-20)

Arquitectura: **Vercel** (Angular + rewrite `/api/*`) → **Render** (API en Docker) → **Postgres externo**.
Redis es opcional. No hace falta CORS: el navegador solo habla con el dominio de Vercel (ADR-03).

> Las condiciones de los planes gratuitos cambian. Antes de crear cada cuenta, confirma en la página oficial del
> proveedor los límites vigentes (horas, almacenamiento, suspensión por inactividad, expiración de la base).

## 0. Antes de empezar
- CI en verde en los dos repos (`mvn verify`, `humo-postgres`, `ng test`, `ng build`, e2e).
- Rama `main` actualizada. **Nunca** subas `.env` ni valores reales a Git.
- Aviso legal: en Colombia las rifas con dinero real requieren autorización. Esta instalación es de demostración.

## 1. Postgres externo
El Postgres gratis de Render **expira a los 30 días**; por eso la base va aparte (Neon, Supabase u otro).
1. Crea el proyecto y una base `alexrifas`.
2. Copia los datos de conexión y arma la URL **JDBC**:
   `jdbc:postgresql://HOST:5432/alexrifas?sslmode=require`
   (usuario y clave van aparte en `DB_USERNAME` y `DB_PASSWORD`; no los pegues dentro de la URL).
3. Si el proveedor ofrece un host "pooler" y otro directo, usa el **directo** para la API (Flyway necesita sesión normal).
4. Flyway crea todas las tablas (V1…V4) al primer arranque. No ejecutes SQL a mano.

## 2. API en Render
1. *New → Blueprint* y elige el repo del backend: lee `render.yaml`.
2. Cuando pida los valores `sync: false`, pega:

| Variable | Valor |
|---|---|
| `DB_URL` | la URL JDBC del paso 1 |
| `DB_USERNAME` / `DB_PASSWORD` | los del proveedor |
| `ADMIN_CORREO` / `ADMIN_CONTRASENA` | tu admin inicial (clave de 10+ caracteres) |
| `JWT_SECRET` | lo genera Render (`generateValue`) |

3. Espera el deploy. La comprobación `GET /actuator/health` debe dar `{"status":"UP"}`.
4. Anota la URL pública, por ejemplo `https://alex-rifas-api.onrender.com`.

El admin inicial **solo se crea si todavía no existe ningún ADMIN** (`AdminInicializador`). Si te equivocas de clave
antes de usarla, bórrala de la base o cámbiala desde el panel.

## 3. Frontend en Vercel
1. En el repo del frontend, edita `vercel.json` y reemplaza `REEMPLAZAR-HOST-DE-LA-API.example.com` por el host de
   Render (sin `https://` en esa parte; el destino queda `https://alex-rifas-api.onrender.com/api/:path*`).
2. *Add New → Project* en Vercel, importa el repo. Framework: **Other** (el `vercel.json` ya define build y salida).
3. Cada push a `main` despliega solo.

## 4. Comprobación de punta a punta (en el celular, en este orden)
1. `https://TU-DOMINIO.vercel.app` carga y muestra las rifas (la primera vez puede tardar ~1 min: aparece el aviso
   «el servidor está despertando» y se reintenta solo).
2. `/admin/login` con `ADMIN_CORREO` / `ADMIN_CONTRASENA` entra al panel.
3. Crea una rifa de prueba (100 números), publícala, reserva dos números desde otro navegador y confírmalos desde
   la cola de pagos. La grilla debe pasar a «vendido».
4. Consola del navegador (escritorio, F12): **sin** errores de Content-Security-Policy.
5. Pega en Render → Logs la IP que ve la API en una reserva: debe ser **la tuya**, no la de Vercel. Si ves siempre la
   misma IP, ajusta `ANTIABUSO_SALTOS_PROXY` (hoy 2: Vercel → Render → API) y vuelve a probar el tope por IP.

## 5. Cold start y horas gratis
- Un servicio gratis de Render se duerme tras ~15 min sin tráfico y tarda ~1 min en despertar. Vercel puede responder
  502/503/504 mientras tanto: el frontend reintenta los GET solo (`servidor.ts`) y muestra el aviso; los POST
  (reservar, confirmar) **nunca** se repiten solos.
- **No uses un "keep-alive"/ping permanente**: gastaría las horas gratis del mes. Para una presentación, abre la URL
  de la API 2–3 minutos antes.

## 6. Redis (opcional)
Sin Redis la API funciona: el tope de reservas por IP y la caché de la grilla se omiten (el tope por teléfono sigue en
la base). Para activarlo define `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD` y `REDIS_SSL=true` si el proveedor lo exige.

## 7. Respaldos
`.github/workflows/respaldo.yml` hace un `pg_dump` cifrado cada domingo. Crea en GitHub → Settings → Secrets:
`PG_URL_RESPALDO` (URL `postgresql://…`) y `BACKUP_PASSPHRASE`. Pruébalo una vez con *Run workflow* y descarga el
artefacto. Para restaurar, usa `scripts/respaldo.sh` como referencia (descifrar con gpg y `pg_restore`/`psql`).

## 8. Si algo falla
| Síntoma | Causa probable |
|---|---|
| La API no arranca en Render | Falta `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` o `JWT_SECRET` (a propósito no tienen valor por omisión): mira el log |
| `Unable to obtain connection` | URL sin `?sslmode=require`, usuario/clave mal, o base dormida: reintenta en 1 min |
| El sitio carga pero no hay datos | El host en `vercel.json` sigue siendo el de ejemplo |
| Página sin estilos | CSP bloqueando algo: revisa la consola; no agregues `unsafe-inline` a `script-src` |
| 401 en el panel tras mucho tiempo | La sesión venció (7 días): vuelve a ingresar |
