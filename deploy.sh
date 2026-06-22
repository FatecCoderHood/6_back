#!/bin/bash

# deploy.sh - Script para deploy no servidor Linux

set -e

echo "🚀 Iniciando deploy do Enersight..."

# 1. Backup do banco
echo "📦 Fazendo backup do banco..."
docker exec enersight-db pg_dump -U postgres postgres > backup_$(date +%Y%m%d_%H%M%S).sql

# 2. Pull das novas imagens
echo "📥 Baixando novas imagens..."
docker-compose pull

# 3. Parar serviços antigos
echo "🛑 Parando serviços..."
docker-compose down

# 4. Subir novos serviços
echo "⬆️ Subindo novos serviços..."
docker-compose up -d

# 5. Health check
echo "🏥 Verificando saúde dos serviços..."
sleep 10

for service in enersight-api enersight-temporal-series-api; do
    if docker ps --filter "name=$service" --filter "status=running" | grep -q $service; then
        echo "✅ $service está rodando"
    else
        echo "❌ $service falhou ao iniciar"
        docker logs $service --tail 50
        exit 1
    fi
done

# 6. Limpeza
echo "🧹 Limpando imagens antigas..."
docker system prune -f

echo "✅ Deploy concluído com sucesso!"