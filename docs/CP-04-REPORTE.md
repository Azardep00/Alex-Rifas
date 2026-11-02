# CP-04 · Grilla compacta de boletos

`GET /api/rifas/{codigo}/boletos` (público) →
`{ codigoRifa, cantidad, digitos, estados, libres, reservados, vendidos, generadaEn }`

`estados` = un carácter por número (posición = número): `L` libre · `R` reservado · `V` vendido.
Una reserva vencida cuenta como libre aunque el job aún no la haya expirado.
Solo se consultan los boletos no disponibles; el resto se rellena como libre.

Caché Redis `grilla:{codigo}` con TTL 3 s; si Redis falla, responde desde la BD.
Visible solo en PUBLICADA/CERRADA/SORTEADA (igual que el detalle).

## No verificado
- `mvn test` no ejecutado (sin Maven Central). Tests: `GrillaServiceTest` (2).
- Criterio "100 y 10 000 números < 200 ms" **sin medir**: falta probar con Postgres+Redis reales.
- La caché puede mostrar un número recién reservado como libre hasta 3 s; la reserva real sigue protegida por el lock.
