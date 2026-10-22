# Infraestructura y configuración local

## Crear `.env` en Windows

Desde la raíz del repositorio, en PowerShell:

```powershell
.\scripts\preparar-entorno.ps1
```

El script crea contraseñas aleatorias y un secreto JWT; si ya existe `.env`, se niega a sobrescribirlo. La alternativa manual es `Copy-Item .env.example .env`, pero en ese caso debes cambiar manualmente todos los valores de ejemplo antes de levantar los contenedores.

En Git Bash/Linux:

```bash
./scripts/preparar-entorno.sh
```

**Nunca subas `.env` a GitHub ni pegues sus secretos en capturas o videos.** El archivo está ignorado por Git. Para una demostración, muestra únicamente las variables y oculta los valores.

## Arranque y diagnóstico

```powershell
docker compose config
docker compose up -d --build
docker compose ps
docker compose logs --tail 100 api
```

`docker compose config` debe terminar sin errores de interpolación. Espera a que PostgreSQL, Redis y MongoDB estén `healthy`; la API puede tardar hasta un minuto en completar la migración Flyway y arrancar.

Para detener sin borrar información: `docker compose down`. Solo durante desarrollo y aceptando perder todos los datos se debe usar `docker compose down -v`.

## Redes y exposición

- `frontend`: entrada de navegador, web y visor.
- `backend`: proxy y servicios que deben acceder a la API. El proxy pertenece a esta red para poder enrutar `/api/`.
- `data`: PostgreSQL, Redis, MongoDB, API y procesador. Está declarada `internal: true`; solo la API y el procesador se conectan a las bases.
- Únicamente `proxy` publica un puerto en el host. PostgreSQL, Redis, MongoDB y la API no publican puertos.

El proxy aparece tanto en `frontend` como en `backend` deliberadamente: si se quita de `backend`, Nginx no podrá resolver/alcanzar `api:8080` para la ruta `/api/`.
