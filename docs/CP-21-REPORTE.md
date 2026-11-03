# CP-21 · Respaldos (backend)

**Nota de plan:** el CP-10 se omitió por decisión del dueño del proyecto.

## Qué hay
- `scripts/respaldo.sh`: `pg_dump` → gzip → cifrado simétrico AES-256 con gpg.
- `.github/workflows/respaldo.yml`: semanal (domingo 08:00 UTC) y manual; guarda el cifrado como artefacto 30 días. Requiere los secretos `PG_URL_RESPALDO` y `BACKUP_PASSPHRASE`.
- `docs/RESPALDOS.md`: qué se respalda, restauración paso a paso, RPO/RTO realistas y límites.

## Verificación
**NADA ejecutado** (sin red ni Postgres). Valida en tu máquina: `bash -n scripts/respaldo.sh` y una restauración de prueba siguiendo `RESPALDOS.md`.

## Pendiente
- Crear los dos secretos en GitHub y correr el workflow una vez a mano.
- El e2e y los headers están en el repo del frontend.
