#!/usr/bin/env bash
# Constrói as três imagens de sandbox usadas pelo SandboxExecutor.
# Correr uma vez na máquina host (ou no pipeline de deploy).
set -euo pipefail
cd "$(dirname "$0")"

docker build -t bughunt/java21:latest    -f java21.Dockerfile .
docker build -t bughunt/python312:latest -f python312.Dockerfile .
docker build -t bughunt/node20:latest    -f node20.Dockerfile .

echo "✓ Imagens construídas:"
docker images | grep bughunt
