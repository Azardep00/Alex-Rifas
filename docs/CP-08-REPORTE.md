# CP-08 · Loterías, resultados cargados a mano y auditoría

Todo bajo `/api/admin/**` (la regla de `SecurityConfig` ya exige ADMIN) y, además, cada método de servicio vuelve a exigir ADMIN (`SoloAdmin`). Un resultado de lotería **nunca se borra**: se anula con motivo.

## Endpoints
| Método | Ruta | Respuesta |
|---|---|---|
| GET | `/api/admin/loterias?activa=` | 200 lista de `LoteriaDto` (orden alfabético) |
| POST | `/api/admin/loterias` | 201 `LoteriaDto` |
| PUT | `/api/admin/loterias/{id}` | 200 `LoteriaDto` (`activa` ausente = no cambia) |
| GET | `/api/admin/loterias/{id}/resultados?estado=&pagina=0&tamano=20` | 200 página de `ResultadoDto` (más recientes primero; `tamano` máx. 100) |
| POST | `/api/admin/loterias/{id}/resultados` | 201 `ResultadoDto` en `PENDIENTE_VERIFICACION` |
| POST | `/api/admin/resultados-loteria/{id}/verificar` | 200 `ResultadoDto` en `VERIFICADO` |
| POST | `/api/admin/resultados-loteria/{id}/anular` (`{"motivo": "…"}`) | 200 `ResultadoDto` en `ANULADO` |

Errores: 400 (datos inválidos, fecha futura, motivo ausente), 403 (no ADMIN, o quien cargó intenta verificar con segundo verificador exigido), 404, 409 (nombre de lotería repetido, lotería inactiva, resultado vigente repetido, transición de estado inválida).

## Reglas de negocio
- **Loterías y chances = filas propias.** `tipo` = `LOTERIA` (semanal) o `CHANCE` (diario). Cada franja de un chance ("Dorado Mañana/Tarde/Noche") es su propia fila. `dias_semana` (ISO 1=lunes…7=domingo) y `hora_sorteo` son solo referencia: la fecha real del sorteo la fija la rifa. El nombre es único sin importar mayúsculas y se normalizan los espacios.
- **Número mayor = texto de 4 dígitos** (`0377` no pierde el cero). Cada `ResultadoDto` trae `ultimos2/3/4`, o sea qué boleto gana en rifas de 100, 1000 y 10 000 números (ADR-06). Con **8954** salen **54**, **954** y **8954**; con **0377**, **77**, **377** y **0377**.
- **Estados:** `PENDIENTE_VERIFICACION → VERIFICADO → ANULADO` (también se puede anular un pendiente). Solo `VERIFICADO` podrá usarse para sortear (CP-09).
- **Un resultado vigente por sorteo** (lotería + fecha): índice único parcial `WHERE estado <> 'ANULADO'`. Uno anulado no estorba para cargar el correcto. Una carrera entre dos cargas simultáneas termina en 409 (el handler ya traduce `DataIntegrityViolationException`).
- **Fecha del sorteo:** no puede ser futura, calculada con la fecha de **Colombia** (no UTC): a las 22:00 del 2-nov en Bogotá, el 3-nov aún es futuro.
- **Segundo verificador** (`LOTERIAS_EXIGIR_SEGUNDO_VERIFICADOR`, por defecto `false`): si es `true`, quien cargó un resultado no puede verificarlo (403). Desactivado, cargar y verificar siguen siendo dos pasos separados, y la auditoría guarda `mismaPersonaCargoYVerifico`. Actívalo cuando haya dos o más administradores.
- **Sin bloqueo por autorización de la rifa:** no se impide publicar rifas por falta de `numero_autorizacion`. El riesgo legal sigue descrito en el informe del CP-07.

## Auditoría (`auditoria_eventos`)
`AuditoriaService.registrar` guarda quién, qué entidad, qué acción, `requestId` (MDC) y detalles JSON, **dentro de la transacción** de la operación: si no se puede auditar, la operación tampoco ocurre; si la operación falla (409, 403…), no queda rastro de algo que no pasó.

| Acción | Entidad | Detalles |
|---|---|---|
| `LOTERIA_CREADA` / `LOTERIA_ACTUALIZADA` | `loteria` | `despues` (y `antes` al actualizar) |
| `RESULTADO_CARGADO` | `resultado_loteria` | lotería, fecha, número, serie, fuente, evidencia |
| `RESULTADO_VERIFICADO` | `resultado_loteria` | número, fecha, `cargadoPor`, `mismaPersonaCargoYVerifico` |
| `RESULTADO_ANULADO` | `resultado_loteria` | `estadoAnterior`, número, fecha, motivo |
| `PAGO_CONFIRMADO_MANUAL` *(pendiente de CP-06)* | `compra` | rifa, monto, boletos, método, referencia |
| `RESERVA_LIBERADA` *(pendiente de CP-06)* | `compra` | boletos liberados, motivo |

La columna `ip_origen` (INET) no se mapea a propósito: el `request_id` permite cruzar con el log de acceso.

## Migración V3
Tablas `loterias` y `resultados_loteria`; columna `rifas.loteria_id` (nullable). Las rifas existentes se enlazan por el texto de `loteria_nombre` (sin importar mayúsculas ni espacios en los bordes): se crea una lotería por cada nombre distinto. Las que se crean así nacen como `LOTERIA`; si alguna es en realidad un chance, edítala. `rifas.loteria_nombre` se conserva para no romper la API actual.

## Archivos
Nuevos: `V3__loterias_y_resultados.sql`, entidades `Loteria`, `ResultadoLoteria`, `AuditoriaEvento`; enums `TipoLoteria`, `EstadoResultado`; repositorios `LoteriaRepository`, `ResultadoLoteriaRepository`, `AuditoriaEventoRepository`; `LoteriaDtos`, `LoteriasProperties`, `SoloAdmin`, `AuditoriaService`, `LoteriaService`, `ResultadoLoteriaService`, `AdminLoteriaController`; `docs/CP-07-INFORME-LOTERIAS.md`.
Modificados: `AlexRifasApplication` (registra `LoteriasProperties`), `Rifa` (+ `loteriaId`), `application.yml` (+ `app.loterias`), `CompraService` y `AdminCompraService` (+ auditoría).
Tests nuevos (38): `ResultadoLoteriaTest` (3), `AuditoriaServiceTest` (2), `LoteriaServiceTest` (9), `ResultadoLoteriaServiceTest` (20), `CompraServiceAuditoriaTest` (4). Ampliados: `AdminCompraServiceTest` (+3), y los constructores de `CompraServiceTest` y `ReservaPublicaServiceTest`.

## Verificación hecha
- **V3 contra PostgreSQL 16 real**, aplicada después de V1 y V2 y sobre rifas ya existentes (nombres con distinta capitalización y espacios, vacíos y nulos): el enlace `loteria_id` quedó correcto y no se crearon duplicados. Se probaron las restricciones: número de 3 dígitos, `VERIFICADO` sin fecha de verificación, anular sin motivo, días fuera de 1–7, tipo inválido, nombre repetido sin importar mayúsculas, resultado vigente repetido (falla) y recarga tras anular (pasa), e inserción JSONB en `auditoria_eventos`.
- **Compilación y ejecución de los tests con stubs** de Spring/JPA/Mockito/JUnit (Maven Central no es accesible desde este entorno): 67 tests pasan, entre nuevos y los existentes de compras y reservas. Además introduje a propósito 4 fallos (quitar la regla del segundo verificador, quitar cada auditoría, invertir los últimos dígitos) y los tests los detectaron.

## Pendiente / no verificado
- **`mvn test` real no se ejecutó.** Los stubs no son Spring ni Mockito de verdad. Corre `mvn test` antes de aceptar el CP.
- **Mapeo de entidades con Hibernate real:** el perfil de prod usa `ddl-auto: validate`. Valida que `Loteria`, `ResultadoLoteria` y `AuditoriaEvento` pasen la validación y que el mapeo JSON de `detalles_json` (agregué `columnDefinition = "jsonb"`, igual que `PagoWompi`) escriba bien contra Postgres. Levanta la app y haz una llamada real a cada endpoint.
- **Flyway en el arranque:** probé el SQL con `psql`, no a través de Flyway (checksum, orden de versiones).
- **Autorización HTTP sin test MockMvc** (mismo pendiente que el CP-06): los tests prueban el servicio, no la regla de `SecurityConfig`.
- **Validación de DTOs** (`@Valid`) no se ejecuta en estos tests; el servicio repite las reglas importantes, pero conviene una prueba HTTP.
- **Evidencia:** `evidencia_url` es solo un enlace; no hay subida de archivos. La captura se guarda donde prefieras y se enlaza.
- **El resultado no se usa todavía para sortear:** eso es el CP-09 (`sortear-por-loteria`, regla de cupo vacío, rifa ligada a `loteria_id`). Hoy `rifas.loteria_id` se llena solo por la migración; falta que crear/editar rifa lo reciba (propongo hacerlo en CP-09).
