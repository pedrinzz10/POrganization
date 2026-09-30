#!/usr/bin/env bash
# Usado pelos workflows de CI: diz se algum arquivo alterado casa com um padrão.
# Uso: bash scripts/ci-changed.sh <sha-base> <regex-de-caminhos> <nome-da-saida>
# Escreve "<nome>=true|false" em $GITHUB_OUTPUT (ou no stdout, fora do Actions).
# Sem base utilizável (primeiro push, histórico reescrito), responde true: na dúvida, roda.
set -euo pipefail

base="${1:-}"
pattern="$2"
name="$3"
out="${GITHUB_OUTPUT:-/dev/stdout}"

if [ -z "$base" ] || [ "$base" = "0000000000000000000000000000000000000000" ] \
  || ! git cat-file -e "${base}^{commit}" 2>/dev/null; then
  echo "sem base para comparar; $name roda"
  echo "$name=true" >> "$out"
  exit 0
fi

changed="$(git diff --name-only "$base" HEAD)"
if grep -qE "$pattern" <<< "$changed"; then
  echo "$name mudou:"
  grep -E "$pattern" <<< "$changed" | head -20
  echo "$name=true" >> "$out"
else
  echo "nada em $name mudou; o job será pulado"
  echo "$name=false" >> "$out"
fi
