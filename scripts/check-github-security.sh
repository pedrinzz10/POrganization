#!/usr/bin/env bash
# Spec B13 (T5): confere as proteções configuradas no GitHub. Precisa do gh autenticado.
# Uso: bash scripts/check-github-security.sh
set -u

REPO="${REPO:-pedrinzz10/POrganization}"
failures=0
fail() { echo "FALHA: $1"; failures=$((failures + 1)); }
ok() { echo "ok: $1"; }

for feature in secret_scanning secret_scanning_push_protection; do
  status="$(gh api "repos/$REPO" --jq ".security_and_analysis.$feature.status" 2>/dev/null)"
  if [ "$status" = enabled ]; then ok "$feature ativo"; else fail "$feature está '${status:-indisponível}'"; fi
done

# Regras efetivas na main (inclui rulesets ativos)
rules="$(gh api "repos/$REPO/rules/branches/main" --jq '[.[].type] | join(",")' 2>/dev/null)"
for rule in pull_request non_fast_forward deletion required_status_checks; do
  if [[ ",$rules," == *",$rule,"* ]]; then ok "main tem a regra $rule"; else fail "main sem a regra $rule"; fi
done

checks="$(gh api "repos/$REPO/rules/branches/main" --jq '[.[] | select(.type == "required_status_checks") | .parameters.required_status_checks[].context] | join(",")' 2>/dev/null)"
if [[ ",$checks," == *",segredos,"* ]]; then ok "check 'segredos' obrigatório na main"; else fail "check 'segredos' não é obrigatório na main"; fi

if [ "$failures" -gt 0 ]; then
  echo "$failures verificação(ões) falharam"
  exit 1
fi
echo "proteções do GitHub OK"
