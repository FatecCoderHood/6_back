from fastapi import FastAPI, Query, HTTPException
from motor.motor_asyncio import AsyncIOMotorClient
from pydantic import BaseModel, field_serializer
from typing import Optional
from datetime import datetime
import os

# ─────────────────────────────────────────────
#  Config
# ─────────────────────────────────────────────
MONGO_HOST     = os.getenv("MONGO_HOST", "localhost")
MONGO_PORT     = os.getenv("MONGO_PORT", "27017")
MONGO_USER     = os.getenv("MONGO_USER", "")
MONGO_PASSWORD = os.getenv("MONGO_PASSWORD", "")
MONGO_AUTH_DB  = os.getenv("MONGO_AUTH_DB", "admin")
DB_NAME        = os.getenv("SOURCE_DB", "aneel")
COL_NAME       = "previsoes_prophet"
COL_LIMITES    = "indicadores_continuidade-limite"

# Chave composta da collection de limites
COMPOSITE_KEY = (
    "DatGeracaoConjuntoDados;SigAgente;NumCNPJ;IdeConjUndConsumidoras;"
    "DscConjUndConsumidoras;SigIndicador;AnoLimiteQualidade;VlrLimite"
)
IDX_SIG_AGENTE = 1
IDX_IDE_CONJ   = 3

MONGO_URI = (
    f"mongodb://{MONGO_USER}:{MONGO_PASSWORD}"
    f"@{MONGO_HOST}:{MONGO_PORT}/?authSource={MONGO_AUTH_DB}"
)

# ─────────────────────────────────────────────
#  App
# ─────────────────────────────────────────────
app = FastAPI(
    title="EnerSight – Previsões Prophet",
    description="API para consulta de previsões Prophet armazenadas no MongoDB.",
    version="1.0.0",
)

# ─────────────────────────────────────────────
#  MongoDB lifespan
# ─────────────────────────────────────────────
@app.on_event("startup")
async def startup():
    app.state.client     = AsyncIOMotorClient(MONGO_URI)
    app.state.col        = app.state.client[DB_NAME][COL_NAME]
    app.state.col_limits = app.state.client[DB_NAME][COL_LIMITES]

@app.on_event("shutdown")
async def shutdown():
    app.state.client.close()

# ─────────────────────────────────────────────
#  Helper: mapeia cod_unidade → SigAgente
#  Usa find (não distinct) para evitar problema
#  com chaves que contêm ";" no nome
# ─────────────────────────────────────────────
async def get_agente_map(col_limits, cod_unidades: list[int]) -> dict[int, str]:
    cod_set    = {str(c) for c in cod_unidades}
    agente_map: dict[int, str] = {}

    cursor = col_limits.find({}, {COMPOSITE_KEY: 1, "_id": 0})
    async for doc in cursor:
        valor = doc.get(COMPOSITE_KEY, "")
        if not valor:
            continue

        partes = valor.split(";")
        if len(partes) <= max(IDX_SIG_AGENTE, IDX_IDE_CONJ):
            continue

        sig_agente = partes[IDX_SIG_AGENTE].strip()
        ide_conj   = partes[IDX_IDE_CONJ].strip()

        if ide_conj in cod_set and int(ide_conj) not in agente_map:
            try:
                agente_map[int(ide_conj)] = sig_agente
            except ValueError:
                continue

    return agente_map

# ─────────────────────────────────────────────
#  Schemas
# ─────────────────────────────────────────────
class Previsao(BaseModel):
    cod_unidade: int
    sig_agente:  Optional[str] = None
    indicador:   str
    mae:         float
    data:        datetime
    previsao:    float

    @field_serializer("data")
    def serialize_data(self, v: datetime) -> str:
        return v.strftime("%Y-%m-%dT%H:%M:%S")


class PaginatedResponse(BaseModel):
    total:    int
    pagina:   int
    limite:   int
    paginas:  int
    dados:    list[Previsao]

# ─────────────────────────────────────────────
#  Projection
# ─────────────────────────────────────────────
PROJECTION = {
    "_id":         0,
    "cod_unidade": 1,
    "indicador":   1,
    "mae":         1,
    "data":        1,
    "previsao":    1,
}

# ─────────────────────────────────────────────
#  Endpoints
# ─────────────────────────────────────────────
@app.get(
    "/previsoes",
    response_model=PaginatedResponse,
    summary="Lista previsões paginadas",
    tags=["Previsões"],
)
async def listar_previsoes(
    cod_unidade: Optional[int] = Query(None, description="Filtrar por código da unidade"),
    indicador:   Optional[str] = Query(None, description="Filtrar por indicador (ex: DEC, FEC)"),
    pagina:      int           = Query(1,    ge=1,         description="Número da página"),
    limite:      int           = Query(20,   ge=1, le=100, description="Itens por página"),
):
    col        = app.state.col
    col_limits = app.state.col_limits

    filtro: dict = {}
    if cod_unidade is not None:
        filtro["cod_unidade"] = cod_unidade
    if indicador is not None:
        filtro["indicador"] = indicador.upper()

    total   = await col.count_documents(filtro)
    skip    = (pagina - 1) * limite
    paginas = max(1, -(-total // limite))

    if total > 0 and pagina > paginas:
        raise HTTPException(
            status_code=400,
            detail=f"Página {pagina} não existe. Total de páginas: {paginas}.",
        )

    cursor = (
        col.find(filtro, PROJECTION)
           .sort("data", 1)
           .skip(skip)
           .limit(limite)
    )
    docs = await cursor.to_list(length=limite)

    cod_unidades = list({d["cod_unidade"] for d in docs})
    agente_map   = await get_agente_map(col_limits, cod_unidades)

    resultado = []
    for d in docs:
        resultado.append(Previsao(
            cod_unidade = d["cod_unidade"],
            sig_agente  = agente_map.get(d["cod_unidade"]),
            indicador   = d["indicador"],
            mae         = d["mae"],
            data        = d["data"],
            previsao    = d["previsao"],
        ))

    return PaginatedResponse(
        total=total,
        pagina=pagina,
        limite=limite,
        paginas=paginas,
        dados=resultado,
    )


@app.get(
    "/previsoes/{cod_unidade}/{indicador}",
    response_model=PaginatedResponse,
    summary="Previsões por unidade e indicador (rota direta)",
    tags=["Previsões"],
)
async def previsoes_por_unidade_indicador(
    cod_unidade: int,
    indicador:   str,
    pagina: int = Query(1,  ge=1),
    limite: int = Query(20, ge=1, le=100),
):
    return await listar_previsoes(
        cod_unidade=cod_unidade,
        indicador=indicador,
        pagina=pagina,
        limite=limite,
    )


@app.get("/health", tags=["Infra"], summary="Health check")
async def health():
    try:
        await app.state.client.admin.command("ping")
        return {"status": "ok", "mongo": "conectado", "db": DB_NAME}
    except Exception as e: 
        raise HTTPException(status_code=503, detail=f"MongoDB indisponível: {e}")