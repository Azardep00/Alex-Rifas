"""Procesador de resultados de Alex-Rifas (contenedor Python, sin puertos).

Escucha una cola en Redis. Cuando la API cierra un sorteo publica ahí un JSON; este worker:
  1. calcula el resumen (porcentaje vendido, etc.),
  2. lo guarda en MongoDB (colección "resultados"),
  3. escribe un reporte .json en el volumen compartido /reportes, que lee el visor Node.js.

Formato del mensaje (lo produce la API desde la Tarea 5):
  {"rifa_codigo": "AB12CD", "titulo": "...", "cantidad_boletos": 100, "boletos_vendidos": 80,
   "ganadores": [{"premio": "Moto", "numero": 42}]}
"""
import json
import logging
import os
import re
import time
from datetime import datetime, timezone
from pathlib import Path

import redis
from pymongo import MongoClient

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
log = logging.getLogger("procesador")

COLA = os.environ.get("COLA_RESULTADOS", "cola:resultados")
REPORTES_DIR = Path(os.environ.get("REPORTES_DIR", "/reportes"))
LATIDO = Path("/tmp/latido")  # el healthcheck del contenedor revisa que este archivo se actualice
CODIGO_VALIDO = re.compile(r"^[A-Za-z0-9_-]{1,32}$")


def procesar(datos: dict) -> dict:
    codigo = str(datos.get("rifa_codigo", ""))
    if not CODIGO_VALIDO.match(codigo):
        raise ValueError("rifa_codigo inválido")
    total = int(datos.get("cantidad_boletos", 0))
    vendidos = int(datos.get("boletos_vendidos", 0))
    if total <= 0 or not 0 <= vendidos <= total:
        raise ValueError("cantidades inconsistentes")
    return {
        "rifa_codigo": codigo,
        "titulo": str(datos.get("titulo", ""))[:120],
        "cantidad_boletos": total,
        "boletos_vendidos": vendidos,
        "porcentaje_vendido": round(vendidos * 100 / total, 1),
        "ganadores": [
            {"premio": str(g.get("premio", ""))[:120], "numero": int(g.get("numero", 0))}
            for g in datos.get("ganadores", [])
        ],
        "procesado_en": datetime.now(timezone.utc).isoformat(),
    }


def guardar(resultado: dict, coleccion) -> None:
    coleccion.replace_one({"rifa_codigo": resultado["rifa_codigo"]}, resultado, upsert=True)
    REPORTES_DIR.mkdir(parents=True, exist_ok=True)
    destino = REPORTES_DIR / f"{resultado['rifa_codigo']}.json"
    temporal = destino.with_suffix(".tmp")
    temporal.write_text(json.dumps(resultado, ensure_ascii=False, indent=2), encoding="utf-8")
    temporal.replace(destino)  # escritura atómica: el visor nunca lee un archivo a medias


def main() -> None:
    cola = redis.Redis(
        host=os.environ.get("REDIS_HOST", "redis"),
        port=int(os.environ.get("REDIS_PORT", "6379")),
        password=os.environ.get("REDIS_PASSWORD") or None,
        decode_responses=True,
    )
    mongo = MongoClient(os.environ["MONGO_URL"], serverSelectionTimeoutMS=5000)
    coleccion = mongo[os.environ.get("MONGO_DB", "alexrifas")]["resultados"]

    cola.ping()
    mongo.admin.command("ping")
    log.info("Conectado a Redis y MongoDB. Esperando mensajes en '%s'...", COLA)

    while True:
        LATIDO.touch()
        item = cola.blpop(COLA, timeout=5)
        if item is None:
            continue
        _, crudo = item
        try:
            resultado = procesar(json.loads(crudo))
            guardar(resultado, coleccion)
            log.info("Resultado procesado: rifa %s", resultado["rifa_codigo"])
        except (ValueError, TypeError, json.JSONDecodeError) as error:
            log.warning("Mensaje descartado (%s): %.200s", error, crudo)


if __name__ == "__main__":
    while True:
        try:
            main()
        except (redis.RedisError, Exception) as error:  # reintenta si Redis/Mongo aún no están listos
            log.error("Fallo en el procesador: %s. Reintentando en 5 s...", error)
            time.sleep(5)
