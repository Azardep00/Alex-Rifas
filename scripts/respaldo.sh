#!/usr/bin/env bash
# Respaldo cifrado de la base de datos (CP-21).
# Uso:  PG_URL='postgresql://usuario:clave@host:5432/bd?sslmode=require' BACKUP_PASSPHRASE='...' ./scripts/respaldo.sh [carpeta]
# Genera  respaldo-AAAAMMDD-HHMMSS.sql.gz.gpg  (formato libpq, NO el JDBC de la API).
set -euo pipefail
: "${PG_URL:?Define PG_URL (postgresql://...)}"
: "${BACKUP_PASSPHRASE:?Define BACKUP_PASSPHRASE}"
destino="${1:-.}"
archivo="$destino/respaldo-$(date -u +%Y%m%d-%H%M%S).sql.gz.gpg"
mkdir -p "$destino"
pg_dump --no-owner --no-privileges --format=plain "$PG_URL" \
  | gzip -9 \
  | gpg --batch --yes --pinentry-mode loopback --symmetric --cipher-algo AES256 --passphrase "$BACKUP_PASSPHRASE" -o "$archivo"
echo "Respaldo creado: $archivo ($(du -h "$archivo" | cut -f1))"
