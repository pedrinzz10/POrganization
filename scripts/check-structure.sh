#!/usr/bin/env bash
# Spec B01: verifica a estrutura do monorepo (T1) e o .gitignore (T2).
# Uso: bash scripts/check-structure.sh  (sai com 0 se tudo estiver certo)
set -u

cd "$(git rev-parse --show-toplevel)" || exit 1

failures=0
fail() { echo "FALHA: $1"; failures=$((failures + 1)); }
ok() { echo "ok: $1"; }

# T1 (CA1): as pastas backend/, frontend/ e specs/ existem na raiz.
for dir in backend frontend specs; do
  if [ -d "$dir" ]; then ok "pasta $dir/ existe"; else fail "pasta $dir/ não existe"; fi
done

# T2 (CA2): arquivos de build, dependências, segredos e IDE são ignorados.
# Cria só o que ainda não existe e remove apenas o que criou.
should_ignore=(
  ".env"
  "backend/target/x"
  "frontend/node_modules/x"
  "frontend/dist/x"
  "frontend/.angular/x"
  ".idea/workspace.xml"
  "backend/porganization.iml"
  ".vscode/settings.json"
)
created=()
cleanup() {
  for path in "${created[@]}"; do rm -f "$path"; done
  for dir in backend/target frontend/node_modules frontend/dist frontend/.angular .idea .vscode; do
    rmdir "$dir" 2>/dev/null || true
  done
}
trap cleanup EXIT

for path in "${should_ignore[@]}"; do
  if [ ! -e "$path" ]; then
    mkdir -p "$(dirname "$path")"
    touch "$path"
    created+=("$path")
  fi
  if git check-ignore -q "$path"; then ok "$path é ignorado"; else fail "$path deveria ser ignorado"; fi
done

# O que precisa ser versionado não pode cair no .gitignore.
should_track=(".env.example" ".vscode/extensions.json" "backend/.gitkeep" "frontend/.gitkeep")
for path in "${should_track[@]}"; do
  if git check-ignore -q --no-index "$path"; then fail "$path não deveria ser ignorado"; else ok "$path é versionável"; fi
done

if [ "$failures" -gt 0 ]; then
  echo "$failures verificação(ões) falharam"
  exit 1
fi
echo "estrutura OK"
