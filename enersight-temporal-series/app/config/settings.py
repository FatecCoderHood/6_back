import os


class Settings:
    """Configuração do pipeline de séries temporais (ANEEL → Prophet → Mongo).

    Todos os valores podem ser sobrescritos por variáveis de ambiente, o que
    permite rodar tanto localmente (MONGO_HOST=localhost) quanto dentro do
    docker-compose (MONGO_HOST=enersight-mongo).
    """

    # --- Conexão MongoDB ---
    MONGO_HOST = os.getenv("MONGO_HOST", "localhost")
    MONGO_PORT = int(os.getenv("MONGO_PORT", "27017"))
    MONGO_USER = os.getenv("MONGO_USER", "app_user")
    MONGO_PASSWORD = os.getenv("MONGO_PASSWORD", "app_password")
    MONGO_AUTH_DB = os.getenv("MONGO_AUTH_DB", "admin")

    # --- Base/collections de origem (CSVs da ANEEL importados via mongoimport) ---
    SOURCE_DB = os.getenv("SOURCE_DB", "aneel")
    COLLECTION_2010_2019 = os.getenv(
        "COLLECTION_2010_2019", "indicadores_continuidade-2010-2019"
    )
    COLLECTION_2020_2029 = os.getenv(
        "COLLECTION_2020_2029", "indicadores_continuidade-2020-2029"
    )
    COLLECTION_LIMITE = os.getenv(
        "COLLECTION_LIMITE", "indicadores_continuidade-limite"
    )
    # A collection de "atributos" normalmente NÃO existe no Mongo: o CSV
    # correspondente só possui cabeçalho, então o mongoimport não o aceita.
    # O loader trata a ausência dela de forma transparente.
    COLLECTION_ATRIBUTOS = os.getenv(
        "COLLECTION_ATRIBUTOS", "indicadores_continuidade-atributos"
    )

    # --- Base/collections de destino (dados tratados + previsões) ---
    OUTPUT_DB = os.getenv("OUTPUT_DB", "aneel")
    TREATED_COLLECTION = os.getenv("TREATED_COLLECTION", "indicadores_tratados")
    FORECAST_COLLECTION = os.getenv("FORECAST_COLLECTION", "previsoes_prophet")

    # --- Parâmetros de tratamento ---
    ANO_MINIMO = int(os.getenv("ANO_MINIMO", "2015"))
    # Indicadores que serão treinados (mesmos do notebook).
    INDICADORES = [
        s.strip() for s in os.getenv("INDICADORES", "DEC,FEC").split(",") if s.strip()
    ]
    # Apenas unidades que possuem um ponto histórico nesta data entram no treino.
    DATA_MAX = os.getenv("DATA_MAX", "2026-02-01")

    # --- Parâmetros do Prophet ---
    HORIZONTE = int(os.getenv("HORIZONTE", "12"))      # meses a prever
    MIN_AMOSTRAS = int(os.getenv("MIN_AMOSTRAS", "24"))  # mínimo de pontos (2 anos)
    N_CHANGEPOINTS = int(os.getenv("N_CHANGEPOINTS", "10"))

    # Insere previsões no Mongo a cada N unidades processadas (checkpoint).
    CHECKPOINT = int(os.getenv("CHECKPOINT", "500"))

    @property
    def mongo_uri(self) -> str:
        return (
            f"mongodb://{self.MONGO_USER}:{self.MONGO_PASSWORD}"
            f"@{self.MONGO_HOST}:{self.MONGO_PORT}/"
            f"?authSource={self.MONGO_AUTH_DB}"
        )


settings = Settings()
