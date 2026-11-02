# CP-03 · Reserva pública (invitado)

## Endpoints (sin autenticación)
| Método | Ruta | Respuesta |
|---|---|---|
| POST | `/api/publico/reservas` | 201 `ReservaPublicaResponse` |
| GET | `/api/publico/reservas/{codigo}?telefono=` | 200 · 404 si código+teléfono no coinciden |

`POST` body: `{ "codigoRifa": "ABC123", "nombre": "Ana Gómez", "telefono": "300 123 4567", "numeros": [4, 7] }`

Errores: 400 (teléfono/nombre/números inválidos, supera máximo por compra), 404 (rifa), 409 (rifa no publicada, ventas cerradas, número ocupado).

## Decisiones
- Reutiliza el mismo núcleo que la reserva con cuenta (`crearReserva`): mismo bloqueo pesimista `findByRifaIdAndNumeroInForUpdate`, misma liberación de reservas vencidas.
- Vigencia = `rifa.reservaMinutos` (hoy 15 por defecto, sin cambiar el comportamiento previo), acotada a `cierre_ventas_en`.
- Teléfono normalizado a `+57XXXXXXXXXX` (solo celulares que empiezan por 3). Esto adelanta la validación de formato del CP-05; el **tope por teléfono/IP sigue pendiente en CP-05**.
- La consulta exige código **y** teléfono; ante cualquier desajuste responde 404 sin distinguir "no existe" de "teléfono distinto". La respuesta pública enmascara el teléfono (`+57 300 ••• 4567`) y no expone ids internos.
- Las compras con cuenta (`/api/compras/*`) no cambian.

## Pendiente / no verificado
- `mvn test` **no se ejecutó** (sin Maven Central en el entorno de Claude). Tests nuevos: `ReservaPublicaServiceTest` (8) y `TelefonoColombiaTest` (2), unitarios con Mockito.
- Falta un test de **concurrencia real** (dos hilos, mismo número) contra Postgres: los unit tests solo verifican la lógica, no el lock. Propuesto como CP-03b junto a Testcontainers.
- El código de reserva es un UUID de 32 caracteres (heredado); es poco cómodo para digitarlo. La SPA debe ofrecer copiar/enlace. Valorar un código corto en CP-05.
