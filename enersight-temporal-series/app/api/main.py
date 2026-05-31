from fastapi import FastAPI, Query, HTTPException
from motor.motor_asyncio import AsyncIOMotorClient
from pydantic import BaseModel, field_serializer
from typing import Optional
from datetime import datetime
import os

# ─────────────────────────────────────────────
#  Config — lê as env vars do compose
# ─────────────────────────────────────────────
MONGO_HOST     = os.getenv("MONGO_HOST", "localhost")
MONGO_PORT     = os.getenv("MONGO_PORT", "27017")
MONGO_USER     = os.getenv("MONGO_USER", "")
MONGO_PASSWORD = os.getenv("MONGO_PASSWORD", "")
MONGO_AUTH_DB  = os.getenv("MONGO_AUTH_DB", "admin")
DB_NAME        = os.getenv("SOURCE_DB", "aneel")
COL_NAME       = "previsoes_prophet"

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
    app.state.client = AsyncIOMotorClient(MONGO_URI)
    app.state.col    = app.state.client[DB_NAME][COL_NAME]

@app.on_event("shutdown")
async def shutdown():
    app.state.client.close()

# ─────────────────────────────────────────────
#  Schema de resposta
# ─────────────────────────────────────────────
class Previsao(BaseModel):
    cod_unidade: int
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
#  Endpoints
# ─────────────────────────────────────────────
PROJECTION = {
    "_id":         0,
    "cod_unidade": 1,
    "indicador":   1,
    "mae":         1,
    "data":        1,
    "previsao":    1,
}


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
    col = app.state.col

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

    return PaginatedResponse(
        total=total,
        pagina=pagina,
        limite=limite,
        paginas=paginas,
        dados=docs,
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