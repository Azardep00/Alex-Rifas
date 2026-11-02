# CP-00 · Línea base (2026-11-02)

## Hecho
- Rama `v2` creada desde `main` (commit `5408d06`). Tag `v1-entrega` marca la entrega original.
- `docker compose config` con `.env.example`: **sin errores de interpolación**.
- Plan y ADRs guardados en `docs/PLAN-V2.md`.

## No verificado
- `mvn test`: **no se pudo ejecutar** en el entorno de Claude (bloquea `repo.maven.apache.org`). Ejecútalo en tu máquina:
  ```bash
  mvn -B test                      # Java 21
  ```
- Inventario de tests existentes: 10 métodos `@Test` en 6 clases (JWT 2, TokenUtils 2, Auth 2, Compra 2, Rifa 1, Sorteo 1), 4 en el procesador Python y 3 en el visor Node. Aún no hay tests de controladores ni de las migraciones.

## Decisiones fijadas
- Reserva por defecto: **60 min** (configurable por rifa; hoy el código fija 15 min).
- Regla de cupo vacío por defecto: **`REPETIR_SIGUIENTE_LOTERIA`**.

## Siguiente: CP-02 (migración V2)
