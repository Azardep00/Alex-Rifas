"""Worker de resultados: Redis -> validación -> MongoDB -> volumen compartido para el visor."""
import json
import logging
import os
import re
import time
from datetime import datetime, timezone
from pathlib import Path

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
log = logging.getLogger("procesador")
COLA = os.environ.get("COLA_RESULTADOS", "cola:resultados")
COLA_PROCESANDO = os.environ.get("COLA_PROCESANDO", "cola:resultados:procesando")
COLA_ERRORES = os.environ.get("COLA_ERRORES", "cola:resultados:errores")
REPORTES_DIR = Path(os.environ.get("REPORTES_DIR", "/reportes"))
LATIDO = Path("/tmp/latido")
CODIGO_VALIDO = re.compile(r"^[A-Za-z0-9_-]{1,32}$")


def procesar(datos: dict) -> dict:
    if not isinstance(datos, dict):
        raise ValueError("el mensaje debe ser un objeto JSON")
    codigo = str(datos.get("rifa_codigo", ""))
    if not CODIGO_VALIDO.fullmatch(codigo):
        raise ValueError("rifa_codigo inválido")
    total = int(datos.get("cantidad_boletos", 0))
    vendidos = int(datos.get("boletos_vendidos", 0))
    if total <= 0 or not 0 <= vendidos <= total:
        raise ValueError("cantidades inconsistentes")
    ganadores_entrada = datos.get("ganadores", [])
    if not isinstance(ganadores_entrada, list):
        raise ValueError("ganadores debe ser una lista")
    ganadores = []
    for ganador in ganadores_entrada:
        if not isinstance(ganador, dict):
            raise ValueError("cada ganador debe ser un objeto")
        numero = int(ganador.get("numero", -1))
        if numero < 0 or numero >= total:
            raise ValueError("número de ganador fuera del rango de la rifa")
        ganadores.append({"premio": str(ganador.get("premio", ""))[:120], "numero": numero})
    return {
        "rifa_codigo": codigo,
        "titulo": str(datos.get("titulo", ""))[:120],
        "cantidad_boletos": total,
        "boletos_vendidos": vendidos,
        "porcentaje_vendido": round(vendidos * 100 / total, 1),
        "ganadores": ganadores,
        "procesado_en": datetime.now(timezone.utc).isoformat(),
    }


def guardar(resultado: dict, coleccion) -> None:
    # La clave única por rifa hace que reprocesar un mensaje sea idempotente.
    coleccion.replace_one({"rifa_codigo": resultado["rifa_codigo"]}, resultado, upsert=True)
    REPORTES_DIR.mkdir(parents=True, exist_ok=True)
    destino = REPORTES_DIR / f"{resultado['rifa_codigo']}.json"
    temporal = destino.with_suffix(".tmp")
    temporal.write_text(json.dumps(resultado, ensure_ascii=False, indent=2), encoding="utf-8")
    temporal.replace(destino)


def crear_conexion_redis():
    import redis

    cliente = redis.Redis(
        host=os.environ.get("REDIS_HOST", "redis"),
        port=int(os.environ.get("REDIS_PORT", "6379")),
        password=os.environ.get("REDIS_PASSWORD") or None,
        decode_responses=True,
        socket_connect_timeout=5,
        socket_timeout=10,
        health_check_interval=30,
    )
    cliente.ping()
    return cliente


def recuperar_pendientes(cola, limite: int = 1000) -> None:
    """Devuelve a la cola de entrada mensajes que quedaron en procesamiento por una caída."""
    cantidad = min(cola.llen(COLA_PROCESANDO), limite)
    for _ in range(cantidad):
        crudo = cola.rpop(COLA_PROCESANDO)
        if crudo is None:
            break
        cola.lpush(COLA, crudo)
    if cantidad:
        log.warning("Se reencolaron %s mensaje(s) que quedaron en procesamiento", cantidad)


def main() -> None:
    from pymongo import MongoClient

    cola = crear_conexion_redis()
    mongo = MongoClient(os.environ["MONGO_URL"], serverSelectionTimeoutMS=5000)
    coleccion = mongo[os.environ.get("MONGO_DB", "alexrifas")]["resultados"]
    coleccion.create_index("rifa_codigo", unique=True)
    mongo.admin.command("ping")
    recuperar_pendientes(cola)
    REPORTES_DIR.mkdir(parents=True, exist_ok=True)
    log.info("Conectado a Redis y MongoDB. Escuchando '%s'", COLA)

    while True:
        LATIDO.touch()
        # BLMOVE mueve el mensaje a una lista de trabajo en una operación atómica.
        # Si el proceso cae, se recupera en el siguiente arranque.
        crudo = cola.execute_command("BLMOVE", COLA, COLA_PROCESANDO, "LEFT", "RIGHT", 5)
        if crudo is None:
            continue
        try:
            resultado = procesar(json.loads(crudo))
            guardar(resultado, coleccion)
            cola.lrem(COLA_PROCESANDO, 1, crudo)
            log.info("Resultado procesado: rifa %s", resultado["rifa_codigo"])
        except (ValueError, TypeError, json.JSONDecodeError) as error:
            cola.rpush(COLA_ERRORES, json.dumps({"mensaje": crudo, "error": str(error)[:300]}))
            cola.lrem(COLA_PROCESANDO, 1, crudo)
            log.warning("Mensaje inválido enviado a cola de errores: %s", error)
        except Exception:
            # Error transitorio al escribir en Mongo/volumen: dejarlo en procesamiento para reintentar.
            log.exception("No se pudo persistir el resultado. El mensaje se reintentará en el próximo reinicio.")
            raise


if __name__ == "__main__":
    while True:
        try:
            main()
        except Exception as error:  # reintenta si Redis o Mongo aún no están listos
            log.error("Fallo en el procesador: %s. Reintentando en 5 s...", error)
            time.sleep(5)
