"""Carrega os indicadores da ANEEL a partir do MongoDB.

Os CSVs foram importados via `mongoimport --type csv --headerline`, porém os
arquivos usam `;` como separador de colunas e `,` como separador decimal. Como o
mongoimport assume `,` como delimitador padrão, cada documento ficou no formato:

    {
        "_id": ...,
        "Dat...;...;VlrIndiceEnviado": "<linha original até a primeira vírgula>",
        "field1": "<resto da linha, ou seja, a parte decimal>"
    }

Ou seja: a CHAVE do campo é o cabeçalho inteiro (separado por `;`) e o VALOR é a
linha original — quebrada na primeira (e única) vírgula, cuja parte direita foi
parar em `field1`. Reconstruímos a linha original concatenando
`valor + "," + field1` e então fazemos o split por `;` para recuperar as colunas.
"""

from typing import List, Optional

import pandas as pd

from app.config.settings import settings
from app.db.mongo import collection_exists, get_database
from app.utils.logger import logger

# Tamanho do lote lido do cursor do Mongo.
_BATCH_SIZE = 50_000


def _extract_header_and_value(doc: dict):
    """Dado um documento bruto, devolve (lista_de_campos_do_cabecalho, linha_original)."""
    header_key = None
    field1 = None
    for key, value in doc.items():
        if key == "_id":
            continue
        if key == "field1":
            field1 = value
            continue
        # A única outra chave é o cabeçalho inteiro.
        header_key = key
        raw_value = value

    if header_key is None:
        return None, None

    header = header_key.split(";")

    line = "" if raw_value is None else str(raw_value)
    if field1 is not None:
        # `field1` é a porção que veio depois da (única) vírgula da linha.
        line = f"{line},{field1}"

    return header, line.split(";")


def _to_records(
    collection_name: str,
    keep_indicadores: Optional[set],
    indicador_col: str,
    ano_col: str,
) -> List[dict]:
    """Lê uma collection, reconstrói as linhas e devolve uma lista de dicts.

    Filtra, já durante a leitura, por `keep_indicadores` (quando informado) e por
    `AnoIndice/AnoLimiteQualidade >= ANO_MINIMO`, reduzindo drasticamente o volume
    carregado em memória sem alterar o resultado final do pipeline.
    """
    db = get_database(settings.SOURCE_DB)
    coll = db[collection_name]
    total = coll.estimated_document_count()
    logger.info("Lendo '%s' (~%s documentos)...", collection_name, f"{total:,}")

    records: List[dict] = []
    header_cache: Optional[List[str]] = None
    read = 0

    cursor = coll.find({}, batch_size=_BATCH_SIZE)
    for doc in cursor:
        read += 1
        header, fields = _extract_header_and_value(doc)
        if header is None or fields is None:
            continue
        if header_cache is None:
            header_cache = header

        # Linhas mal-formadas (nº de campos != cabeçalho) são descartadas.
        if len(fields) != len(header):
            continue

        row = dict(zip(header, fields))

        if keep_indicadores is not None:
            sig = (row.get(indicador_col) or "").strip()
            if sig not in keep_indicadores:
                continue

        # Filtro de ano logo na leitura (equivale ao ANO_MINIMO do notebook).
        ano_raw = row.get(ano_col)
        try:
            if ano_raw is not None and ano_raw != "" and int(float(ano_raw)) < settings.ANO_MINIMO:
                continue
        except (TypeError, ValueError):
            pass

        records.append(row)

        if read % 1_000_000 == 0:
            logger.info("  ... %s linhas lidas, %s mantidas", f"{read:,}", f"{len(records):,}")

    logger.info(
        "'%s' concluída: %s linhas mantidas (de %s lidas).",
        collection_name,
        f"{len(records):,}",
        f"{read:,}",
    )
    return records


def load_apurados() -> pd.DataFrame:
    """Carrega os indicadores apurados (2010-2019 + 2020-2029 + atributos opcional).

    Mantém apenas os indicadores configurados (DEC/FEC), pois são os únicos usados
    no treinamento — equivalente ao filtro `SigIndicador == 'DEC'/'FEC'` do notebook.
    """
    keep = set(settings.INDICADORES)
    records: List[dict] = []

    for coll_name in (settings.COLLECTION_2010_2019, settings.COLLECTION_2020_2029):
        records.extend(
            _to_records(coll_name, keep, indicador_col="SigIndicador", ano_col="AnoIndice")
        )

    # A collection de atributos costuma NÃO existir (CSV só com cabeçalho).
    if collection_exists(settings.SOURCE_DB, settings.COLLECTION_ATRIBUTOS):
        records.extend(
            _to_records(
                settings.COLLECTION_ATRIBUTOS,
                keep,
                indicador_col="SigIndicador",
                ano_col="AnoIndice",
            )
        )
    else:
        logger.warning(
            "Collection de atributos '%s' não encontrada — ignorando "
            "(esperado: o CSV de atributos só possui cabeçalho).",
            settings.COLLECTION_ATRIBUTOS,
        )

    if not records:
        return pd.DataFrame()
    return pd.DataFrame.from_records(records)


def load_limite() -> pd.DataFrame:
    """Carrega os limites de qualidade (collection de limite)."""
    records = _to_records(
        settings.COLLECTION_LIMITE,
        keep_indicadores=None,
        indicador_col="SigIndicador",
        ano_col="AnoLimiteQualidade",
    )
    if not records:
        return pd.DataFrame()
    return pd.DataFrame.from_records(records)
