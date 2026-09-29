#!/usr/bin/env bash
# Prepara um clone novo para desenvolvimento. Rode uma vez: bash scripts/setup-dev.sh
set -euo pipefail

root="$(git rev-parse --show-toplevel)"
cd "$root"

git config core.hooksPath .githooks
echo "ok: hooks do repositório ativos (.githooks/pre-commit bloqueia segredos)"

if [ -f .env.example ] && [ ! -f .env ]; then
  cp .env.example .env
  echo "ok: .env criado a partir do .env.example; preencha os valores (ele não vai para o git)"
fi

if command -v gitleaks >/dev/null 2>&1; then
  echo "ok: gitleaks $(gitleaks version) encontrado"
else
  echo "aviso: gitleaks não instalado. O hook funciona sem ele, mas fica mais completo com ele:"
  echo "  Windows: winget install gitleaks    macOS: brew install gitleaks"
fi
