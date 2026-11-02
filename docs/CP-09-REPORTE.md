# CP-09 · Sorteo por lotería

Reemplaza a `docs/CP-09-PARCIAL.md` (bórralo). Nadie escoge el número ganador: lo deciden resultados de lotería **VERIFICADOS** (CP-08), uno por premio, en orden de fecha.

## Endpoints (solo ADMIN, bajo `/api/admin/**`; el servicio vuelve a exigir ADMIN)
| Método | Ruta | Respuesta |
|---|---|---|
| GET | `/api/admin/rifas/{rifaId}/sortear-por-loteria/previsualizar` | 200 `SorteoLoteriaDto` (no guarda nada; `completo=false` si falta o está sin verificar un resultado, con `mensaje` que dice qué hacer) |
| POST | `/api/admin/rifas/{rifaId}/sortear-por-loteria` | 201 `SorteoLoteriaDto` con `ejecutado=true`; rifa → `SORTEADA` |

Errores: 403 (no ADMIN), 404 (rifa), 409 (no configurada por lotería, no está CERRADA, ya sorteada, boletos reservados, sin premios, vendidos < premios, **falta o pendiente un resultado**, configuración inconsistente).

## Reglas de negocio
1. **Ganador = últimos N dígitos** del número mayor (2, 3 o 4), con ceros a la izquierda. Los números salen como texto (`07`, `042`).
2. **Un resultado por premio**, en orden de posición del premio y de fecha del resultado, **desde la fecha del sorteo de la rifa en hora de Colombia**. Solo cuentan los resultados no anulados de la lotería de la rifa.
3. **Si el siguiente resultado está PENDIENTE_VERIFICACION, el cálculo se detiene**; no se salta. Es lo que impide “escoger” el resultado que conviene.
4. **Cupo vacío** (boleto no vendido) según `regla_vacante`:
   - `REPETIR_SIGUIENTE_LOTERIA`: ese resultado se descarta y el premio espera al **siguiente sorteo de la misma lotería**.
   - `ADJUDICAR_SIGUIENTE_VENDIDO`: gana el siguiente número vendido y sin premio, en orden ascendente con vuelta al inicio.
5. **Un boleto no gana dos premios**: si el número ya ganó otro premio cuenta como cupo vacío (misma regla).
6. **Atómico**: o se deciden todos los premios y la rifa queda SORTEADA, o no se guarda nada (409).
7. **Determinista**: cargar un resultado posterior no cambia lo ya decidido.
8. **Concurrencia**: al ejecutar se leen los resultados con `PESSIMISTIC_WRITE`; `anular` también bloquea su fila. Nadie puede verificar/anular un resultado mientras se decide el sorteo, y la doble ejecución la frena además el `@Version` de la rifa.

## Resultado guardado y auditoría
- `ganadores` guarda `resultado_loteria_id`, `numero_resultado` (lo que salió) y `adjudicado_por_vacante` (V4).
- `auditoria_eventos`: acción `SORTEO_LOTERIA_EJECUTADO` (entidad `rifa`) con lotería, dígitos, regla y **cada paso** (resultado, número, resolución, causa, boleto), incluidos los resultados descartados. Es el único lugar donde queda rastro de los pasos `REPETIDO`.
- Se publica el mismo `SorteoProcesadoEvent` que el sorteo manual (Redis, tras el commit).

## Crear la rifa (`RifaRequest`, 4 campos nuevos AL FINAL, todos opcionales)
`loteriaId`, `digitos`, `reglaVacante` (defecto `REPETIR_SIGUIENTE_LOTERIA`), `reservaMinutos` (defecto 15, rango 5–1440).
- Método `LOTERIA` ⇒ **obligatorios** `loteriaId` (existente y activa) y `digitos`; `cantidadBoletos` debe ser **exactamente 10^dígitos** (100/1000/10 000).
- Con otro método, `loteriaId` y `digitos` se rechazan (400).
- `loteriaNombre` pasa a ser el nombre del catálogo.
- `RifaGestionDto` y `RifaDetalle` devuelven `digitos`, `reglaVacante`, `reservaMinutos` (gestión también `loteriaId`). La página `detalle.html` muestra los dígitos y la regla de cupo vacío (ADR-07: la regla debe ser pública antes de vender).

## Bloqueos
- **Sorteo manual** (`/api/organizador/rifas/{id}/sortear`): 409 si la rifa está configurada por lotería (`LOTERIA` + `loteriaId` + `digitos`).
- **Anular resultado**: 409 si ya decidió un premio.

## ⚠ Cambios de comportamiento
- Crear una rifa `LOTERIA` **ya no basta con `loteriaNombre`**: exige `loteriaId` y `digitos`. Las rifas antiguas (enlazadas por nombre en V3, sin dígitos) **siguen** con sorteo manual.
- Cambió el constructor de `RifaService` (+`LoteriaRepository`) y de `ResultadoLoteriaService` (+`GanadorRepository`); los records `RifaRequest`, `RifaGestionDto` y `RifaDetalle` ganaron campos al final.

## Archivos
Nuevos: `V4__sorteo_por_loteria.sql`, `CausaVacante`, `ResolucionPaso`, `SorteoLoteriaCalculador`, `SorteoLoteriaService`, `AdminSorteoLoteriaController`; tests `SorteoLoteriaCalculadorTest` (17), `SorteoLoteriaServiceTest` (17).
Modificados: `Ganador`, `Rifa` (+`sorteaPorLoteria()`), `GanadorRepository`, `ResultadoLoteriaRepository` (+3 consultas), `RifaDtos`, `SorteoDtos`, `RifaService`, `SorteoService`, `ResultadoLoteriaService`, `web/templates/detalle.html`; tests `RifaServiceTest` (+9), `SorteoServiceTest` (+2), `ResultadoLoteriaServiceTest` (+2, constructor).

## Verificación hecha
- Maven Central no es accesible desde este entorno (403), así que **no corrió `mvn test`**.
- Compilé el código de producción y los tests de este CP con **JDK 17**, **JUnit 5.10 y Mockito 2.23 reales**, y **stubs mínimos de Spring/JPA/Hibernate/Jakarta** escritos por mí: 72 tests pasan (los 44 de lotería/rifa/sorteo más los existentes de `ResultadoLoteriaServiceTest` y `ResultadoLoteriaTest`).
- Mutaciones: quitar el bloqueo del sorteo manual, el de anular, la validación 10^dígitos o el lock de lectura hace fallar tests. Dos tests eran débiles (pasaban por otra causa) y los reforcé.

## Pendiente / no verificado
- **`mvn test` real** (Spring Boot 3.5, Mockito 5, modo estricto de `MockitoExtension`). Mockito 2.23 no tiene `verifyNoInteractions`; lo probé con `verifyZeroInteractions`, que es equivalente.
- **V4 contra PostgreSQL real y Flyway**, y `ddl-auto: validate` con los 3 campos nuevos de `Ganador`.
- **Consultas con `@Lock`**: probar contra Postgres que `findVigentesDesdeParaActualizar` y `findByIdParaActualizar` bloquean como se espera.
- **Autorización HTTP sin test MockMvc** (igual que CP-06/08) y `@Valid` de los DTOs sin ejecutar.
- El endpoint público de ganadores (`GanadorDto`) **no** muestra aún el número de lotería ni la regla aplicada; conviene en CP-15/19.
