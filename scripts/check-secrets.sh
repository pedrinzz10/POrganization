#!/usr/bin/env bash
# Spec B13: procura segredos em arquivos do repositório.
#
# Uso:
#   bash scripts/check-secrets.sh            # arquivos rastreados (git ls-files)
#   bash scripts/check-secrets.sh --staged   # conteúdo staged (usado pelo hook pre-commit)
#   bash scripts/check-secrets.sh ARQ...     # arquivos informados
#
# Sai com 1 se encontrar algo. A saída mostra arquivo, linha e regra, nunca o valor.
set -u

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

findings=0
report() { echo "SEGREDO: $1:$2: $3"; findings=$((findings + 1)); }

# Decodifica o payload (base64url) de um JWT e diz se é service_role.
is_service_role_jwt() {
  local payload="${1#*.}"
  payload="${payload%%.*}"
  payload="$(printf '%s' "$payload" | tr '_-' '/+')"
  while [ $(( ${#payload} % 4 )) -ne 0 ]; do payload="$payload="; done
  printf '%s' "$payload" | base64 -d 2>/dev/null | grep -Eq '"role"[[:space:]]*:[[:space:]]*"service_role"'
}

check_name() {
  local label="$1" base="${1##*/}"
  case "$base" in
    .env.example) ;;
    .env | .env.*) report "$label" 0 "arquivo .env não pode ser versionado" ;;
    *.pem | *.key | *.p12 | *.pfx | *.jks | *.keystore) report "$label" 0 "arquivo de chave/certificado não pode ser versionado" ;;
    id_rsa | id_ecdsa | id_ed25519) report "$label" 0 "chave SSH privada não pode ser versionada" ;;
  esac
}

# $1 = nome exibido, $2 = arquivo com o conteúdo a analisar
check_content() {
  local label="$1" file="$2" base="${1##*/}" line

  grep -Iq . "$file" 2>/dev/null || return 0 # binário ou vazio

  while IFS=: read -r line _; do
    report "$label" "$line" "chave secreta do Supabase (sb_secret_)"
  done < <(grep -nE 'sb_secret_[A-Za-z0-9_-]{10,}' "$file")

  while IFS=: read -r line _; do
    report "$label" "$line" "chave privada"
  done < <(grep -nE -- '-----BEGIN ([A-Z0-9]+ )*PRIVATE KEY-----' "$file")

  while IFS=: read -r line _; do
    report "$label" "$line" "URL de banco com senha"
  done < <(grep -nE '(postgres(ql)?|mysql|mongodb(\+srv)?)://[^:/@[:space:]]+:[^$<{@[:space:]][^@[:space:]]*@' "$file")

  while IFS=: read -r line _; do
    report "$label" "$line" "senha em URL JDBC"
  done < <(grep -niE 'jdbc:[a-z]+://[^[:space:]]*[?&;]password=[^$<{&[:space:]]' "$file")

  local match
  while IFS= read -r match; do
    line="${match%%:*}"
    if is_service_role_jwt "${match#*:}"; then
      report "$label" "$line" "JWT com role service_role"
    fi
  done < <(grep -noE 'eyJ[A-Za-z0-9_-]+\.eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+' "$file")

  case "$base" in
    application*.yml | application*.yaml | application*.properties)
      # chave sensível com valor literal (permitido: vazio, "" ou ${VAR})
      while IFS=: read -r line _; do
        report "$label" "$line" "valor literal em propriedade sensível (use \${VAR})"
      done < <(grep -niE '^[[:space:]]*[a-z0-9._-]*(password|secret|token|private-key|api-key|client-secret)[[:space:]]*[:=][[:space:]]*[^$[:space:]"'"'"'#]' "$file")
      ;;
    .env.example)
      while IFS=: read -r line _; do
        report "$label" "$line" "variável sensível com valor no .env.example (deixe vazia)"
      done < <(grep -nE '^[A-Z0-9_]*(PASSWORD|SECRET|TOKEN|PRIVATE|_KEY)[A-Z0-9_]*=.+' "$file")
      ;;
  esac
}

files=()
mode=args
if [ "$#" -eq 0 ]; then
  mode=tracked
  while IFS= read -r f; do files+=("$f"); done < <(git ls-files)
elif [ "$1" = "--staged" ]; then
  mode=staged
  while IFS= read -r f; do files+=("$f"); done < <(git diff --cached --name-only --diff-filter=ACMR)
else
  files=("$@")
fi

i=0
for f in "${files[@]}"; do
  check_name "$f"
  if [ "$mode" = staged ]; then
    i=$((i + 1))
    git show ":$f" > "$TMP/$i" 2>/dev/null || continue
    check_content "$f" "$TMP/$i"
  elif [ -f "$f" ]; then
    check_content "$f" "$f"
  fi
done

if [ "$findings" -gt 0 ]; then
  echo
  echo "$findings possível(is) segredo(s) encontrado(s). Remova o valor e use variável de ambiente."
  echo "Se um segredo real já foi commitado, troque-o no serviço de origem (veja SECURITY.md)."
  exit 1
fi
echo "check-secrets: nenhum segredo encontrado (${#files[@]} arquivo(s))"
