import os

class Settings:
    DB_HOST = os.getenv("DB_HOST", "enersight-db")
    DB_NAME = os.getenv("DB_NAME", "enersight_app")
    DB_USER = os.getenv("DB_USER", "app_user")
    DB_PASSWORD = os.getenv("DB_PASSWORD", "app_password")

    DATA_DIR = "/tmp/data"

settings = Settings()