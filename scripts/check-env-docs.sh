#!/usr/bin/env bash
# Spec B12 (T2): toda variável de configuração usada no código está documentada.
#  - ${VAR} dos application*.yml e as chaves do render.yaml: no README e no .env.example
#    (exceto as que a hospedagem define sozinha, como PORT)
#  - campos dos environment*.ts do Angular: no README
# Uso: bash scripts/check-env-docs.sh
set -u

cd "$(git rev-parse --show-toplevel)" || exit 1

# Definidas pela hospedagem/perfil, não pelo .env local
SEM_ENV_EXAMPLE="PORT SPRING_PROFILES_ACTIVE"

failures=0
fail() { echo "FALHA: $1"; failures=$((failures + 1)); }

backend_vars="$( { grep -rhoE '\$\{[A-Z][A-Z0-9_]*' backend/src/main/resources/application*.yml | tr -d '${'; \
  grep -oE 'key: [A-Z][A-Z0-9_]*' render.yaml | sed 's/key: //'; } | sort -u)"
frontend_vars="$(grep -hoE '^\s+[a-zA-Z]+:' frontend/src/environments/environment*.ts | tr -d ' :' | grep -v '^production$' | sort -u)"

if [ -z "$backend_vars" ] || [ -z "$frontend_vars" ]; then
  fail "não encontrou variáveis no código (o script precisa de ajuste?)"
fi

for var in $backend_vars; do
  grep -qw "$var" README.md || fail "$var não está no README.md"
  if [[ " $SEM_ENV_EXAMPLE " != *" $var "* ]]; then
    grep -qE "^$var=" .env.example || fail "$var não está no .env.example"
  fi
done

for var in $frontend_vars; do
  grep -qw "$var" README.md || fail "environment.$var não está no README.md"
done

if [ "$failures" -gt 0 ]; then
  echo "$failures variável(is) sem documentação"
  exit 1
fi
echo "variáveis documentadas: $(echo $backend_vars $frontend_vars | wc -w) ($(echo $backend_vars | tr '\n' ' ')| frontend: $(echo $frontend_vars | tr '\n' ' '))"
