# CP-05 · Antiabuso de reservas sin cuenta (ADR-05)

## Comportamiento
`POST /api/publico/reservas` ahora responde **429** (`Retry-After` en segundos + mensaje en español) cuando:

| Tope | Valor por defecto | Dónde se cuenta |
|---|---|---|
| Reservas PENDIENTES vigentes por **teléfono** (todas las rifas) | 3 | Base de datos (`compras`, índice `idx_compras_telefono_estado` de V2) |
| Reservas PENDIENTES vigentes por **IP** | 5 | Redis: `abuso:ip:{ip}` (ZSET código → vencimiento) |

Configurable por entorno: `ANTIABUSO_MAX_PENDIENTES_TELEFONO`, `ANTIABUSO_MAX_PENDIENTES_IP`, `ANTIABUSO_SALTOS_PROXY`.

Ejemplo de error: `{"estado":429,"mensaje":"Ya tienes 3 reservas pendientes de pago. Págalas o espera a que venzan para reservar más; podrás intentarlo de nuevo en unos 12 minutos."}`

## Decisiones
- **Orden:** se valida la solicitud primero (números, rifa, máximo por compra) y recién después se consume cupo; una petición inválida no gasta cupo. El antiabuso corre **antes** de bloquear boletos, así un abusador no toma locks.
- **Cupo por IP "agregar y luego contar":** dos peticiones simultáneas no se cuelan ambas por el mismo hueco. Si la reserva falla después (p. ej. 409 número ocupado) el cupo se devuelve (`liberar`). Al confirmar el pago también se libera.
- **Redis caído → se omite solo el tope por IP** (se registra un warning); el tope por teléfono sigue en BD. Mismo criterio que la caché de la grilla.
- **IP real:** `IpCliente` toma la entrada de `X-Forwarded-For` a `saltos-proxy` posiciones desde el **final** (la añade tu proxy), no la primera (la escribe el cliente y se falsifica). `forward-headers-strategy: framework` no cambia `getRemoteAddr()`, por eso no se usa directamente.
- **Teléfono +57:** la normalización venía del CP-03; ahora además se rechazan celulares con los 10 dígitos iguales (`3333333333`).
- Reservas con cuenta (`/api/compras`) no pasan por este antiabuso.

## Archivos
Nuevos: `AntiabusoService`, `AntiabusoProperties`, `IpCliente`, `ReservasPendientesExcedidasException`; tests `AntiabusoServiceTest` (9), `IpClienteTest` (6), `ApiExceptionHandlerTest` (1).
Modificados: `CompraService` (+ `AntiabusoService`, `reservarInvitado(request, ip)`, `crearReserva` dividido en `crearReserva` + `reservarBoletos`), `ReservaPublicaController`, `ApiExceptionHandler`, `CompraRepository`, `AlexRifasApplication`, `TelefonoColombia`, `application.yml`, y los tests `ReservaPublicaServiceTest` (+5) y `CompraServiceTest` (constructor).

## Pendiente / no verificado
- **`mvn test` no se ejecutó** (el entorno de Claude no tiene Maven Central). Solo verifiqué: (a) `IpCliente` compilado y ejecutado con sus casos; (b) el resto compila sin errores estructurales propios (los únicos errores de `javac` son por faltar Spring/Mockito). **Corre `mvn test` antes de dar el CP por bueno.**
- **Carrera residual en el tope por teléfono:** se cuenta en BD sin lock; N peticiones simultáneas del mismo teléfono podrían pasar de 3 a 3+N−1. Está acotado por el tope por IP y los locks de boletos. Si quieres cerrarlo del todo: `pg_advisory_xact_lock` por teléfono (solo Postgres, no H2) o llevar también el teléfono a Redis.
- **CGNAT:** las operadoras móviles comparten IP entre muchos usuarios; 5 por IP puede bloquear gente legítima en la misma red. Medir antes de producción; el valor es configurable.
- **`saltos-proxy` depende del despliegue:** 1 asume un solo proxy delante de la API. Con Vercel (rewrite) → Render pueden ser 2. Verificar en CP-20 enviando una petición con `X-Forwarded-For` falso y revisando qué IP ve la API.
- **CP-06 debe llamar `AntiabusoService.liberar(codigo)`** al cancelar/liberar una reserva desde admin. Si no, el cupo por IP se libera igual al vencer, pero antes de tiempo no.
- **Si Redis no es persistente**, un reinicio borra los contadores por IP (el de teléfono no se pierde).
- No se cambió el código de reserva de 32 caracteres (mencionado en CP-03): no está en el alcance del CP-05 del plan.
