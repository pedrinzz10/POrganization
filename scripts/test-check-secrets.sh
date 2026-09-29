#!/usr/bin/env bash
# Spec B13: testes do scripts/check-secrets.sh (T1, T2) e do hook pre-commit (T3).
# As fixtures com segredos falsos são montadas só em tempo de execução, numa pasta
# temporária, para o repositório nunca conter um segredo (nem falso) em texto.
# Uso: bash scripts/test-check-secrets.sh
set -u

ROOT="$(git rev-parse --show-toplevel)"
CHECK="$ROOT/scripts/check-secrets.sh"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

failures=0
fail() { echo "FALHA: $1"; failures=$((failures + 1)); }
ok() { echo "ok: $1"; }

b64url() { printf '%s' "$1" | base64 | tr -d '=\n' | tr '/+' '_-'; }
rand() { LC_ALL=C tr -dc 'A-Za-z0-9' </dev/urandom | head -c "$1"; }

# Pedaços montados em runtime: nenhum aparece inteiro neste arquivo.
SB_SECRET="sb_""secret_$(rand 32)"
SB_PUBLISHABLE="sb_""publishable_$(rand 32)"
JWT_HEADER="$(b64url '{"alg":"HS256","typ":"JWT"}')"
JWT_SERVICE="$JWT_HEADER.$(b64url '{"iss":"supabase","ref":"abcdefghij","role":"service_role"}').$(rand 43)"
JWT_ANON="$JWT_HEADER.$(b64url '{"iss":"supabase","ref":"abcdefghij","role":"anon"}').$(rand 43)"
PK_BEGIN="-----BEGIN ""PRIVATE KEY-----"
PK_END="-----END ""PRIVATE KEY-----"
DB_PASS="$(rand 20)"

# roda o check numa pasta de fixture; $1 = nome do caso, $2 = esperado (fail|pass), resto = arquivos
expect() {
  local name="$1" expected="$2"; shift 2
  local out code
  out="$(cd "$TMP/case" && bash "$CHECK" "$@" 2>&1)"; code=$?
  if [ "$expected" = fail ] && [ "$code" -eq 0 ]; then fail "$name: deveria falhar"; return; fi
  if [ "$expected" = pass ] && [ "$code" -ne 0 ]; then fail "$name: deveria passar"; echo "$out"; return; fi
  for secret in "$SB_SECRET" "$JWT_SERVICE" "$DB_PASS"; do
    if [[ "$out" == *"$secret"* ]]; then fail "$name: a saída expôs o segredo"; return; fi
  done
  ok "$name ($expected)"
}

new_case() { rm -rf "$TMP/case"; mkdir -p "$TMP/case"; }

# ---------- T1 (CA1): conteúdos e arquivos proibidos ----------
new_case; printf 'const key = "%s";\n' "$SB_SECRET" > "$TMP/case/client.ts"
expect "chave secreta do Supabase" fail client.ts

new_case; printf 'export const k = "%s";\n' "$JWT_SERVICE" > "$TMP/case/env.ts"
expect "JWT com role service_role" fail env.ts

new_case; printf '%s\n%s\n%s\n' "$PK_BEGIN" "$(rand 64)" "$PK_END" > "$TMP/case/notas.txt"
expect "chave privada" fail notas.txt

new_case; echo "url=postgresql://postgres.abc:${DB_PASS}@aws-0-sa-east-1.pooler.supabase.com:5432/postgres" > "$TMP/case/conexao.md"
expect "URL de banco com senha" fail conexao.md

new_case; printf 'spring:\n  datasource:\n    password: %s\n' "$DB_PASS" > "$TMP/case/application-prod.yml"
expect "senha literal em application*.yml" fail application-prod.yml

new_case; printf 'DB_USER=x\n' > "$TMP/case/.env"
expect "arquivo .env rastreado" fail .env

new_case; printf 'qualquer\n' > "$TMP/case/servidor.pem"
expect "arquivo .pem rastreado" fail servidor.pem

# permitidos
new_case; printf 'export const environment = { supabaseKey: "%s" };\n' "$SB_PUBLISHABLE" > "$TMP/case/environment.ts"
expect "publishable key do Supabase" pass environment.ts

new_case; printf 'export const environment = { supabaseKey: "%s" };\n' "$JWT_ANON" > "$TMP/case/environment.ts"
expect "JWT anon legado" pass environment.ts

new_case; printf 'spring:\n  datasource:\n    url: ${DB_URL}\n    password: ${DB_PASSWORD}\n' > "$TMP/case/application.yml"
printf 'Conexão: postgresql://postgres:${DB_PASSWORD}@host:5432/postgres\n' > "$TMP/case/README.md"
expect "variáveis \${VAR}" pass application.yml README.md

# ---------- T2 (CA2): .env.example sem valores sensíveis ----------
new_case; printf 'DB_URL=jdbc:postgresql://localhost:5432/app\nDB_PASSWORD=abc\n' > "$TMP/case/.env.example"
expect ".env.example com senha preenchida" fail .env.example

new_case; printf '# comentário\nDB_URL=jdbc:postgresql://localhost:5432/app\nDB_PASSWORD=\nSUPABASE_ISSUER=https://<ref>.supabase.co/auth/v1\n' > "$TMP/case/.env.example"
expect ".env.example só com nomes sensíveis vazios" pass .env.example

# ---------- T3 (CA3): hook pre-commit bloqueia segredo staged ----------
REPO="$TMP/repo"
mkdir -p "$REPO/scripts" "$REPO/.githooks"
cp "$CHECK" "$REPO/scripts/check-secrets.sh"
cp "$ROOT/.githooks/pre-commit" "$REPO/.githooks/pre-commit"
chmod +x "$REPO/scripts/check-secrets.sh" "$REPO/.githooks/pre-commit"
(
  cd "$REPO" || exit 1
  git init -q
  git config user.name teste && git config user.email teste@example.com
  git config core.autocrlf false
  git config core.hooksPath .githooks
  git add scripts .githooks && git commit -qm base >/dev/null 2>&1
) || fail "hook: não conseguiu preparar o repositório temporário"

base_head="$(git -C "$REPO" rev-parse HEAD 2>/dev/null)"
printf 'const key = "%s";\n' "$SB_SECRET" > "$REPO/vazou.ts"
git -C "$REPO" add vazou.ts
if git -C "$REPO" commit -qm "vazamento" >/dev/null 2>&1 || [ "$(git -C "$REPO" rev-parse HEAD)" != "$base_head" ]; then
  fail "hook: commit com segredo deveria ser bloqueado"
else
  ok "hook bloqueia commit com segredo"
fi

git -C "$REPO" rm -q --cached vazou.ts && rm "$REPO/vazou.ts"
printf 'const titulo = "POrganization";\n' > "$REPO/limpo.ts"
git -C "$REPO" add limpo.ts
if git -C "$REPO" commit -qm "limpo" >/dev/null 2>&1; then
  ok "hook deixa passar commit limpo"
else
  fail "hook: commit limpo deveria passar"
fi

if [ "$failures" -gt 0 ]; then
  echo "$failures teste(s) falharam"
  exit 1
fi
echo "todos os testes do check-secrets passaram"
