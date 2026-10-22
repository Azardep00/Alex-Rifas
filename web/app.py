"""Sitio público de Alex-Rifas: catálogo de rifas usando la API Spring Boot."""
import os
from datetime import datetime

import requests
from flask import Flask, abort, jsonify, render_template

API_URL = os.environ.get("API_URL", "http://api:8080").rstrip("/")
TIMEOUT = float(os.environ.get("API_TIMEOUT", "4"))
app = Flask(__name__)


def api_get(path: str):
    """Devuelve JSON de la API; captura errores de red sin filtrar detalles internos."""
    response = requests.get(f"{API_URL}{path}", timeout=TIMEOUT)
    response.raise_for_status()
    return response.json()


def estado_api() -> str:
    try:
        return str(api_get("/actuator/health").get("status", "DESCONOCIDO"))
    except (requests.RequestException, ValueError, TypeError):
        return "CAIDA"


def cargar_rifas():
    try:
        data = api_get("/api/rifas?pagina=0&tamano=24")
        return data.get("contenido", []), None
    except (requests.RequestException, ValueError, TypeError):
        app.logger.warning("No se pudo consultar la lista pública de rifas en la API")
        return [], "En este momento no podemos consultar las rifas. Intenta de nuevo en unos minutos."


@app.context_processor
def inyectar_anio():
    return {"now_year": datetime.now().year}


@app.get("/")
def inicio():
    rifas, error = cargar_rifas()
    return render_template("index.html", rifas=rifas, error=error, estado_api=estado_api())


@app.get("/rifas/<codigo>")
def detalle_rifa(codigo: str):
    try:
        rifa = api_get(f"/api/rifas/{codigo}")
    except requests.HTTPError as exc:
        if exc.response is not None and exc.response.status_code == 404:
            abort(404)
        app.logger.warning("La API no pudo entregar el detalle de una rifa")
        return render_template("error.html", titulo="Rifa no disponible",
                               mensaje="No pudimos cargar esta rifa. Intenta de nuevo más tarde."), 503
    except (requests.RequestException, ValueError, TypeError):
        app.logger.warning("Error de conexión al consultar una rifa")
        return render_template("error.html", titulo="Servicio temporalmente no disponible",
                               mensaje="No pudimos conectarnos con el servicio de rifas."), 503
    return render_template("detalle.html", rifa=rifa, estado_api=estado_api())


@app.get("/salud")
def salud():
    return jsonify(estado="UP")


@app.errorhandler(404)
def no_encontrado(_error):
    return render_template("error.html", titulo="No encontramos esa página",
                           mensaje="La dirección puede ser incorrecta o el contenido ya no está disponible."), 404


@app.after_request
def cabeceras_seguras(respuesta):
    respuesta.headers["X-Content-Type-Options"] = "nosniff"
    respuesta.headers["X-Frame-Options"] = "DENY"
    respuesta.headers["Referrer-Policy"] = "strict-origin-when-cross-origin"
    respuesta.headers["Content-Security-Policy"] = (
        "default-src 'self'; style-src 'self'; img-src 'self' https: data:; "
        "base-uri 'self'; form-action 'self'; frame-ancestors 'none'"
    )
    return respuesta


if __name__ == "__main__":
    app.run(host="0.0.0.0", port=int(os.environ.get("PORT", "5000")))
