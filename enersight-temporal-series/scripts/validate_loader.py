"""Validação rápida do loader contra o Mongo real (não treina nada).

Lê uma amostra pequena de cada collection, reconstrói as linhas e mostra como
ficaram as colunas e os valores numéricos — para conferir o tratamento do
delimitador antes de rodar o pipeline completo.
"""

import pandas as pd

from app.config.settings import settings
from app.data.loader import _extract_header_and_value
from app.data.treatment import clean_df
from app.db.mongo import collection_exists, get_database

pd.set_option("display.width", 200)
pd.set_option("display.max_columns", 20)

db = get_database(settings.SOURCE_DB)

for coll_name in (
    settings.COLLECTION_2010_2019,
    settings.COLLECTION_2020_2029,
    settings.COLLECTION_LIMITE,
):
    print(f"\n=== {coll_name} ===")
    rows = []
    for doc in db[coll_name].find({}, limit=5):
        header, fields = _extract_header_and_value(doc)
        print(f"  cols={len(header)} fields={len(fields)} ok={len(header) == len(fields)}")
        rows.append(dict(zip(header, fields)))
    df = pd.DataFrame(rows)
    val_col = "VlrLimite" if "limite" in coll_name else "VlrIndiceEnviado"
    df = clean_df(df, val_col)
    print(df.to_string())

print(f"\nAtributos existe? {collection_exists(settings.SOURCE_DB, settings.COLLECTION_ATRIBUTOS)}")
