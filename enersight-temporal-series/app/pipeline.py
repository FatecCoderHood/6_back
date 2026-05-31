"""Orquestra o pipeline completo: Mongo → tratamento → Prophet → Mongo.

Equivale, de ponta a ponta, ao que o notebook fazia com CSVs do Google Drive,
porém lendo os dados do MongoDB e gravando os resultados de volta nele.
"""

import gc

import pandas as pd

from app.config.settings import settings
from app.data.loader import load_apurados, load_limite
from app.data.treatment import build_indicadores, build_series
from app.storage.writer import append_forecasts, get_forecast_collection, save_treated
from app.training.prophet_trainer import processar_unidade
from app.utils.logger import logger


def _treinar_indicador(df_indicadores: pd.DataFrame, indicador: str, forecast_coll) -> dict:
    serie = build_series(df_indicadores, indicador)
    unidades = serie["IdeConjUndConsumidoras"].unique()

    pendentes = []          # previsões ainda não persistidas (checkpoint)
    total_inseridos = 0
    processadas = 0
    ignoradas = 0
    erros = 0

    for i, cod in enumerate(unidades, start=1):
        df_unit = serie[serie["IdeConjUndConsumidoras"] == cod]
        try:
            res = processar_unidade(int(cod), df_unit, indicador=indicador)
            if res:
                pendentes.append(res["previsoes"])
                processadas += 1
                logger.info(
                    "✓ [%s/%s] %s unidade %s | MAE: %.4f",
                    i, len(unidades), indicador, cod, res["mae"],
                )
            else:
                ignoradas += 1
        except Exception as exc:  # noqa: BLE001 — não queremos abortar o lote
            erros += 1
            logger.warning("✗ [%s/%s] %s unidade %s | erro: %s", i, len(unidades), indicador, cod, exc)
        finally:
            gc.collect()

        # Checkpoint: persiste parcialmente para não perder progresso.
        if i % settings.CHECKPOINT == 0 and pendentes:
            df_batch = pd.concat(pendentes, ignore_index=True)
            total_inseridos += append_forecasts(forecast_coll, df_batch)
            pendentes = []
            logger.info("  → Checkpoint %s: %s previsões salvas", indicador, f"{total_inseridos:,}")

    if pendentes:
        df_batch = pd.concat(pendentes, ignore_index=True)
        total_inseridos += append_forecasts(forecast_coll, df_batch)

    resumo = {
        "indicador": indicador,
        "unidades_processadas": processadas,
        "ignoradas": ignoradas,
        "erros": erros,
        "previsoes_inseridas": total_inseridos,
    }
    logger.info("Indicador %s finalizado: %s", indicador, resumo)
    return resumo


def run() -> dict:
    logger.info("== Iniciando pipeline de séries temporais (ANEEL/Prophet) ==")

    # 1. Carregamento a partir do Mongo.
    df_apurados = load_apurados()
    df_limite = load_limite()
    if df_apurados.empty:
        raise RuntimeError("Nenhum indicador apurado encontrado no Mongo — abortando.")

    # 2. Tratamento/consolidação.
    df_indicadores = build_indicadores(df_apurados, df_limite)
    del df_apurados, df_limite
    gc.collect()

    # 3. Persiste os dados tratados.
    save_treated(df_indicadores)

    # 4. Treinamento por indicador + persistência das previsões.
    forecast_coll = get_forecast_collection()
    resumos = [
        _treinar_indicador(df_indicadores, indicador, forecast_coll)
        for indicador in settings.INDICADORES
    ]

    logger.info("== Pipeline concluído ==")
    return {"indicadores": resumos}


if __name__ == "__main__":
    run()
