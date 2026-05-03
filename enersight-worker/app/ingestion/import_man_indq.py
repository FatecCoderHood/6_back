from app.config.settings import settings
from app.utils.logger import logger

import pandas as pd
import numpy as np
from sqlalchemy import create_engine

def calcula_desvio(realizado, limite):
    limite = np.where(limite == 0, np.nan, limite)
    calculo = ((realizado - limite) / limite) * 100
    return np.maximum(0, calculo)

def save_to_db(df):
    logger.info("import_man_indq::save_to_db - Saving results to database...")

    engine = create_engine(
        "postgresql+psycopg2://app_user:app_password@localhost/enersight_app"
    )

    df.to_sql(
        "enel_sp_indq",
        engine,
        schema="staging",
        if_exists="replace",   # or "append"
        index=False,
        chunksize=10000,       # 👈 batching
        method="multi"         # 👈 bulk insert
    )

    logger.info("import_man_indq::save_to_db - Results saved to database.")

def import_man_indq():
    logger.info("import_man_indq::import_man_indq - Starting import...")

    DOWNLOADS_PATH = "/home/rtrevizoli/Downloads"

    logger.info("\n\n===============================================================")
    logger.info("Importing manual INDQ data from ANEEL => STEP 1: Data Preparation")
    logger.info("===============================================================\n")

    logger.info("import_man_indq::import_man_indq - Carregando os CSVs no Pandas...")
    df_real = pd.read_csv(rf"{DOWNLOADS_PATH}/indicadores-continuidade-coletivos-2020-2029.csv", sep=';', encoding='latin1')
    df_lim = pd.read_csv(rf"{DOWNLOADS_PATH}/indicadores-continuidade-coletivos-limite.csv", sep=';', encoding='latin1')

    # Filtrando para os últimos 5 anos (2021 a 2025)
    anos_alvo = [2021, 2022, 2023, 2024, 2025]
    df_real = df_real[df_real['AnoIndice'].isin(anos_alvo)]
    df_lim = df_lim[df_lim['AnoLimiteQualidade'].isin(anos_alvo)]

    df_real = df_real.rename(columns={'VlrIndiceEnviado': 'Valor_Realizado'})
    df_lim = df_lim.rename(columns={'VlrLimite': 'Valor_Limite', 'AnoLimiteQualidade': 'AnoIndice'})

    df_real['Valor_Realizado'] = df_real['Valor_Realizado'].astype(str).str.replace(',', '.').astype(float)
    df_lim['Valor_Limite'] = df_lim['Valor_Limite'].astype(str).str.replace(',', '.').astype(float)

    # Converter a coluna 'IdeConjUndConsumidoras' para string em ambos os DataFrames antes do merge
    df_real['IdeConjUndConsumidoras'] = df_real['IdeConjUndConsumidoras'].astype(str)
    df_lim['IdeConjUndConsumidoras'] = df_lim['IdeConjUndConsumidoras'].astype(str)

    # Adicionando 'IdeConjUndConsumidoras' às chaves de junção
    chaves_join = ['SigAgente', 'DscConjUndConsumidoras', 'IdeConjUndConsumidoras', 'SigIndicador', 'AnoIndice']
    df_completo = pd.merge(df_real, df_lim, on=chaves_join, how='inner')

    # Pivotando para DEC e FEC virarem colunas na mesma linha
    # IMPORTANTE: Colocamos o 'AnoIndice' no index para manter o histórico separado por ano!
    df_tratado = df_completo.pivot_table(
        index=['SigAgente', 'DscConjUndConsumidoras', 'IdeConjUndConsumidoras', 'AnoIndice'],
        columns='SigIndicador',
        values=['Valor_Realizado', 'Valor_Limite'],
        aggfunc={
            'Valor_Realizado': 'sum',  # Soma os 12 meses para dar o total do ano
            'Valor_Limite': 'mean'     # Tira a média para manter o valor original do limite anual
        }
    ).reset_index()
    df_tratado.columns = [f"{col[1]}_{col[0]}" if col[1] else col[0] for col in df_tratado.columns]

    df_tratado = df_tratado.rename(columns={
        'IdeConjUndConsumidoras': 'Conjunto_ID',
        'SigAgente': 'Distribuidora',
        'DscConjUndConsumidoras': 'Conjunto',
        'AnoIndice': 'Ano',
        'DEC_Valor_Realizado': 'DEC_realizado',
        'DEC_Valor_Limite': 'DEC_limite',
        'FEC_Valor_Realizado': 'FEC_realizado',
        'FEC_Valor_Limite': 'FEC_limite'
    })

    df_tratado = df_tratado.dropna(subset=['DEC_realizado', 'DEC_limite', 'FEC_realizado', 'FEC_limite'])

    logger.info("\n\n===============================================================")
    logger.info("Importing manual INDQ data from ANEEL => STEP 2: Data Export")
    logger.info("===============================================================\n")

    caminho_saida = rf"{DOWNLOADS_PATH}/dados_aneel_prontos_para_calculo.csv"
    df_tratado.to_csv(caminho_saida, index=False, sep=';', decimal=',')
    logger.info(f"import_man_indq::import_man_indq - Base redonda salva em: {caminho_saida}")

    logger.info("\n\n===============================================================")
    logger.info("Importing manual INDQ data from ANEEL => STEP 3: Criticality Calculation")
    logger.info("===============================================================\n")

    df_calculo = pd.read_csv(caminho_saida, sep=';', decimal=',')

    df_calculo['Desvio_DEC'] = calcula_desvio(df_calculo['DEC_realizado'], df_calculo['DEC_limite'])
    df_calculo['Desvio_FEC'] = calcula_desvio(df_calculo['FEC_realizado'], df_calculo['FEC_limite'])
    df_calculo['Score_Criticidade'] = df_calculo['Desvio_DEC'] + df_calculo['Desvio_FEC']

    df_resultado = df_calculo.sort_values(by='Score_Criticidade', ascending=False)

    logger.info("\n\n===============================================================")
    logger.info("Importing manual INDQ data from ANEEL => STEP 4: Save to Database")
    logger.info("===============================================================\n")

    save_to_db(df_resultado)

    logger.info("import_man_indq::import_man_indq - Import completed.")

if __name__ == "__main__":
    import_man_indq()