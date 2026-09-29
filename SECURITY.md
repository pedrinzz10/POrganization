# Segurança

## Reportar uma vulnerabilidade

Não abra issue pública. Use o [aviso privado de vulnerabilidade do GitHub](https://github.com/pedrinzz10/POrganization/security/advisories/new) deste repositório.

## Segredos

O repositório é público. Nenhum segredo entra no git: ele guarda só os **nomes** das variáveis (no `.env.example`), e os valores ficam em cada ambiente.

| Ambiente | Onde ficam os valores |
|---|---|
| Local | `.env` na raiz (ignorado pelo git), criado por `bash scripts/setup-dev.sh` |
| Testes e CI | não usam segredos: banco no Testcontainers e JWT falso do Spring Security Test |
| API (Render) | painel do Render; no `render.yaml` os segredos têm `sync: false` |
| Frontend (Vercel) | só valores públicos: URL do Supabase e a publishable key (`sb_publishable_`) |

Nunca use no projeto a **secret key** (`sb_secret_`) nem a antiga **service_role** do Supabase. O backend acessa o banco pela conexão Postgres e valida tokens pelo JWKS, então não precisa delas.

### Camadas de proteção

1. **`.gitignore`**: ignora `.env`, chaves e certificados. Coberto por `scripts/check-structure.sh`.
2. **Hook pre-commit** (`.githooks/pre-commit`): roda `scripts/check-secrets.sh` no conteúdo staged e o gitleaks, se instalado. Ative com `bash scripts/setup-dev.sh`.
3. **CI** (`.github/workflows/secrets.yml`): check `segredos` em todo push e PR, com `check-secrets`, os testes dele e o gitleaks no histórico inteiro.
4. **GitHub**: secret scanning e push protection ativos; a `main` só recebe mudanças por PR, sem force push. Confira com `bash scripts/check-github-security.sh`.

Falso positivo no `check-secrets`? Troque o valor literal por uma variável (`${VAR}`) ou monte o texto em tempo de execução, como em `scripts/test-check-secrets.sh`.

## Se um segredo vazar

Apagar o arquivo ou reescrever o histórico **não resolve**: o valor pode já ter sido copiado. A ordem é:

1. **Revogar e trocar o segredo na origem, imediatamente.**
   - Senha do banco: Supabase → Project Settings → Database → Reset database password.
   - Chaves de API do Supabase: Project Settings → API Keys → revogar a chave e criar outra.
   - Chaves de assinatura JWT: Project Settings → JWT Keys → rotacionar.
   - Outros (SMTP, Google OAuth, VAPID, `CRON_SECRET`, `GOOGLE_TOKEN_KEY`): gerar novo valor no provedor.
2. Atualizar o novo valor no `.env` local e no painel do Render.
3. Remover o valor do código, com um commit normal.
4. Verificar nos logs do serviço se houve acesso indevido no período.
5. Se o alerta veio do secret scanning do GitHub, fechá-lo como *revoked*.
