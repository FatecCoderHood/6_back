"""Treinamento Prophet por unidade consumidora (porte fiel do notebook).

Prophet é leve (sem GPU, fit em segundos por série), ideal para milhares de
unidades. A configuração reproduz a do notebook:
  - yearly_seasonality=True  → DEC/FEC têm forte sazonalidade anual
  - weekly/daily_seasonality=False → dados mensais
  - n_changepoints reduzido → evita overfitting em séries curtas
"""

from typing import Optional

import numpy as np
import pandas as pd
from prophet import Prophet

from app.config.settings import settings


def _new_model() -> Prophet:
    return Prophet(
        yearly_seasonality=True,
        weekly_seasonality=False,
        daily_seasonality=False,
        n_changepoints=settings.N_CHANGEPOINTS,
        seasonality_mode="additive",
    )


def processar_unidade(cod: int, df_unit: pd.DataFrame, indicador: str) -> Optional[dict]:
    """Treina o Prophet para uma unidade e devolve previsões + MAE de validação.

    Retorna None quando o histórico é insuficiente (< MIN_AMOSTRAS pontos).
    """
    horizonte = settings.HORIZONTE
    min_amostras = settings.MIN_AMOSTRAS

    df_unit = (
        df_unit[["data", "VlrIndiceEnviado"]]
        .dropna()
        .rename(columns={"data": "ds", "VlrIndiceEnviado": "y"})
        .reset_index(drop=True)
    )

    if len(df_unit) < min_amostras:
        return None

    # Modelo completo (todos os dados disponíveis).
    m = _new_model()
    m.fit(df_unit)

    # Validação interna: MAE nos últimos HORIZONTE meses.
    if len(df_unit) >= horizonte + min_amostras:
        df_train_val = df_unit.iloc[:-horizonte]
        df_test_val = df_unit.iloc[-horizonte:]
        m_val = _new_model()
        m_val.fit(df_train_val)
        future_val = m_val.make_future_dataframe(periods=horizonte, freq="MS", include_history=False)
        forecast_val = m_val.predict(future_val)
        mae_val = float(np.mean(np.abs(forecast_val["yhat"].values - df_test_val["y"].values)))
    else:
        mae_val = float("nan")

    # Previsão futura com o modelo completo.
    future = m.make_future_dataframe(periods=horizonte, freq="MS", include_history=False)
    forecast = m.predict(future)

    df_prev = pd.DataFrame(
        {
            "cod_unidade": cod,
            "indicador": indicador,
            "mae": round(mae_val, 6),
            "data": forecast["ds"],
            "previsao": forecast["yhat"].round(6),
            "previsao_lower": forecast["yhat_lower"].round(6),
            "previsao_upper": forecast["yhat_upper"].round(6),
        }
    )

    return {"cod_unidade": cod, "mae": mae_val, "previsoes": df_prev}
