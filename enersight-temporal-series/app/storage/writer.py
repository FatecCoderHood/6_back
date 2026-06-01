"""Persistência dos dados tratados e das previsões no MongoDB (base de destino)."""

from typing import Iterable

import numpy as np
import pandas as pd

from app.config.settings import settings
from app.db.mongo import get_database
from app.utils.logger import logger

_INSERT_BATCH = 50_000


def _records(df: pd.DataFrame) -> Iterable[dict]:
    """Converte um DataFrame em dicts prontos para o Mongo (datas → datetime, NaN → None)."""
    df = df.replace({np.nan: None})
    return df.to_dict("records")


def _insert_in_batches(collection, df: pd.DataFrame) -> int:
    total = 0
    records = _records(df)
    for start in range(0, len(records), _INSERT_BATCH):
        batch = records[start : start + _INSERT_BATCH]
        if batch:
            collection.insert_many(batch, ordered=False)
            total += len(batch)
    return total


def reset_collection(db_name: str, collection_name: str):
    """Remove a collection de destino para uma execução idempotente."""
    db = get_database(db_name)
    db[collection_name].drop()
    return db[collection_name]


def save_treated(df_indicadores: pd.DataFrame) -> int:
    """Grava o conjunto de indicadores tratados/consolidados."""
    coll = reset_collection(settings.OUTPUT_DB, settings.TREATED_COLLECTION)
    n = _insert_in_batches(coll, df_indicadores)
    logger.info(
        "Dados tratados salvos: %s docs em %s.%s",
        f"{n:,}",
        settings.OUTPUT_DB,
        settings.TREATED_COLLECTION,
    )
    return n


def get_forecast_collection():
    """Recria (zera) a collection de previsões e devolve a referência."""
    return reset_collection(settings.OUTPUT_DB, settings.FORECAST_COLLECTION)


def append_forecasts(collection, df_prev: pd.DataFrame) -> int:
    """Insere um lote de previsões (datas como datetime nativo do Mongo)."""
    if df_prev.empty:
        return 0
    df_prev = df_prev.copy()
    df_prev["data"] = pd.to_datetime(df_prev["data"])
    records = _records(df_prev)
    collection.insert_many(records, ordered=False)
    return len(records)
