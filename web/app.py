"""Sitio web público de Alex-Rifas (contenedor Python).

Es el "BFF" del sistema: consume la API Spring Boot por la red interna (API_URL) y entrega
HTML al navegador. Las pantallas de rifas se agregan en la Tarea 6.
"""
import os

import requests
from flask import Flask, jsonify, render_template

API_URL = os.environ.get("API_URL", "http://api:8080")

app = Flask(__name__)


def estado_api() -> str:
    """Consulta el health-check de la API; nunca lanza excepción para no tumbar la página."""
    try:
        respuesta = requests.get(f"{API_URL}/actuator/health", timeout=3)
        return respuesta.json().get("status", "DESCONOCIDO")
    except (requests.RequestException, ValueError):
        return "CAIDA"


@app.get("/")
def inicio():
    return render_template("index.html", estado_api=estado_api())


@app.get("/salud")
def salud():
    return jsonify(estado="UP")


@app.after_request
def cabeceras_seguras(respuesta):
    respuesta.headers["X-Content-Type-Options"] = "nosniff"
    respuesta.headers["X-Frame-Options"] = "DENY"
    respuesta.headers["Content-Security-Policy"] = "default-src 'self'; style-src 'self' 'unsafe-inline'"
    return respuesta
