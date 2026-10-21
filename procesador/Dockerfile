FROM python:3.12-slim
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1
WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY main.py .
# Usuario sin privilegios. /reportes lo crea Docker al montar el volumen, así que lo preparamos con permisos.
RUN useradd --system --no-create-home --shell /usr/sbin/nologin rifas \
 && mkdir /reportes && chown rifas:rifas /reportes
USER rifas
CMD ["python", "main.py"]
