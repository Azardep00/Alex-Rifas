# Reparto individual de contribuciones

| Integrante | Rama | Carpetas/archivos principales | Evidencia para el PR |
|---|---|---|---|
| Sebas | `infra/secrets-compose` | `docker-compose.yml`, `.env.example`, `scripts/`, `docs/INFRAESTRUCTURA.md`, configuración YAML del backend | `docker compose config`, servicios/redes/volúmenes y creación local de `.env` |
| Kevin | `feat/compras-sorteos` | servicios, controladores, entidades y repositorios Java; `pom.xml`; pruebas Java | explicar reserva con bloqueo, expiración, confirmación manual, sorteo y Redis |
| Dani | `feat/web-procesador` | `web/` y `procesador/` | catálogo de rifas, detalle, pruebas del procesador y flujo Redis → Mongo/reportes |
| Johan | `feat/visor-docs` | `visor/` y `docs/PUBLICACION-Y-VIDEO.md`, plantilla Moodle | visor de resultados, tests Node, Docker Hub y guion de sustentación |

Las carpetas principales son diferentes para minimizar conflictos. Los cuatro deben comenzar desde la misma versión de `main` actualizada, abrir un PR separado y no integrar cambios de otra persona a mano sin coordinarlo.
