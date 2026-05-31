from functools import lru_cache

from pymongo import MongoClient

from app.config.settings import settings


@lru_cache(maxsize=1)
def get_client() -> MongoClient:
    """Cliente MongoDB reutilizável (singleton por processo)."""
    return MongoClient(settings.mongo_uri)


def get_database(name: str):
    return get_client()[name]


def collection_exists(db_name: str, collection_name: str) -> bool:
    return collection_name in get_database(db_name).list_collection_names()
