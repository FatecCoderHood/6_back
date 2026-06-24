# enersight-temporal-series

Pipeline de séries temporais para os indicadores de continuidade da ANEEL
(DEC/FEC). Lê os dados do MongoDB, trata/consolida, treina um modelo
[Prophet](https://facebook.github.io/prophet/) por unidade consumidora e grava
de volta no Mongo tanto os **dados tratados** quanto as **previsões futuras**.

É a versão "produtizada" do notebook `AnellTratado_PROPHET_CSV.ipynb` (que lia
CSVs do Google Drive). Aqui a origem e o destino são o MongoDB.

## Fluxo

```
MongoDB (aneel.*)  →  tratamento/consolidação  →  Prophet (por unidade)  →  MongoDB (aneel.*)
```

1. **Carregamento** (`app/data/loader.py`) — lê as collections de origem e
   reconstrói cada linha (ver "Detalhe da importação" abaixo).
2. **Tratamento** (`app/data/treatment.py`) — limpeza de tipos, filtro por ano
   mínimo, consolidação de apurados + limites e construção da série mensal.
3. **Treinamento** (`app/training/prophet_trainer.py`) — um Prophet por unidade,
   com validação interna (MAE nos últimos 12 meses) e horizonte de 12 meses.
4. **Persistência** (`app/storage/writer.py`) — grava os resultados no Mongo.

## Collections

**Origem** (base `aneel`, importadas conforme `docs/HOW_TO_UPLOAD_ANEEL_DATA.md`):

- `indicadores_continuidade-2010-2019`
- `indicadores_continuidade-2020-2029`
- `indicadores_continuidade-limite`
- `indicadores_continuidade-atributos` — **opcional/ausente**: o CSV de atributos
  só tem cabeçalho, então o `mongoimport` não o aceita. O pipeline detecta a
  ausência e segue normalmente (esse caso era inócuo já no notebook).

**Destino** (base `aneel`):

- `indicadores_tratados` — dataset consolidado e limpo.
- `previsoes_prophet` — previsões por unidade/indicador (`cod_unidade`,
  `indicador`, `mae`, `data`, `previsao`, `previsao_lower`, `previsao_upper`).

> Cada execução **recria** as collections de destino (idempotente).

## Detalhe da importação (delimitador)

Os CSVs usam `;` como separador de colunas e `,` como decimal, mas o
`mongoimport --type csv` assume `,` como delimitador. Resultado: cada documento
ficou assim —

```json
{ "Dat...;...;VlrIndiceEnviado": "<linha até a 1ª vírgula>", "field1": "<parte decimal>" }
```

A chave é o cabeçalho inteiro e o valor é a linha original quebrada na vírgula.
O loader reconstrói a linha (`valor + "," + field1`) e refaz o split por `;`.

## Como rodar

### Via Docker (recomendado)

A partir de `../docker`, com o Mongo no ar:

```bash
docker compose up enersight-mongo mongo-express -d
docker compose run --rm enersight-temporal-series
```

### Localmente

```bash
python -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
MONGO_HOST=localhost python -m app
```

## Configuração (variáveis de ambiente)

| Variável | Padrão | Descrição |
|---|---|---|
| `MONGO_HOST` | `localhost` | Host do Mongo (`enersight-mongo` no compose) |
| `MONGO_PORT` | `27017` | Porta |
| `MONGO_USER` / `MONGO_PASSWORD` | `app_user` / `app_password` | Credenciais |
| `MONGO_AUTH_DB` | `admin` | Banco de autenticação |
| `SOURCE_DB` | `aneel` | Base de origem |
| `OUTPUT_DB` | `aneel` | Base de destino |
| `TREATED_COLLECTION` | `indicadores_tratados` | Collection dos dados tratados |
| `FORECAST_COLLECTION` | `previsoes_prophet` | Collection das previsões |
| `INDICADORES` | `DEC,FEC` | Indicadores a treinar |
| `ANO_MINIMO` | `2015` | Ano mínimo considerado |
| `DATA_MAX` | `2026-02-01` | Unidades precisam ter ponto nessa data |
| `HORIZONTE` | `12` | Meses previstos |
| `MIN_AMOSTRAS` | `24` | Mínimo de pontos históricos por unidade |
| `N_CHANGEPOINTS` | `10` | Changepoints do Prophet |
| `CHECKPOINT` | `500` | Grava previsões a cada N unidades |

## API de consulta (`enersight-temporal-series-api`)

Serviço FastAPI separado (`app/api/main.py`, `Dockerfile.api`) que expõe as previsões já gravadas
no Mongo para o frontend — não roda o pipeline, só lê `previsoes_prophet`.

| Rota | Descrição |
|---|---|
| `GET /previsoes` | Lista paginada (`cod_unidade`, `indicador`, `pagina`, `limite` como filtros) |
| `GET /previsoes/{cod_unidade}/{indicador}` | Mesma listagem, atalho por unidade+indicador |
| `GET /health` | Healthcheck (usado pelo compose) |

> ⚠️ **Sem autenticação**: nenhuma rota exige token hoje, e o serviço é publicado na porta 8000 do
> host via docker-compose. Não exponha esse compose em uma rede não confiável sem adicionar alguma
> proteção antes.

### Como rodar

```bash
# a partir de ../docker
docker compose up -d enersight-mongo
docker compose up -d enersight-temporal-series-api
```

Escuta em **`http://localhost:8000`** (Swagger UI em `/docs`).

## Testes

Não há suíte de testes automatizados ainda para este serviço (nem para o pipeline, nem para a
API). Verificação hoje é manual: rodar o pipeline e inspecionar as collections de destino no
Mongo, ou chamar `GET /previsoes` e `GET /health` na API e confirmar os dados esperados.
