from sqlalchemy import create_engine
from app.config.settings import settings

def get_engine():
    url = (
        f"postgresql://{settings.DB_USER}:"
        f"{settings.DB_PASSWORD}@{settings.DB_HOST}/"
        f"{settings.DB_NAME}"
    )
    return create_engine(url)