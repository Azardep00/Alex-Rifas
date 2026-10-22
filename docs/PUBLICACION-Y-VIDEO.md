# Guía de publicación y video — Proyecto 03

Esta guía está basada en el instructivo del curso: Compose correcto; al menos tres servicios conectados; tres volúmenes; puertos web y redes; publicación en GitHub y Docker Hub; y video donde participen todos los integrantes.

## 1. Integrar el trabajo del grupo

1. Cada persona parte de `main` actualizado y sube su propia rama.
2. Cada persona abre un Pull Request hacia `main` y explica qué archivos añadió.
3. Revisen los cuatro PR juntos antes de aprobarlos. Integren primero infraestructura, luego backend, web/procesador y finalmente visor/documentación.
4. Una vez integrados los cuatro PR, una persona hace una prueba limpia del stack y todos revisan el resultado.

No deben copiar el archivo `.env` a GitHub. En el video, oculten los valores de las contraseñas, JWT y tokens.

## 2. Publicar imágenes en Docker Hub

1. Cada imagen se construye con su propio Dockerfile: `api` desde la raíz, `web` desde `web/`, `visor` desde `visor/` y `procesador` desde `procesador/`.
2. En Docker Hub creen (o usen) repositorios públicos con nombres `alex-rifas-api`, `alex-rifas-web`, `alex-rifas-visor` y `alex-rifas-procesador`.
3. En la máquina que vaya a publicar, ejecuten:

```bash
docker login
```

4. Cambien en el archivo local `.env` `DOCKERHUB_USER` por el usuario real y `TAG` por una versión, por ejemplo `1.0.0`. No agreguen `.env` al repositorio.
5. Desde la raíz del proyecto:

```bash
docker compose build api web visor procesador
docker compose push api web visor procesador
```

6. Verifiquen en Docker Hub que las cuatro etiquetas aparezcan como públicas. Compartan el enlace al perfil o a cada repositorio de imagen.

**Entrega individual:** el instructivo también menciona la publicación por cada estudiante. Después de que los cuatro PR estén integrados en `main`, cada integrante debe hacer `git pull origin main`, configurar `DOCKERHUB_USER` con su propia cuenta y publicar las cuatro imágenes desde su máquina. Así el contenido publicado representa el proyecto integrado, no una rama incompleta.

> El build del backend usa Maven y Java 21 en una etapa de compilación. No necesitan instalar Maven en Windows para construir la API: Docker descarga la imagen Maven definida en el Dockerfile.

## 3. Prueba de aceptación antes de grabar

En un entorno de desarrollo sin información que deban conservar:

```powershell
.\scripts\preparar-entorno.ps1
docker compose config
docker compose up -d --build
docker compose ps
docker compose logs --tail 100 api procesador
```

Deben comprobar: sitio Python accesible por el puerto público; ruta `/visor/`; API saludable; PostgreSQL, Redis y MongoDB saludables; reserva protegida con JWT; expiración de reservas; confirmación manual hecha por admin; sorteo que publica un evento en Redis; procesador que guarda el JSON en Mongo y el volumen `reportes`; visor que muestra el resultado.

No usen `docker compose down -v` si necesitan conservar las pruebas o datos de los compañeros: elimina los volúmenes.

## 4. Guion de video recomendado (6–9 minutos)

- **Sebas (1.5–2 min):** mostrar `docker-compose.yml`, servicios, `depends_on`/healthchecks, redes, puertos, volúmenes y el archivo `.env` con los valores ocultos.
- **Kevin (2–2.5 min):** explicar los roles y JWT, reserva transaccional de boletos, expiración, confirmación manual de pago, sorteo y cola Redis. Aclarar que la integración real de Wompi aún no está implementada.
- **Dani (1.5–2 min):** navegar por la web Flask, explicar cómo consume la API, y mostrar cómo el worker Python lee la cola, valida datos y persiste en Mongo/reportes.
- **Johan (1.5–2 min):** mostrar el visor Node.js, ejecutar `docker compose build` y `docker compose push`, y enseñar en pantalla los repositorios públicos de Docker Hub y el repo GitHub.

Cada integrante debe hablar y aparecer o identificarse claramente. Eviten grabar solo una voz narrando todo el trabajo.

## 5. Archivos de texto para Moodle

El instructivo solicita enlaces y también menciona adjuntar el `docker-compose.yml`. Como recomendación conservadora, creen un TXT con:

- nombres de los cuatro integrantes;
- URL pública del repositorio GitHub;
- URL de Docker Hub (perfil y los repositorios de imágenes);
- versión/tag publicada;
- comando para ejecutar y puerto de entrada;
- explicación breve de los servicios, redes y volúmenes.

Adjunten el `docker-compose.yml` como archivo independiente si la tarea de Moodle permite cargar más de un archivo. Cada estudiante realiza su propio envío según el instructivo.
