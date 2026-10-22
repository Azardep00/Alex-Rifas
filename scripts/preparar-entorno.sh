#!/usr/bin/env sh
set -eu
ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
ENV_FILE="$ROOT/.env"
if [ -e "$ENV_FILE" ]; then
  echo "Ya existe $ENV_FILE; no se sobrescribió. Elimínalo manualmente si deseas regenerarlo." >&2
  exit 1
fi
new_secret() { openssl rand -base64 "$1" | tr '+/' '-_' | tr -d '=\n'; }
printf 'Usuario de Docker Hub (Enter para usar local): '
read -r DOCKERHUB_USER || true
DOCKERHUB_USER=${DOCKERHUB_USER:-local}
{
  echo '# Generado localmente por scripts/preparar-entorno.sh. No subir a Git.'
  printf 'DOCKERHUB_USER=%s\n' "$DOCKERHUB_USER"
  echo 'TAG=latest'
  echo 'HTTP_PORT=80'
  echo 'POSTGRES_DB=alexrifas'
  echo 'POSTGRES_USER=alexrifas'
  printf 'POSTGRES_PASSWORD=%s\n' "$(new_secret 30)"
  printf 'REDIS_PASSWORD=%s\n' "$(new_secret 30)"
  echo 'MONGO_USER=alexrifas'
  printf 'MONGO_PASSWORD=%s\n' "$(new_secret 30)"
  printf 'JWT_SECRET=%s\n' "$(new_secret 48)"
  echo 'ADMIN_CORREO=admin@alexrifas.local'
  printf 'ADMIN_CONTRASENA=%s\n' "$(new_secret 30)"
} > "$ENV_FILE"
chmod 600 "$ENV_FILE" 2>/dev/null || true
echo "Creado $ENV_FILE. Protege el archivo y no lo subas a GitHub."
