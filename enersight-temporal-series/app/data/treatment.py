"""Limpeza e consolidação dos indicadores (porte fiel do notebook).

Reproduz as etapas das células de "Limpeza e consolidação dos indicadores" e
"Preparação da série" do notebook AnellTratado_PROPHET_CSV.ipynb.
"""

import numpy as np
import pandas as pd

from app.config.settings import settings
from app.utils.logger import logger

# Colunas numéricas que precisam de coerção.
_COLS_FIX = [
    "NumCNPJ",
    "IdeConjUndConsumidoras",
    "AnoIndice",
    "NumPeriodoIndice",
    "AnoLimiteQualidade",
]

_COMMON_COLS = [
    "IdeConjUndConsumidoras",
    "SigIndicador",
    "AnoIndice",
    "NumPeriodoIndice",
    "VlrIndiceEnviado",
    "TipoRegistro",
]


def clean_df(df: pd.DataFrame, val_col: str, date_col: str = "DatGeracaoConjuntoDados") -> pd.DataFrame:
    """Normaliza tipos: datas, valor (vírgula→ponto), inteiros e SigAgente."""
    if df.empty:
        return df
    df = df.copy()

    if date_col in df.columns:
        df[date_col] = pd.to_datetime(df[date_col], errors="coerce")

    if val_col in df.columns:
        df[val_col] = df[val_col].astype(str).str.replace(",", ".", regex=False)
        df[val_col] = pd.to_numeric(df[val_col], errors="coerce")

    for col in _COLS_FIX:
        if col in df.columns:
            df[col] = pd.to_numeric(df[col], errors="coerce")

    if "SigAgente" in df.columns:
        df["SigAgente"] = df["SigAgente"].astype(str).str.strip().str.upper()

    return df


def build_indicadores(df_apurados_raw: pd.DataFrame, df_limite_raw: pd.DataFrame) -> pd.DataFrame:
    """Consolida apurados + limites no DataFrame `df_indicadores` do notebook."""
    # --- Apurados ---
    df_apurados = clean_df(df_apurados_raw, "VlrIndiceEnviado")
    if "AnoIndice" in df_apurados.columns:
        df_apurados = df_apurados[df_apurados["AnoIndice"] >= settings.ANO_MINIMO].copy()
    df_apurados["TipoRegistro"] = "Apurado"

    # --- Limites: vira DEC e FEC ---
    df_limit_processed = clean_df(df_limite_raw, "VlrLimite")
    if "AnoLimiteQualidade" in df_limit_processed.columns:
        df_limit_processed = df_limit_processed[
            df_limit_processed["AnoLimiteQualidade"] >= settings.ANO_MINIMO
        ].copy()

    df_limit_processed = df_limit_processed.rename(
        columns={"AnoLimiteQualidade": "AnoIndice", "VlrLimite": "VlrIndiceEnviado"}
    )
    df_limit_processed["TipoRegistro"] = "Limite"

    limites = []
    for indicador in settings.INDICADORES:
        tmp = df_limit_processed.copy()
        tmp["SigIndicador"] = indicador
        limites.append(tmp)
    df_limit = (
        pd.concat(limites, ignore_index=True) if limites else df_limit_processed.iloc[0:0]
    )

    # --- Consolidação ---
    df_indicadores = pd.concat(
        [
            df_apurados[[c for c in _COMMON_COLS if c in df_apurados.columns]],
            df_limit[[c for c in _COMMON_COLS if c in df_limit.columns]],
        ],
        ignore_index=True,
    )

    df_indicadores = df_indicadores.dropna(subset=["IdeConjUndConsumidoras", "AnoIndice"])
    df_indicadores["IdeConjUndConsumidoras"] = df_indicadores["IdeConjUndConsumidoras"].astype(int)
    df_indicadores["AnoIndice"] = df_indicadores["AnoIndice"].astype(int)
    df_indicadores["NumPeriodoIndice_key"] = (
        pd.to_numeric(df_indicadores["NumPeriodoIndice"], errors="coerce").fillna(0).astype(int)
    )

    logger.info("df_indicadores consolidado: %s linhas", f"{len(df_indicadores):,}")
    return df_indicadores


def build_series(df_indicadores: pd.DataFrame, indicador: str) -> pd.DataFrame:
    """Prepara a série temporal de um indicador (DEC ou FEC).

    Cria a coluna `data` (AnoIndice + NumPeriodoIndice) e mantém apenas as
    unidades que possuem ponto histórico em DATA_MAX, ordenadas por unidade/data.
    """
    df = df_indicadores[df_indicadores["SigIndicador"] == indicador].copy()
    df = df.dropna(subset=["NumPeriodoIndice"])

    df["data"] = pd.to_datetime(
        df["AnoIndice"].astype(int).astype(str)
        + "-"
        + df["NumPeriodoIndice_key"].astype(int).astype(str).str.zfill(2)
        + "-01"
    )

    ids_validos = df.loc[df["data"] == settings.DATA_MAX, "IdeConjUndConsumidoras"].unique()

    serie = df[df["IdeConjUndConsumidoras"].isin(ids_validos)].sort_values(
        ["IdeConjUndConsumidoras", "data"]
    )
    logger.info("Indicador %s: %s unidades válidas em %s", indicador, len(ids_validos), settings.DATA_MAX)
    return serie
