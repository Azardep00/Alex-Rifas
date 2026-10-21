# Alex-Rifas

Sistema de rifas en línea. Proyecto 03 de *Infraestructura y Seguridad Computacional* (Universidad de Ibagué):
**aplicación multicontenedor con Docker Compose**.

## Arquitectura

| Servicio | Imagen / tecnología | Rol | Redes | Volumen |
|---|---|---|---|---|
| `proxy` | nginx (unprivileged) | Única entrada pública (puerto 80) | frontend | — |
| `web` | Python 3.12 + Flask + gunicorn | Sitio público | frontend, backend | — |
| `visor` | Node.js 22 + Express | Visualización de resultados | frontend, backend | `reportes` (solo lectura) |
| `api` | Spring Boot 3.5 (Java 21) | API REST, lógica de rifas | backend, data | — |
| `procesador` | Python 3.12 (worker) | Procesa resultados de sorteos desde una cola | data | `reportes` |
| `postgres` | PostgreSQL 16 | Datos transaccionales (migraciones Flyway) | data | `pg_data` |
| `redis` | Redis 7 | Cola de trabajos, caché, límites | data | `redis_data` |
| `mongo` | MongoDB 7 | Resultados procesados y bitácora | data | `mongo_data` |

Redes: `frontend` (lo que ve el usuario), `backend` (tráfico hacia la API) y `data`
(`internal: true`: las bases de datos no tienen salida a internet). Solo el proxy publica un puerto.

## Cómo ejecutarlo

Requisitos: Docker y Docker Compose v2.

```bash
cp .env.example .env        # edita TODAS las contraseñas y el JWT_SECRET
docker compose up -d --build
docker compose ps           # todos deben quedar "healthy"
```

- Sitio: http://localhost  ·  Visor de resultados: http://localhost/visor/
- Ver logs: `docker compose logs -f api`
- Detener: `docker compose down` (con `-v` también borra los datos)

### Probar el procesador de resultados

Con el stack arriba, publica un resultado en la cola y míralo aparecer en el visor:

```bash
docker compose exec redis sh -c 'redis-cli -a "$REDIS_PASSWORD" --no-auth-warning RPUSH cola:resultados \
  "{\"rifa_codigo\":\"AB12CD\",\"titulo\":\"Rifa de prueba\",\"cantidad_boletos\":200,\"boletos_vendidos\":150,\"ganadores\":[{\"premio\":\"Moto\",\"numero\":42}]}"'
```

Luego abre http://localhost/visor/.

## Publicar las imágenes en Docker Hub

```bash
docker login
export DOCKERHUB_USER=tu_usuario TAG=1.0.0     # o ponlos en .env
docker compose build
docker compose push api web visor procesador
```

El `docker-compose.yml` usa `image: ${DOCKERHUB_USER}/alex-rifas-<servicio>:${TAG}` junto con `build:`,
así que el mismo archivo construye en local o descarga las imágenes publicadas (`docker compose pull`).

## Seguridad aplicada

- Solo el proxy expone un puerto; la API y las bases de datos no son accesibles desde fuera.
- Red `data` interna, sin salida a internet.
- Contenedores propios con usuario sin privilegios y `no-new-privileges`.
- Secretos en `.env` (fuera de Git) y variables obligatorias: el stack no arranca si faltan.
- Healthchecks y `depends_on: service_healthy` para un orden de arranque correcto.
- Cabeceras de seguridad en proxy, web y visor.

## Desarrollo del backend sin Docker

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev     # H2 en memoria + Swagger en /swagger-ui.html
```
