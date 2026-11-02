# CP-06 · Administración de compras y resumen de rifa

Todo bajo `/api/admin/**` (la regla de `SecurityConfig` ya exige rol ADMIN) y, además, cada método del servicio vuelve a exigir ADMIN (defensa en profundidad, igual que `confirmarPagoManual`).

## Endpoints
| Método | Ruta | Respuesta |
|---|---|---|
| GET | `/api/admin/compras?estado=&rifa=&telefono=&pagina=0&tamano=20` | 200 página de `CompraAdminDto` (todos los filtros opcionales) |
| POST | `/api/admin/compras/{codigo}/liberar` (cuerpo opcional `{"motivo": "…"}`) | 200 `{codigo, estado:CANCELADA, boletosLiberados, liberadaEn}` |
| GET | `/api/admin/rifas/{codigo}/resumen` | 200 `ResumenRifa` |

Errores: 403 (no ADMIN), 404 (compra/rifa), 400 (teléfono inválido, `estado` desconocido), 409 (liberar algo que no está pendiente).

## Reglas de negocio
- **Listar:** las PENDIENTES salen por vencimiento ascendente (la cola se atiende de la más urgente); el resto, de la más reciente a la más antigua. `tamano` máx. 100. El admin ve el **teléfono completo** (lo necesita para contactar al comprador por WhatsApp). Se arma con 2 consultas en lote (boletos y rifas), no N+1.
- **Liberar:** solo `PENDIENTE → CANCELADA`; los boletos RESERVADOS de esa compra pasan a DISPONIBLE. Mismo orden de bloqueo que `confirmarPagoManual` (compra y luego boletos). Si la reserva ya había vencido y otro comprador tomó el número, **no se le quita**. Confirmada/expirada/cancelada → 409 (devolver un pago confirmado es otro proceso, fuera de este CP). Devuelve el cupo por IP del CP-05 (`AntiabusoService.liberar`).
- **Resumen:** `reservados` cuenta solo reservas vigentes (coherente con la grilla del CP-04); `disponibles = cantidad − vendidos − reservados`; `porcentajeVendido` = vendidos/cantidad; `recaudoConfirmado` = suma de compras CONFIRMADAS; `recaudoPendiente` = reservas vigentes sin pagar (no es ingreso); `recaudoMaximo` = precio × cantidad; contadores de compras por estado.

## Archivos
Nuevos: `AdminDtos`, `AdminCompraService`, `AdminRifaController`; test `AdminCompraServiceTest` (11).
Modificados: `AdminCompraController` (+ listar, liberar), `CompraRepository` (+ `JpaSpecificationExecutor`, sumas y conteo por estado), `BoletoRepository` (+ `findByCompraIdIn`, conteo de reservas vigentes).

## Pendiente / no verificado
- **`mvn test` no se ejecutó** (sin Maven Central). Verifiqué solo que el código no tiene errores estructurales de compilación con stubs mínimos de Spring. Las consultas JPQL de `CompraRepository` (`sumarSubtotal`, `sumarPendientesVigentes`, `contarPorEstado`) y el `Specification` **no se han ejecutado contra Postgres/H2**: corre el arranque de la app y una llamada real a cada endpoint.
- **Autorización a nivel HTTP sin test:** los tests prueban que el servicio rechaza ORGANIZADOR/CLIENTE/null con 403, pero no hay un test MockMvc que pruebe la regla de `SecurityConfig` (requiere levantar el contexto). Propuesto junto a Testcontainers (CP-03b).
- **Sin auditoría persistente:** la liberación solo queda en el log (con admin y motivo aplanado). La tabla `auditoria_eventos` existe pero no tiene entidad; el plan la usa en el CP-08, ahí conviene registrar también liberaciones y confirmaciones.
- **Organizadores:** el plan del CP-18 habla de la cola de pagos para el panel; hoy `/api/admin/**` es solo ADMIN (igual que confirmar pago). Si el ORGANIZADOR debe gestionar las compras de sus rifas, hay que abrir ruta y validar propiedad de la rifa: dímelo antes del CP-18.
- `comprasPendientes` del resumen cuenta el estado guardado: una reserva vencida hasta que el job la expira (≤30 s) aún cuenta como pendiente.
