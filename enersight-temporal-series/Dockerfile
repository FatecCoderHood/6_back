FROM python:3.12-slim

# Logs sem buffer e sem .pyc
ENV PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1

WORKDIR /app

# Dependências de sistema para compilar o Prophet/cmdstan (Stan backend).
RUN apt-get update && apt-get install -y \
    build-essential \
    gcc \
    g++ \
    && rm -rf /var/lib/apt/lists/*

# Instala dependências primeiro (melhor cache de build).
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Código da aplicação.
COPY app ./app

# Roda o pipeline completo: Mongo → tratamento → Prophet → Mongo.
CMD ["python", "-m", "app"]
