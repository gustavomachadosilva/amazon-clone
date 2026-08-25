#!/bin/bash
# Script de Smoke Test para o Clone da Amazon (Monolito Modular)
# Este script valida se o backend, frontend e banco de dados estão rodando corretamente.

# Cores para o terminal
GREEN='\033[0;32m'
RED='\033[0;31m'
NC='\033[0m' # Sem cor
BOLD='\033[1m'

echo -e "${BOLD}Iniciando testes de fumaça (Smoke Tests) do Marketplace...${NC}\n"

# 1. Verificar se o Docker Compose subiu os serviços
echo -e "${BOLD}[1/4] Verificando containers Docker...${NC}"
if ! command -v docker &> /dev/null; then
    echo -e "${RED}Erro: Docker não está instalado ou não está no PATH.${NC}"
else
    docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
fi
echo ""

# 2. Testar conexão com o Backend e rota de Health Check
echo -e "${BOLD}[2/4] Testando o endpoint de Health Check do Backend...${NC}"
BACKEND_URL="http://localhost:8080/api/health"

# Tenta fazer uma requisição HTTP para o health check
response=$(curl -s -o /dev/null -w "%{http_code}" $BACKEND_URL)

if [ "$response" -eq 200 ]; then
    echo -e "${GREEN}✔ Backend respondendo com sucesso (HTTP 200) na rota /api/health.${NC}"
else
    echo -e "${RED}✘ Falha ao conectar ao Backend. HTTP Status: $response (Esperava 200).${NC}"
    echo "Verifique se a aplicação Spring Boot está rodando na porta 8080."
fi
echo ""

# 3. Testar endpoint de Catálogo (Módulo Catalog)
echo -e "${BOLD}[3/4] Testando rota de busca de produtos (Módulo Catalog)...${NC}"
CATALOG_URL="http://localhost:8080/api/catalog/products"

catalog_response=$(curl -s -w "\nHTTP_STATUS:%{http_code}" $CATALOG_URL)
http_status=$(echo "$catalog_response" | tr -d '\r' | tr -d '\n' | grep -o 'HTTP_STATUS:[0-9]*' | cut -d: -f2)
response_body=$(echo "$catalog_response" | sed '/HTTP_STATUS:/d')

if [ "$http_status" -eq 200 ]; then
    echo -e "${GREEN}✔ Módulo de Catálogo respondendo corretamente (HTTP 200).${NC}"
    echo "Payload retornado:"
    echo "$response_body" | jq . 2>/dev/null || echo "$response_body"
else
    echo -e "${RED}✘ Falha no Módulo de Catálogo. HTTP Status: $http_status (Esperava 200).${NC}"
fi
echo ""

# 4. Testar se o Frontend está acessível
echo -e "${BOLD}[4/4] Verificando disponibilidade do Frontend (React/Vite)...${NC}"
FRONTEND_URL="http://localhost:5173" # Porta padrão do Vite

frontend_status=$(curl -s -o /dev/null -w "%{http_code}" $FRONTEND_URL)

if [ "$frontend_status" -eq 200 ] || [ "$frontend_status" -eq 304 ]; then
    echo -e "${GREEN}✔ Servidor Frontend React/Vite ativo na porta 5173.${NC}"
else
    echo -e "${RED}✘ Não foi possível acessar o Frontend na porta 5173.${NC}"
    echo "Certifique-se de que o servidor de desenvolvimento do React ('npm run dev') está rodando."
fi

echo -e "\n${BOLD}--- Testes Concluídos ---${NC}"
