# Plano de implementação: POrganization

Plano para o Pedro executar no Claude Code local, no repositório `pedrinzz10/POrganization` (monorepo `backend/` + `frontend/`). O plano é uma lista de **specs em JSON**: cada spec é uma entrega pequena e verificável, com ação, arquivos, story, status, critérios de aceite e os testes que provam cada critério.

## 1. Stack e convenções

| Item | Decisão |
|---|---|
| Backend | Java 21, Spring Boot 4.x (última estável, starters modulares), Spring Web MVC, Validation, Data JPA, Flyway (`spring-boot-starter-flyway` + `flyway-database-postgresql`), Security (OAuth2 Resource Server), Actuator; Jackson 3 com `BigDecimal` serializado como string |
| Banco | Supabase Postgres (17), migrações versionadas com Flyway em `backend/src/main/resources/db/migration`; conexão pelo session pooler (porta 5432) |
| Segurança do banco | toda tabela do schema `public` com RLS ativo e sem policies, para a Data API do Supabase (anon key pública) não acessar nada; o backend conecta como `postgres` e não é afetado |
| Auth | Supabase Auth no frontend (`@supabase/supabase-js`); o backend só valida o JWT do Supabase (JWKS com JWT Signing Keys assimétricas, issuer e audience `authenticated`) e usa o `sub` como `user_id` |
| Frontend | Angular 21+ (última estável), standalone components, zoneless, signals, roteamento com lazy loading, Angular Material + CDK; arquivos com sufixo de tipo (`.component.ts`, `.service.ts`) |
| Deploy | API no Render (Docker), Angular na Vercel, banco e auth no Supabase |
| Testes backend | JUnit 5 + AssertJ + Mockito (unitário); `@SpringBootTest`/`@DataJpaTest` + Testcontainers Postgres + MockMvc (integração); GreenMail (e-mail) e WireMock (APIs externas) |
| Testes frontend | Vitest (runner padrão do Angular CLI) com TestBed (componente/serviço), timers com `vi.useFakeTimers()` em vez de `fakeAsync`; Playwright (e2e) |
| Dinheiro | `BigDecimal` no Java, `NUMERIC(14,2)` no banco, arredondamento `HALF_EVEN` só quando necessário (parcelas) |
| Datas | `LocalDate`/`LocalTime`/`OffsetDateTime`; fuso do usuário padrão `America/Sao_Paulo` |
| Multiusuário | toda tabela de dados tem `user_id uuid not null`; todo repositório filtra por usuário; acesso a registro de outro usuário responde **404** |

Pacotes do backend: `com.porganization.{config,security,common,commitments,studies,finance,notifications,integrations,today}`.
Pastas do frontend: `frontend/src/app/{core,shared,layout,features/{auth,today,commitments,studies,finance,settings}}`.

## 2. Esquema da spec

Cada spec é um objeto JSON com estes campos:

```json
{
  "id": "C02",
  "etapa": "2-compromissos",
  "titulo": "API de criação rápida e CRUD de compromissos",
  "acao": "O que o Claude Code deve fazer, em uma ou duas frases imperativas.",
  "story": "Como Pedro, quero ... para ...",
  "arquivos": ["caminho/relativo/ao/repo (criar ou alterar)"],
  "dependencias": ["C01"],
  "conceito_angular": "Só em specs de frontend: o conceito do Angular que a spec introduz, explicado em uma frase.",
  "status": "em_revisao",
  "criterios_de_aceite": [
    { "id": "CA1", "descricao": "Comportamento observável e verificável." }
  ],
  "testes_dos_criterios": [
    {
      "id": "T1",
      "criterio": "CA1",
      "tipo": "unitario | integracao | componente | e2e",
      "arquivo": "caminho do arquivo de teste",
      "cenario": "Dado ... quando ... então ..."
    }
  ]
}
```

Regras:

- `status` segue o ciclo `pendente` → `em_andamento` → `em_revisao` → `concluida`; use `bloqueada` com uma nota quando depender de algo externo. Todas começam em `pendente`.
- Todo critério tem pelo menos um teste em `testes_dos_criterios` apontando para ele pelo campo `criterio`.
- `arquivos` é a melhor previsão; o Claude Code pode criar arquivos auxiliares, mas deve citar os que fugirem da lista no PR.
- `dependencias` diz o que precisa estar `concluida` antes. Dentro de cada etapa a ordem das specs já respeita as dependências.
- Uma spec = um branch = um PR pequeno.

## 3. Como usar com o Claude Code

1. O arquivo `specs.json` na raiz reúne todas as specs deste plano (gerado a partir dos blocos JSON abaixo; ao mudar uma spec aqui, regenere-o). Opcionalmente, extraia cada spec para `specs/<etapa>/<id>.json` e crie `specs/README.md` com este esquema.
2. Para cada spec, na ordem, use um prompt como:
   > Implemente a spec `specs/2-compromissos/C02.json`. Mude o status para `em_andamento`, escreva primeiro os testes de `testes_dos_criterios`, depois o código até todos passarem. No final, rode `./mvnw verify` e/ou `npm test`, marque `em_revisao` e me explique o conceito Angular da spec, se houver.
3. Depois de revisar e fazer merge, mude o status para `concluida`.

## 4. Specs

### Etapa 1: Base

```json
[
  {
    "id": "B01",
    "etapa": "1-base",
    "titulo": "Estrutura do monorepo",
    "acao": "Criar a estrutura backend/ e frontend/, .gitignore unificado, .editorconfig, pasta specs/ e README inicial com visão do projeto.",
    "story": "Como Pedro, quero um monorepo organizado para evoluir API e frontend no mesmo repositório.",
    "arquivos": [".gitignore", ".editorconfig", "README.md", "backend/.gitkeep", "frontend/.gitkeep", "specs/README.md"],
    "dependencias": [],
    "status": "concluida",
    "criterios_de_aceite": [
      { "id": "CA1", "descricao": "As pastas backend/, frontend/ e specs/ existem na raiz." },
      { "id": "CA2", "descricao": ".gitignore ignora target/, node_modules/, dist/, .angular/, .env e arquivos de IDE." }
    ],
    "testes_dos_criterios": [
      { "id": "T1", "criterio": "CA1", "tipo": "integracao", "arquivo": "scripts/check-structure.sh", "cenario": "Dado o repositório clonado, quando o script roda, então sai com código 0 se as três pastas existem." },
      { "id": "T2", "criterio": "CA2", "tipo": "integracao", "arquivo": "scripts/check-structure.sh", "cenario": "Dado um arquivo .env e backend/target/x criados, quando `git check-ignore` roda neles, então ambos são ignorados." }
    ]
  },
  {
    "id": "B13",
    "etapa": "1-base",
    "titulo": "Proteção contra vazamento de segredos",
    "acao": "Criar camadas contra commit de segredos no repositório público: scripts/check-secrets.sh (chaves sb_secret_, JWT com role service_role, chaves privadas, URL de banco com senha, senha literal em application*.yml, arquivos proibidos como .env/.pem/.key/.p12/.jks, e variáveis sensíveis com valor no .env.example), hook pre-commit versionado em .githooks (check-secrets nos arquivos staged + gitleaks quando instalado) ativado por scripts/setup-dev.sh, .gitleaks.toml com regras do Supabase, workflow de CI 'segredos' (check-secrets + gitleaks no histórico inteiro), SECURITY.md com o procedimento de rotação, e no GitHub: secret scanning, push protection e ruleset da main (só via PR, sem force push nem exclusão).",
    "story": "Como Pedro, quero várias barreiras impedindo que senhas e chaves cheguem ao repositório público, e um procedimento claro se algo vazar.",
    "arquivos": ["scripts/check-secrets.sh", "scripts/test-check-secrets.sh", "scripts/setup-dev.sh", "scripts/check-github-security.sh", ".githooks/pre-commit", ".gitleaks.toml", ".github/workflows/secrets.yml", "SECURITY.md", "README.md"],
    "dependencias": ["B01"],
    "status": "concluida",
    "criterios_de_aceite": [
      { "id": "CA1", "descricao": "check-secrets.sh falha para sb_secret_, JWT service_role, chave privada, URL de banco com senha, senha literal em application*.yml e arquivo proibido rastreado; passa para sb_publishable_, JWT anon e ${VAR}. A saída nunca imprime o segredo inteiro." },
      { "id": "CA2", "descricao": "No .env.example, variáveis com nome sensível (PASSWORD, SECRET, TOKEN, PRIVATE, _KEY) ficam sem valor." },
      { "id": "CA3", "descricao": "Com o hook ativo, commit com segredo staged é bloqueado e commit limpo passa." },
      { "id": "CA4", "descricao": "O check 'segredos' roda em todo push e PR, com check-secrets, os testes dele e gitleaks no histórico inteiro." },
      { "id": "CA5", "descricao": "O repositório tem secret scanning e push protection ativos, e a main só recebe mudanças por PR, sem force push nem exclusão." }
    ],
    "testes_dos_criterios": [
      { "id": "T1", "criterio": "CA1", "tipo": "unitario", "arquivo": "scripts/test-check-secrets.sh", "cenario": "Dado fixtures geradas em tempo de execução (para o próprio repositório não conter segredos falsos), quando check-secrets roda em cada uma, então falha nas 6 proibidas, passa nas 3 permitidas e a saída não contém o valor completo." },
      { "id": "T2", "criterio": "CA2", "tipo": "unitario", "arquivo": "scripts/test-check-secrets.sh", "cenario": "Dado .env.example com DB_PASSWORD=abc, então falha; com DB_PASSWORD= e DB_URL=jdbc:postgresql://localhost:5432/app, então passa." },
      { "id": "T3", "criterio": "CA3", "tipo": "integracao", "arquivo": "scripts/test-check-secrets.sh", "cenario": "Dado um repositório git temporário com core.hooksPath=.githooks, quando commita um arquivo com sb_secret_, então o commit falha e não existe; com um arquivo limpo, então o commit é criado." },
      { "id": "T4", "criterio": "CA4", "tipo": "integracao", "arquivo": ".github/workflows/secrets.yml", "cenario": "Dado o PR desta spec, então o check 'segredos' termina com success." },
      { "id": "T5", "criterio": "CA5", "tipo": "integracao", "arquivo": "scripts/check-github-security.sh", "cenario": "Dado gh autenticado, quando o script consulta a API do repositório, então secret scanning e push protection estão enabled e existe ruleset ativo na main com pull_request, non_fast_forward e deletion." }
    ]
  },
  {
    "id": "B02",
    "etapa": "1-base",
    "titulo": "Esqueleto da API Spring Boot com health check",
    "acao": "Gerar o projeto Spring Boot 4.x em backend/ pelo Spring Initializr (Maven wrapper, Java 21) com Web MVC, Validation, Data JPA, Flyway, PostgreSQL, Security, OAuth2 Resource Server, Actuator e Testcontainers, usando os starters modulares do Boot 4 (ex.: spring-boot-starter-flyway + flyway-database-postgresql, pois só flyway-core não é mais autoconfigurado; starters de teste separados para MockMvc, Data JPA e Security; testcontainers-postgresql); expor GET /api/health público; no Actuator expor só health e usar management.endpoint.env.show-values=never e configprops.show-values=never para nenhum segredo aparecer em endpoint ou log.",
    "story": "Como Pedro, quero uma API que sobe localmente e responde um health check para validar o setup.",
    "arquivos": ["backend/pom.xml", "backend/mvnw", "backend/src/main/java/com/porganization/PorganizationApplication.java", "backend/src/main/java/com/porganization/common/HealthController.java", "backend/src/main/resources/application.yml", "backend/src/main/java/com/porganization/security/SecurityConfig.java", "backend/src/test/java/com/porganization/TestcontainersConfiguration.java"],
    "dependencias": ["B01"],
    "status": "concluida",
    "criterios_de_aceite": [
      { "id": "CA1", "descricao": "`./mvnw verify` passa em máquina limpa com Docker disponível." },
      { "id": "CA2", "descricao": "GET /api/health responde 200 com {\"status\":\"UP\"} sem autenticação." },
      { "id": "CA3", "descricao": "O Actuator expõe só /actuator/health; /actuator/env e /actuator/configprops não são acessíveis sem autenticação." }
    ],
    "testes_dos_criterios": [
      { "id": "T1", "criterio": "CA1", "tipo": "integracao", "arquivo": "backend/src/test/java/com/porganization/PorganizationApplicationTests.java", "cenario": "Dado o contexto Spring com Postgres do Testcontainers, quando a aplicação inicia, então o contexto carrega sem erro." },
      { "id": "T2", "criterio": "CA2", "tipo": "integracao", "arquivo": "backend/src/test/java/com/porganization/common/HealthControllerTest.java", "cenario": "Dado nenhum token, quando GET /api/health, então status 200 e body.status == UP." },
      { "id": "T3", "criterio": "CA3", "tipo": "integracao", "arquivo": "backend/src/test/java/com/porganization/common/HealthControllerTest.java", "cenario": "Dado nenhum token, quando GET /actuator/health, então 200; quando GET /actuator/env ou /actuator/configprops, então não é 200." }
    ]
  },
  {
    "id": "B03",
    "etapa": "1-base",
    "titulo": "Conexão com Supabase Postgres e Flyway",
    "acao": "Configurar perfis dev/test/prod com datasource por variáveis de ambiente (DB_URL, DB_USER, DB_PASSWORD); no perfil dev ler o .env da raiz com spring.config.import=optional:file:../.env[.properties]; Flyway habilitado, ddl-auto=validate, e a migração V1 com a tabela user_settings (user_id uuid PK, email text null, timezone text not null default 'America/Sao_Paulo', created_at). Toda tabela criada no schema public (inclusive flyway_schema_history, a partir da V1) deve ter RLS ativo sem policies (`alter table ... enable row level security`), para a Data API do Supabase (anon key exposta no frontend) não ler nem gravar nada; o backend conecta como postgres e não é afetado. Localmente e em produção usar o session pooler do Supabase (porta 5432), pois a conexão direta é só IPv6. Criar classe base de teste com Testcontainers reutilizável, usando a imagem postgres na mesma versão major do projeto Supabase (17 em projetos novos).",
    "story": "Como Pedro, quero que o esquema do banco seja versionado e aplicado automaticamente no Supabase.",
    "arquivos": ["backend/src/main/resources/application.yml", "backend/src/main/resources/application-dev.yml", "backend/src/main/resources/application-prod.yml", "backend/src/main/resources/db/migration/V1__user_settings.sql", "backend/src/test/java/com/porganization/support/IntegrationTest.java", ".env.example", "backend/src/main/resources/db/migration/afterMigrate.sql", "backend/src/main/java/com/porganization/settings/UserSettings.java", "backend/src/main/java/com/porganization/settings/UserSettingsRepository.java"],
    "dependencias": ["B02", "B13"],
    "status": "concluida",
    "criterios_de_aceite": [
      { "id": "CA1", "descricao": "Ao subir, o Flyway aplica V1 e a tabela user_settings existe." },
      { "id": "CA2", "descricao": "Nenhuma credencial fica no código; .env.example lista todas as variáveis." },
      { "id": "CA3", "descricao": "Hibernate valida o esquema (ddl-auto=validate) e falha se entidade e tabela divergirem." },
      { "id": "CA4", "descricao": "Todas as tabelas do schema public têm RLS ativo, inclusive as criadas por migrações futuras." }
    ],
    "testes_dos_criterios": [
      { "id": "T1", "criterio": "CA1", "tipo": "integracao", "arquivo": "backend/src/test/java/com/porganization/support/FlywayMigrationTest.java", "cenario": "Dado o Postgres do Testcontainers, quando o contexto sobe, então flyway_schema_history tem V1 com success=true e information_schema contém user_settings." },
      { "id": "T2", "criterio": "CA2", "tipo": "unitario", "arquivo": "scripts/check-secrets.sh", "cenario": "Dado o repositório, quando scripts/check-secrets.sh (B13) roda, então não há senha literal em application*.yml nem valor em variável sensível do .env.example." },
      { "id": "T3", "criterio": "CA3", "tipo": "integracao", "arquivo": "backend/src/test/java/com/porganization/PorganizationApplicationTests.java", "cenario": "Dado ddl-auto=validate no perfil test, quando o contexto sobe com todas as migrações, então não há SchemaManagementException." },
      { "id": "T4", "criterio": "CA4", "tipo": "integracao", "arquivo": "backend/src/test/java/com/porganization/support/RowLevelSecurityTest.java", "cenario": "Dado todas as migrações aplicadas, quando consulta pg_class.relrowsecurity das tabelas do schema public, então todas são true (o teste roda em toda spec e barra migração nova sem RLS)." },
      { "id": "T5", "criterio": "CA3", "tipo": "integracao", "arquivo": "backend/src/test/java/com/porganization/support/SchemaValidationTest.java", "cenario": "Dado o banco migrado e uma entidade mapeando user_settings com uma coluna que não existe, quando o Hibernate sobe com ddl-auto=validate, então a inicialização falha com SchemaManagementException citando a coluna." }
    ]
  },
  {
    "id": "B04",
    "etapa": "1-base",
    "titulo": "Validação do JWT do Supabase e usuário atual",
    "acao": "Configurar Spring Security como resource server validando o JWT do Supabase pelo JWKS (SUPABASE_JWKS_URI = https://<ref>.supabase.co/auth/v1/.well-known/jwks.json), issuer (SUPABASE_ISSUER = https://<ref>.supabase.co/auth/v1) e audience 'authenticated'; pré-requisito: ativar as JWT Signing Keys assimétricas (ES256) no projeto Supabase, pois com a chave legada HS256 o JWKS não publica chaves; liberar só /api/health e /actuator/health; criar CurrentUser (resolver de argumento) que devolve o UUID do claim sub; configurar CORS para a origem do frontend (FRONTEND_ORIGIN) e GET /api/me, que no primeiro acesso cria user_settings com o email do claim email e o timezone padrão (upsert).",
    "story": "Como Pedro, quero que só eu, logado pelo Supabase, acesse meus dados pela API.",
    "arquivos": ["backend/src/main/java/com/porganization/security/SecurityConfig.java", "backend/src/main/java/com/porganization/security/CurrentUser.java", "backend/src/main/java/com/porganization/security/CurrentUserArgumentResolver.java", "backend/src/main/java/com/porganization/security/MeController.java", "backend/src/main/resources/application.yml", ".env.example"],
    "dependencias": ["B03"],
    "status": "concluida",
    "criterios_de_aceite": [
      { "id": "CA1", "descricao": "Requisição sem token ou com token inválido/expirado em /api/** responde 401." },
      { "id": "CA2", "descricao": "Com token válido, GET /api/me devolve o userId igual ao sub do token." },
      { "id": "CA3", "descricao": "Preflight CORS da origem configurada é aceito; de outra origem é recusado." },
      { "id": "CA4", "descricao": "Token com issuer ou audience diferente do configurado responde 401." },
      { "id": "CA5", "descricao": "O primeiro GET /api/me cria user_settings com o email do token; os seguintes não duplicam." }
    ],
    "testes_dos_criterios": [
      { "id": "T1", "criterio": "CA1", "tipo": "integracao", "arquivo": "backend/src/test/java/com/porganization/security/SecurityConfigTest.java", "cenario": "Dado nenhum token e depois um token com exp no passado, quando GET /api/me, então 401 nos dois casos." },
      { "id": "T2", "criterio": "CA2", "tipo": "integracao", "arquivo": "backend/src/test/java/com/porganization/security/MeControllerTest.java", "cenario": "Dado MockMvc com jwt().jwt(j -> j.subject(uuid)), quando GET /api/me, então body.userId == uuid." },
      { "id": "T3", "criterio": "CA3", "tipo": "integracao", "arquivo": "backend/src/test/java/com/porganization/security/CorsTest.java", "cenario": "Dado OPTIONS com Origin igual a FRONTEND_ORIGIN, então Access-Control-Allow-Origin presente; com Origin http://evil.test, então 403." },
      { "id": "T4", "criterio": "CA4", "tipo": "integracao", "arquivo": "backend/src/test/java/com/porganization/security/SecurityConfigTest.java", "cenario": "Dado JwtDecoder da aplicação apontando para um JWKS de teste, quando chega token assinado com iss 'https://outro.supabase.co/auth/v1' ou aud 'anon', então 401; com iss e aud corretos, então 200." },
      { "id": "T5", "criterio": "CA5", "tipo": "integracao", "arquivo": "backend/src/test/java/com/porganization/security/MeControllerTest.java", "cenario": "Dado jwt com sub=uuid e claim email 'pedro@teste.com', quando GET /api/me duas vezes, então user_settings tem 1 linha com esse email e timezone America/Sao_Paulo." }
    ]
  },
  {
    "id": "B05",
    "etapa": "1-base",
    "titulo": "Padrão de erros da API",
    "acao": "Criar @RestControllerAdvice que devolve ProblemDetail (RFC 9457) para validação (400 com lista de campos), NotFoundException (404) e erro inesperado (500 sem stack trace). Criar a exceção NotFoundException usada por todos os módulos.",
    "story": "Como Pedro, quero erros previsíveis da API para o frontend mostrar mensagens claras.",
    "arquivos": ["backend/src/main/java/com/porganization/common/GlobalExceptionHandler.java", "backend/src/main/java/com/porganization/common/NotFoundException.java"],
    "dependencias": ["B04"],
    "status": "concluida",
    "criterios_de_aceite": [
      { "id": "CA1", "descricao": "Body inválido em qualquer endpoint devolve 400 com errors[{field,message}]." },
      { "id": "CA2", "descricao": "NotFoundException vira 404 com title 'Não encontrado'." },
      { "id": "CA3", "descricao": "Exceção não tratada vira 500 sem expor stack trace ou mensagem interna." }
    ],
    "testes_dos_criterios": [
      { "id": "T1", "criterio": "CA1", "tipo": "integracao", "arquivo": "backend/src/test/java/com/porganization/common/GlobalExceptionHandlerTest.java", "cenario": "Dado um controller de teste com @Valid DTO, quando POST com campo obrigatório vazio, então 400 e errors contém o campo." },
      { "id": "T2", "criterio": "CA2", "tipo": "integracao", "arquivo": "backend/src/test/java/com/porganization/common/GlobalExceptionHandlerTest.java", "cenario": "Dado endpoint que lança NotFoundException, então 404 e title correto." },
      { "id": "T3", "criterio": "CA3", "tipo": "integracao", "arquivo": "backend/src/test/java/com/porganization/common/GlobalExceptionHandlerTest.java", "cenario": "Dado endpoint que lança RuntimeException('segredo'), então 500 e o body não contém 'segredo'." }
    ]
  },
  {
    "id": "B06",
    "etapa": "1-base",
    "titulo": "Esqueleto do Angular",
    "acao": "Gerar o app Angular em frontend/ com `ng new` (Angular 21+: standalone, zoneless e Vitest por padrão; SCSS, roteamento), adicionar Angular Material, criar os environments com `ng generate environments` (apiUrl, supabaseUrl, supabaseKey com a publishable key sb_publishable_, que é pública por design; nunca a secret key) e registrar o locale pt-BR em app.config.ts. Manter a convenção de nomes com sufixo usada nas specs: configurar os schematics em angular.json para gerar com sufixo de tipo (component, service, guard, interceptor, pipe) e renomear app.ts para app.component.ts (classe AppComponent).",
    "story": "Como Pedro, quero um frontend Angular rodando localmente para começar as telas.",
    "arquivos": ["frontend/package.json", "frontend/angular.json", "frontend/src/main.ts", "frontend/src/app/app.config.ts", "frontend/src/app/app.routes.ts", "frontend/src/app/app.component.ts", "frontend/src/environments/environment.ts", "frontend/src/environments/environment.development.ts"],
    "dependencias": ["B01"],
    "conceito_angular": "Standalone components e app.config.ts: o app é montado por providers (roteador, HttpClient, locale) declarados num único lugar, sem NgModule.",
    "status": "concluida",
    "criterios_de_aceite": [
      { "id": "CA1", "descricao": "`npm start` sobe o app e `npm test` e `npm run build` passam." },
      { "id": "CA2", "descricao": "O pipe currency formata 1234.5 como 'R$ 1.234,50' (locale pt-BR ativo)." }
    ],
    "testes_dos_criterios": [
      { "id": "T1", "criterio": "CA1", "tipo": "componente", "arquivo": "frontend/src/app/app.component.spec.ts", "cenario": "Dado TestBed com AppComponent, quando cria o componente, então ele existe e renderiza um <router-outlet>." },
      { "id": "T2", "criterio": "CA2", "tipo": "unitario", "arquivo": "frontend/src/app/core/locale.spec.ts", "cenario": "Dado CurrencyPipe com LOCALE_ID pt-BR, quando transform(1234.5, 'BRL'), então retorna 'R$ 1.234,50' (normalizando espaço não separável)." }
    ]
  },
  {
    "id": "B07",
    "etapa": "1-base",
    "titulo": "Login e cadastro com Supabase Auth",
    "acao": "Criar AuthService com supabase-js que expõe session e user como signals, métodos signIn, signUp e signOut; criar LoginComponent com formulário reativo (e-mail, senha) e mensagens de erro.",
    "story": "Como Pedro, quero entrar com e-mail e senha para acessar meus dados em qualquer dispositivo.",
    "arquivos": ["frontend/src/app/core/auth/supabase.client.ts", "frontend/src/app/core/auth/auth.service.ts", "frontend/src/app/features/auth/login/login.component.ts", "frontend/src/app/features/auth/login/login.component.html", "frontend/src/app/features/auth/signup/signup.component.ts"],
    "dependencias": ["B06"],
    "conceito_angular": "Serviços com injeção de dependência (inject()) e signals: o AuthService é um singleton que guarda o estado do login e qualquer componente reage quando ele muda. Reactive Forms validam os campos.",
    "status": "concluida",
    "criterios_de_aceite": [
      { "id": "CA1", "descricao": "Botão Entrar fica desabilitado enquanto e-mail for inválido ou senha tiver menos de 6 caracteres." },
      { "id": "CA2", "descricao": "Login com sucesso navega para /hoje; erro do Supabase aparece como mensagem na tela." },
      { "id": "CA3", "descricao": "Sessão persiste ao recarregar a página; signOut limpa a sessão e volta para /login." }
    ],
    "testes_dos_criterios": [
      { "id": "T1", "criterio": "CA1", "tipo": "componente", "arquivo": "frontend/src/app/features/auth/login/login.component.spec.ts", "cenario": "Dado e-mail 'abc', quando renderiza, então botão Entrar está disabled; com e-mail válido e senha de 6+, fica enabled." },
      { "id": "T2", "criterio": "CA2", "tipo": "componente", "arquivo": "frontend/src/app/features/auth/login/login.component.spec.ts", "cenario": "Dado AuthService mockado resolvendo sucesso, quando submit, então Router.navigate(['/hoje']); rejeitando 'Invalid login', então a mensagem aparece no DOM." },
      { "id": "T3", "criterio": "CA3", "tipo": "unitario", "arquivo": "frontend/src/app/core/auth/auth.service.spec.ts", "cenario": "Dado client supabase mockado com sessão salva, quando o serviço inicia, então session() não é nulo; após signOut, session() é nulo e navega para /login." }
    ]
  },
  {
    "id": "B08",
    "etapa": "1-base",
    "titulo": "Guard de rotas e interceptor com token",
    "acao": "Criar authGuard funcional que redireciona para /login sem sessão, e authInterceptor funcional que adiciona Authorization: Bearer <access_token> nas chamadas para environment.apiUrl e faz signOut em 401.",
    "story": "Como Pedro, quero que as telas privadas só abram logado e que a API receba meu token automaticamente.",
    "arquivos": ["frontend/src/app/core/auth/auth.guard.ts", "frontend/src/app/core/auth/auth.interceptor.ts", "frontend/src/app/app.config.ts", "frontend/src/app/app.routes.ts"],
    "dependencias": ["B07", "B04"],
    "conceito_angular": "Guards (CanActivateFn) decidem se uma rota abre; interceptors (HttpInterceptorFn) alteram toda requisição do HttpClient num único ponto.",
    "status": "concluida",
    "criterios_de_aceite": [
      { "id": "CA1", "descricao": "Sem sessão, acessar /hoje redireciona para /login." },
      { "id": "CA2", "descricao": "Chamadas para apiUrl levam o header Bearer; chamadas para outros domínios não." },
      { "id": "CA3", "descricao": "Resposta 401 da API faz logout e leva para /login." }
    ],
    "testes_dos_criterios": [
      { "id": "T1", "criterio": "CA1", "tipo": "unitario", "arquivo": "frontend/src/app/core/auth/auth.guard.spec.ts", "cenario": "Dado AuthService com session null, quando o guard roda, então retorna UrlTree para /login." },
      { "id": "T2", "criterio": "CA2", "tipo": "unitario", "arquivo": "frontend/src/app/core/auth/auth.interceptor.spec.ts", "cenario": "Dado HttpTestingController, quando GET apiUrl+'/me' e GET https://outro.com, então só a primeira tem Authorization." },
      { "id": "T3", "criterio": "CA3", "tipo": "unitario", "arquivo": "frontend/src/app/core/auth/auth.interceptor.spec.ts", "cenario": "Dado resposta 401, então AuthService.signOut foi chamado uma vez." }
    ]
  },
  {
    "id": "B09",
    "etapa": "1-base",
    "titulo": "Layout e navegação",
    "acao": "Criar ShellComponent com toolbar, menu lateral (Hoje, Compromissos, Estudos, Finanças, Configurações) responsivo e botão Sair; rotas filhas com lazy loading (loadComponent) e páginas placeholder para cada seção.",
    "story": "Como Pedro, quero navegar entre as áreas do app pelo celular e pelo computador.",
    "arquivos": ["frontend/src/app/layout/shell/shell.component.ts", "frontend/src/app/layout/shell/shell.component.html", "frontend/src/app/app.routes.ts", "frontend/src/app/features/today/today.page.ts", "frontend/src/app/features/commitments/commitments.page.ts", "frontend/src/app/features/studies/studies.page.ts", "frontend/src/app/features/finance/finance.page.ts", "frontend/src/app/features/settings/settings.page.ts"],
    "dependencias": ["B08"],
    "conceito_angular": "Roteamento com rotas filhas e lazy loading: cada seção é carregada só quando acessada; routerLink e routerLinkActive destacam o item atual.",
    "status": "concluida",
    "criterios_de_aceite": [
      { "id": "CA1", "descricao": "Os 5 itens do menu navegam para suas rotas e o item ativo fica destacado." },
      { "id": "CA2", "descricao": "Em largura < 768px o menu vira gaveta aberta por botão." },
      { "id": "CA3", "descricao": "A rota raiz / redireciona para /hoje." }
    ],
    "testes_dos_criterios": [
      { "id": "T1", "criterio": "CA1", "tipo": "e2e", "arquivo": "frontend/e2e/navigation.spec.ts", "cenario": "Dado usuário logado (sessão mockada), quando clica em cada item, então a URL muda e o item tem a classe ativa." },
      { "id": "T2", "criterio": "CA2", "tipo": "e2e", "arquivo": "frontend/e2e/navigation.spec.ts", "cenario": "Dado viewport 375x800, então o menu está oculto e aparece ao clicar no botão de menu." },
      { "id": "T3", "criterio": "CA3", "tipo": "unitario", "arquivo": "frontend/src/app/app.routes.spec.ts", "cenario": "Dado o RouterTestingHarness, quando navega para '/', então a URL final é '/hoje'." }
    ]
  },
  {
    "id": "B10",
    "etapa": "1-base",
    "titulo": "CI no GitHub Actions",
    "acao": "Criar workflow que em push e PR roda `./mvnw verify` no backend e `npm ci && npm test -- --watch=false && npm run build` no frontend, com cache de Maven e npm, disparando só quando a pasta correspondente muda.",
    "story": "Como Pedro, quero que cada PR seja testado automaticamente antes do merge.",
    "arquivos": [".github/workflows/backend.yml", ".github/workflows/frontend.yml"],
    "dependencias": ["B02", "B06"],
    "status": "concluida",
    "criterios_de_aceite": [
      { "id": "CA1", "descricao": "Um PR que altera backend/ roda o job do backend e ele fica verde." },
      { "id": "CA2", "descricao": "Um PR que altera frontend/ roda o job do frontend e ele fica verde." },
      { "id": "CA3", "descricao": "Um teste quebrado deixa o check vermelho." }
    ],
    "testes_dos_criterios": [
      { "id": "T1", "criterio": "CA1", "tipo": "integracao", "arquivo": ".github/workflows/backend.yml", "cenario": "Dado um PR de teste mudando backend/, então o check 'backend' termina com success." },
      { "id": "T2", "criterio": "CA2", "tipo": "integracao", "arquivo": ".github/workflows/frontend.yml", "cenario": "Dado um PR de teste mudando frontend/, então o check 'frontend' termina com success." },
      { "id": "T3", "criterio": "CA3", "tipo": "integracao", "arquivo": ".github/workflows/backend.yml", "cenario": "Dado um commit temporário com assertEquals(1, 2), então o check falha (reverter em seguida)." }
    ]
  },
  {
    "id": "B11",
    "etapa": "1-base",
    "titulo": "Deploy: Render, Vercel e Supabase",
    "acao": "Criar Dockerfile multi-stage do backend (usuário não root, sem .env na imagem via .dockerignore) e render.yaml (variáveis DB_*, SUPABASE_JWKS_URI, FRONTEND_ORIGIN, porta via $PORT; segredos declarados com sync: false, com valor só no painel do Render); criar vercel.json com rewrite de SPA e build de produção com environment.ts de produção; no Render usar a URL do session pooler do Supabase (porta 5432), porque a conexão direta é só IPv6 e o modo transaction (6543) quebra os prepared statements do JDBC e o lock do Flyway; limitar o pool do Hikari (maximum-pool-size 5) e incluir SUPABASE_ISSUER nas variáveis.",
    "story": "Como Pedro, quero o app publicado para usar no celular e mostrar no portfólio.",
    "arquivos": ["backend/Dockerfile", "backend/.dockerignore", "render.yaml", "frontend/vercel.json", "frontend/src/environments/environment.ts", "frontend/playwright.prod.config.ts"],
    "dependencias": ["B09", "B10"],
    "status": "bloqueada",
    "nota": "Arquivos de deploy e CA1 prontos. CA2 e CA3 dependem do deploy real com as contas do Pedro (Render, Vercel e Supabase); os testes estão prontos em frontend/e2e/smoke-prod.spec.ts (npm run e2e:prod).",
    "criterios_de_aceite": [
      { "id": "CA1", "descricao": "`docker build` do backend gera imagem que sobe e responde /api/health." },
      { "id": "CA2", "descricao": "Recarregar a página em /financas na Vercel não dá 404 (rewrite SPA)." },
      { "id": "CA3", "descricao": "Login em produção funciona e GET /api/me responde 200 pela URL do Render." }
    ],
    "testes_dos_criterios": [
      { "id": "T1", "criterio": "CA1", "tipo": "integracao", "arquivo": "scripts/smoke-docker.sh", "cenario": "Dado docker build e docker run com envs de teste, quando curl /api/health, então 200." },
      { "id": "T2", "criterio": "CA2", "tipo": "e2e", "arquivo": "frontend/e2e/smoke-prod.spec.ts", "cenario": "Dado BASE_URL da Vercel, quando page.goto('/financas') direto, então a página carrega o app (não 404)." },
      { "id": "T3", "criterio": "CA3", "tipo": "e2e", "arquivo": "frontend/e2e/smoke-prod.spec.ts", "cenario": "Dado usuário de teste, quando faz login em produção, então chega em /hoje e a chamada /api/me retorna 200." }
    ]
  },
  {
    "id": "B12",
    "etapa": "1-base",
    "titulo": "README completo",
    "acao": "Escrever README com visão do produto, arquitetura (diagrama simples), como rodar local (Docker, .env), como rodar testes, deploy, e o fluxo de specs.",
    "story": "Como Pedro, quero um README de portfólio que explique o projeto e deixe qualquer pessoa rodá-lo.",
    "arquivos": ["README.md", "docs/arquitetura.md"],
    "dependencias": ["B11"],
    "status": "em_revisao",
    "criterios_de_aceite": [
      { "id": "CA1", "descricao": "Seguindo só o README, em máquina nova, backend e frontend sobem localmente." },
      { "id": "CA2", "descricao": "O README lista todas as variáveis de ambiente usadas no código." }
    ],
    "testes_dos_criterios": [
      { "id": "T1", "criterio": "CA1", "tipo": "e2e", "arquivo": "README.md", "cenario": "Dado um clone novo, quando executo os comandos da seção 'Rodando localmente' em ordem, então /api/health e http://localhost:4200 respondem." },
      { "id": "T2", "criterio": "CA2", "tipo": "unitario", "arquivo": "scripts/check-env-docs.sh", "cenario": "Dado os nomes ${VAR} em application*.yml e environment*.ts, então cada um aparece no README e em .env.example." }
    ]
  }
]
```

### Etapa 2: Compromissos

```json
[
  {
    "id": "C01",
    "etapa": "2-compromissos",
    "titulo": "Modelo de compromissos",
    "acao": "Criar migração V2 com tabela commitments (id uuid, user_id, title, date, start_time null, end_time null, all_day, description, location, done, recurrence_rule jsonb null, created_at, updated_at) com índice (user_id, date); entidade JPA e repositório com consultas sempre filtradas por user_id.",
    "story": "Como Pedro, quero guardar meus compromissos com o mínimo de dados obrigatórios.",
    "arquivos": ["backend/src/main/resources/db/migration/V2__commitments.sql", "backend/src/main/java/com/porganization/commitments/Commitment.java", "backend/src/main/java/com/porganization/commitments/CommitmentRepository.java"],
    "dependencias": ["B05"],
    "status": "em_revisao",
    "criterios_de_aceite": [
      { "id": "CA1", "descricao": "Só title, date e user_id são obrigatórios; o resto é opcional." },
      { "id": "CA2", "descricao": "O repositório não retorna compromissos de outro usuário." }
    ],
    "testes_dos_criterios": [
      { "id": "T1", "criterio": "CA1", "tipo": "integracao", "arquivo": "backend/src/test/java/com/porganization/commitments/CommitmentRepositoryTest.java", "cenario": "Dado um Commitment só com title, date e user_id, quando save, então persiste; sem title, então falha por constraint." },
      { "id": "T2", "criterio": "CA2", "tipo": "integracao", "arquivo": "backend/src/test/java/com/porganization/commitments/CommitmentRepositoryTest.java", "cenario": "Dado compromissos do usuário A e B, quando findByUserIdAndDateBetween(A, ...), então só vêm os de A." }
    ]
  },
  {
    "id": "C02",
    "etapa": "2-compromissos",
    "titulo": "API de criação rápida e CRUD de compromissos",
    "acao": "Criar CommitmentController/Service com POST /api/commitments (DTO com title, date, startTime obrigatórios para criação rápida, resto opcional), GET /api/commitments/{id}, PUT, DELETE, todos escopados ao usuário atual.",
    "story": "Como Pedro, quero criar um compromisso em segundos só com título, dia e hora.",
    "arquivos": ["backend/src/main/java/com/porganization/commitments/CommitmentController.java", "backend/src/main/java/com/porganization/commitments/CommitmentService.java", "backend/src/main/java/com/porganization/commitments/dto/CommitmentRequest.java", "backend/src/main/java/com/porganization/commitments/dto/CommitmentResponse.java"],
    "dependencias": ["C01"],
    "status": "pendente",
    "criterios_de_aceite": [
      { "id": "CA1", "descricao": "POST com title, date e startTime responde 201 com Location e o compromisso criado." },
      { "id": "CA2", "descricao": "POST sem title responde 400 apontando o campo." },
      { "id": "CA3", "descricao": "GET/PUT/DELETE de compromisso de outro usuário responde 404." }
    ],
    "testes_dos_criterios": [
      { "id": "T1", "criterio": "CA1", "tipo": "integracao", "arquivo": "backend/src/test/java/com/porganization/commitments/CommitmentControllerIT.java", "cenario": "Dado jwt do usuário A, quando POST {title:'Dentista', date:'2026-10-02', startTime:'14:00'}, então 201 e GET do Location retorna o mesmo título." },
      { "id": "T2", "criterio": "CA2", "tipo": "integracao", "arquivo": "backend/src/test/java/com/porganization/commitments/CommitmentControllerIT.java", "cenario": "Dado POST sem title, então 400 e errors[0].field == 'title'." },
      { "id": "T3", "criterio": "CA3", "tipo": "integracao", "arquivo": "backend/src/test/java/com/porganization/commitments/CommitmentControllerIT.java", "cenario": "Dado compromisso de A, quando B faz GET, PUT e DELETE nele, então 404 nos três e o registro continua intacto." }
    ]
  },
  {
    "id": "C03",
    "etapa": "2-compromissos",
    "titulo": "Consulta por intervalo",
    "acao": "Criar GET /api/commitments?from=YYYY-MM-DD&to=YYYY-MM-DD que devolve os compromissos do intervalo ordenados por data e hora (sem hora primeiro, como 'dia todo'); limitar o intervalo a 400 dias.",
    "story": "Como Pedro, quero ver meus compromissos de um dia, semana, mês ou ano.",
    "arquivos": ["backend/src/main/java/com/porganization/commitments/CommitmentController.java", "backend/src/main/java/com/porganization/commitments/CommitmentService.java"],
    "dependencias": ["C02"],
    "status": "em_revisao",
    "criterios_de_aceite": [
      { "id": "CA1", "descricao": "Retorna só itens com from <= date <= to, ordenados por date, depois all_day primeiro, depois start_time." },
      { "id": "CA2", "descricao": "Intervalo com to < from ou maior que 400 dias responde 400." }
    ],
    "testes_dos_criterios": [
      { "id": "T1", "criterio": "CA1", "tipo": "integracao", "arquivo": "backend/src/test/java/com/porganization/commitments/CommitmentRangeIT.java", "cenario": "Dado itens em 01/10 10:00, 01/10 dia todo, 02/10 08:00 e 10/10, quando from=01/10&to=02/10, então vem [dia todo, 10:00, 08:00 de 02/10]." },
      { "id": "T2", "criterio": "CA2", "tipo": "integracao", "arquivo": "backend/src/test/java/com/porganization/commitments/CommitmentRangeIT.java", "cenario": "Dado from=2026-10-10&to=2026-10-01, então 400; dado intervalo de 401 dias, então 400." }
    ]
  },
  {
    "id": "C04",
    "etapa": "2-compromissos",
    "titulo": "Regra de recorrência e expansão",
    "acao": "Criar RecurrenceRule (freq DAILY|WEEKLY|MONTHLY|YEARLY, interval, byWeekDays, until ou count) e RecurrenceExpander puro que gera as datas de ocorrência dentro de [from, to]; a consulta por intervalo passa a devolver ocorrências (id da série + occurrenceDate).",
    "story": "Como Pedro, quero cadastrar uma vez algo que se repete (aula toda terça, academia seg/qua/sex) e vê-lo em todos os dias certos.",
    "arquivos": ["backend/src/main/java/com/porganization/commitments/recurrence/RecurrenceRule.java", "backend/src/main/java/com/porganization/commitments/recurrence/RecurrenceExpander.java", "backend/src/main/java/com/porganization/commitments/CommitmentService.java", "backend/src/main/java/com/porganization/commitments/dto/OccurrenceResponse.java"],
    "dependencias": ["C03"],
    "status": "em_revisao",
    "criterios_de_aceite": [
      { "id": "CA1", "descricao": "WEEKLY com byWeekDays [MON,WED,FRI] a partir de 2026-10-01 gera 05, 07 e 09/10 na semana seguinte." },
      { "id": "CA2", "descricao": "MONTHLY no dia 31 cai no último dia dos meses menores (30/11, 28/02)." },
      { "id": "CA3", "descricao": "until e count encerram a série; sem nenhum dos dois ela é infinita, mas só o intervalo pedido é expandido." }
    ],
    "testes_dos_criterios": [
      { "id": "T1", "criterio": "CA1", "tipo": "unitario", "arquivo": "backend/src/test/java/com/porganization/commitments/recurrence/RecurrenceExpanderTest.java", "cenario": "Dado a regra semanal, quando expand(2026-10-05, 2026-10-11), então [05, 07, 09]." },
      { "id": "T2", "criterio": "CA2", "tipo": "unitario", "arquivo": "backend/src/test/java/com/porganization/commitments/recurrence/RecurrenceExpanderTest.java", "cenario": "Dado início em 2026-10-31 mensal, quando expand até 2027-02-28, então [31/10, 30/11, 31/12, 31/01, 28/02]." },
      { "id": "T3", "criterio": "CA3", "tipo": "unitario", "arquivo": "backend/src/test/java/com/porganization/commitments/recurrence/RecurrenceExpanderTest.java", "cenario": "Dado DAILY count=3, então 3 datas mesmo com intervalo de 10 dias; dado DAILY sem fim e intervalo de 7 dias, então 7 datas." }
    ]
  },
  {
    "id": "C05",
    "etapa": "2-compromissos",
    "titulo": "Concluir e editar ocorrências",
    "acao": "Criar tabela commitment_exceptions (commitment_id, occurrence_date, done, cancelled, override_title, override_time) e endpoints PATCH /api/commitments/{id}/occurrences/{date} (done, cancelar, alterar só esta) e PATCH /api/commitments/{id}/done para não recorrentes.",
    "story": "Como Pedro, quero marcar como feito um compromisso (ou só a ocorrência de hoje de um recorrente) e ajustar uma ocorrência sem mexer na série.",
    "arquivos": ["backend/src/main/resources/db/migration/V3__commitment_exceptions.sql", "backend/src/main/java/com/porganization/commitments/CommitmentException.java", "backend/src/main/java/com/porganization/commitments/CommitmentService.java", "backend/src/main/java/com/porganization/commitments/CommitmentController.java"],
    "dependencias": ["C04"],
    "status": "pendente",
    "criterios_de_aceite": [
      { "id": "CA1", "descricao": "Marcar done numa ocorrência afeta só aquela data." },
      { "id": "CA2", "descricao": "Cancelar uma ocorrência a remove da consulta por intervalo." },
      { "id": "CA3", "descricao": "Alterar horário de uma ocorrência muda só aquela data na consulta." }
    ],
    "testes_dos_criterios": [
      { "id": "T1", "criterio": "CA1", "tipo": "integracao", "arquivo": "backend/src/test/java/com/porganization/commitments/OccurrenceIT.java", "cenario": "Dado série diária, quando PATCH done=true em 03/10, então a consulta de 02 a 04/10 mostra done só em 03/10." },
      { "id": "T2", "criterio": "CA2", "tipo": "integracao", "arquivo": "backend/src/test/java/com/porganization/commitments/OccurrenceIT.java", "cenario": "Dado cancelamento em 03/10, então a consulta de 02 a 04/10 tem 2 ocorrências." },
      { "id": "T3", "criterio": "CA3", "tipo": "integracao", "arquivo": "backend/src/test/java/com/porganization/commitments/OccurrenceIT.java", "cenario": "Dado override_time 18:00 em 03/10 numa série às 08:00, então 03/10 vem 18:00 e 04/10 vem 08:00." }
    ]
  },
  {
    "id": "C06",
    "etapa": "2-compromissos",
    "titulo": "Serviço Angular e criação rápida",
    "acao": "Criar CommitmentsService (HttpClient, tipos TypeScript dos DTOs) e QuickAddComponent com uma linha: título, data (padrão hoje) e hora, Enter salva e limpa o campo; exibir snackbar de sucesso ou erro.",
    "story": "Como Pedro, quero digitar título, dia e hora e salvar com Enter, sem abrir formulário grande.",
    "arquivos": ["frontend/src/app/features/commitments/data/commitments.service.ts", "frontend/src/app/features/commitments/data/commitment.model.ts", "frontend/src/app/features/commitments/quick-add/quick-add.component.ts", "frontend/src/app/features/commitments/quick-add/quick-add.component.html"],
    "dependencias": ["C02", "B09"],
    "conceito_angular": "HttpClient devolve Observables (RxJS): a requisição só acontece quando alguém faz subscribe. output() emite um evento do componente filho para o pai avisar que algo foi criado.",
    "status": "pendente",
    "criterios_de_aceite": [
      { "id": "CA1", "descricao": "Com título preenchido, Enter chama POST com title, date e startTime e limpa o título." },
      { "id": "CA2", "descricao": "Título vazio não envia nada e mostra erro no campo." },
      { "id": "CA3", "descricao": "Após salvar, o componente emite (created) com o compromisso criado." }
    ],
    "testes_dos_criterios": [
      { "id": "T1", "criterio": "CA1", "tipo": "componente", "arquivo": "frontend/src/app/features/commitments/quick-add/quick-add.component.spec.ts", "cenario": "Dado HttpTestingController, quando digita 'Dentista', hora 14:00 e Enter, então há um POST com o body esperado e o input fica vazio após a resposta." },
      { "id": "T2", "criterio": "CA2", "tipo": "componente", "arquivo": "frontend/src/app/features/commitments/quick-add/quick-add.component.spec.ts", "cenario": "Dado título vazio, quando Enter, então expectNone(POST) e mat-error visível." },
      { "id": "T3", "criterio": "CA3", "tipo": "componente", "arquivo": "frontend/src/app/features/commitments/quick-add/quick-add.component.spec.ts", "cenario": "Dado resposta 201, então o spy de created recebeu o objeto retornado." }
    ]
  },
  {
    "id": "C07",
    "etapa": "2-compromissos",
    "titulo": "Visões Hoje e Semana",
    "acao": "Criar CommitmentsPage com abas Hoje/Semana/Mês/Ano (Mês e Ano ficam na C08); implementar Hoje (lista cronológica com checkbox de concluído) e Semana (7 colunas seg a dom com navegação anterior/próxima).",
    "story": "Como Pedro, quero ver o que tenho hoje e na semana e marcar o que já fiz.",
    "arquivos": ["frontend/src/app/features/commitments/commitments.page.ts", "frontend/src/app/features/commitments/views/day-view.component.ts", "frontend/src/app/features/commitments/views/week-view.component.ts", "frontend/src/app/features/commitments/data/date-range.util.ts"],
    "dependencias": ["C05", "C06"],
    "conceito_angular": "input() recebe dados do componente pai; computed() deriva valores de signals (ex.: agrupar ocorrências por dia) e recalcula sozinho quando a lista muda. @for e @if são o controle de fluxo nos templates.",
    "status": "pendente",
    "criterios_de_aceite": [
      { "id": "CA1", "descricao": "Hoje lista só as ocorrências do dia, com os sem hora no topo." },
      { "id": "CA2", "descricao": "Marcar o checkbox chama o PATCH certo (série ou ocorrência) e risca o item." },
      { "id": "CA3", "descricao": "Semana começa na segunda e 'próxima' carrega a semana seguinte." }
    ],
    "testes_dos_criterios": [
      { "id": "T1", "criterio": "CA1", "tipo": "componente", "arquivo": "frontend/src/app/features/commitments/views/day-view.component.spec.ts", "cenario": "Dado input com 3 ocorrências (uma dia todo), então a primeira linha é a de dia todo." },
      { "id": "T2", "criterio": "CA2", "tipo": "componente", "arquivo": "frontend/src/app/features/commitments/views/day-view.component.spec.ts", "cenario": "Dado ocorrência recorrente de 03/10, quando clica no checkbox, então PATCH /occurrences/2026-10-03 com done=true e o item recebe a classe 'done'." },
      { "id": "T3", "criterio": "CA3", "tipo": "unitario", "arquivo": "frontend/src/app/features/commitments/data/date-range.util.spec.ts", "cenario": "Dado quinta 2026-10-01, então weekRange retorna 2026-09-28 a 2026-10-04; nextWeek retorna 2026-10-05 a 2026-10-11." }
    ]
  },
  {
    "id": "C08",
    "etapa": "2-compromissos",
    "titulo": "Visões Mês e Ano",
    "acao": "Implementar MonthView (grade 6x7 com contagem e até 3 títulos por dia, clique abre o dia) e YearView (12 mini-meses com marcação de dias com compromisso); ambos consultam o intervalo inteiro numa chamada.",
    "story": "Como Pedro, quero enxergar o mês e o ano para planejar com antecedência.",
    "arquivos": ["frontend/src/app/features/commitments/views/month-view.component.ts", "frontend/src/app/features/commitments/views/year-view.component.ts", "frontend/src/app/features/commitments/data/date-range.util.ts"],
    "dependencias": ["C07"],
    "conceito_angular": "Componentes de apresentação reutilizáveis: o mesmo mini-mês é usado 12 vezes no ano, recebendo o mês por input().",
    "status": "pendente",
    "criterios_de_aceite": [
      { "id": "CA1", "descricao": "A grade do mês de outubro/2026 começa em 28/09 e tem 42 células." },
      { "id": "CA2", "descricao": "Dia com mais de 3 compromissos mostra 3 títulos e '+N'." },
      { "id": "CA3", "descricao": "A visão Ano faz uma única requisição de 01/01 a 31/12." }
    ],
    "testes_dos_criterios": [
      { "id": "T1", "criterio": "CA1", "tipo": "unitario", "arquivo": "frontend/src/app/features/commitments/data/date-range.util.spec.ts", "cenario": "Dado monthGrid(2026, 10), então length 42 e primeiro dia 2026-09-28." },
      { "id": "T2", "criterio": "CA2", "tipo": "componente", "arquivo": "frontend/src/app/features/commitments/views/month-view.component.spec.ts", "cenario": "Dado 5 ocorrências no dia 15, então a célula mostra 3 títulos e o texto '+2'." },
      { "id": "T3", "criterio": "CA3", "tipo": "componente", "arquivo": "frontend/src/app/features/commitments/views/year-view.component.spec.ts", "cenario": "Dado ano 2026, então HttpTestingController recebe exatamente um GET com from=2026-01-01&to=2026-12-31." }
    ]
  },
  {
    "id": "C09",
    "etapa": "2-compromissos",
    "titulo": "Formulário completo e recorrência na tela",
    "acao": "Criar CommitmentFormDialog (MatDialog) para editar todos os campos, incluindo recorrência (frequência, dias da semana, intervalo, fim por data ou quantidade) e, ao editar um recorrente, perguntar 'só esta ocorrência' ou 'toda a série'; excluir com confirmação.",
    "story": "Como Pedro, quero detalhar um compromisso e configurar repetição quando precisar.",
    "arquivos": ["frontend/src/app/features/commitments/form/commitment-form.dialog.ts", "frontend/src/app/features/commitments/form/commitment-form.dialog.html", "frontend/src/app/features/commitments/form/recurrence-editor.component.ts"],
    "dependencias": ["C07"],
    "conceito_angular": "FormGroup aninhado e validação condicional: o subgrupo de recorrência só é obrigatório quando 'repetir' está ligado. MatDialog abre um componente em modal e devolve um resultado ao fechar.",
    "status": "pendente",
    "criterios_de_aceite": [
      { "id": "CA1", "descricao": "Ligar 'repetir' semanal exige ao menos um dia da semana." },
      { "id": "CA2", "descricao": "O formulário gera o JSON de recurrenceRule esperado pela API." },
      { "id": "CA3", "descricao": "Editar 'só esta' chama PATCH da ocorrência; 'toda a série' chama PUT." }
    ],
    "testes_dos_criterios": [
      { "id": "T1", "criterio": "CA1", "tipo": "componente", "arquivo": "frontend/src/app/features/commitments/form/recurrence-editor.component.spec.ts", "cenario": "Dado freq WEEKLY sem dias, então o form é inválido; marcando MON, fica válido." },
      { "id": "T2", "criterio": "CA2", "tipo": "componente", "arquivo": "frontend/src/app/features/commitments/form/commitment-form.dialog.spec.ts", "cenario": "Dado semanal seg/qua a cada 1 semana até 2026-12-31, então o body enviado tem recurrenceRule {freq:'WEEKLY', interval:1, byWeekDays:['MON','WED'], until:'2026-12-31'}." },
      { "id": "T3", "criterio": "CA3", "tipo": "componente", "arquivo": "frontend/src/app/features/commitments/form/commitment-form.dialog.spec.ts", "cenario": "Dado edição de ocorrência e escolha 'só esta', então PATCH /occurrences/{date}; escolha 'toda a série', então PUT /commitments/{id}." }
    ]
  },
  {
    "id": "C10",
    "etapa": "2-compromissos",
    "titulo": "Tela Hoje: seção de compromissos",
    "acao": "Criar GET /api/today (TodayController agregador, por enquanto só com commitments do dia no fuso do usuário) e a seção 'Compromissos' na TodayPage com quick add e checkbox; a página será estendida nas etapas 3 e 4.",
    "story": "Como Pedro, quero abrir o app e ver na tela Hoje meus compromissos do dia.",
    "arquivos": ["backend/src/main/java/com/porganization/today/TodayController.java", "backend/src/main/java/com/porganization/today/TodayService.java", "backend/src/main/java/com/porganization/today/TodayResponse.java", "frontend/src/app/features/today/today.page.ts", "frontend/src/app/features/today/sections/today-commitments.component.ts"],
    "dependencias": ["C07"],
    "conceito_angular": "Composição de página: a TodayPage só busca os dados e distribui para componentes de seção, cada um com sua responsabilidade.",
    "status": "pendente",
    "criterios_de_aceite": [
      { "id": "CA1", "descricao": "GET /api/today retorna as ocorrências de hoje considerando o fuso do usuário (23h30 em São Paulo ainda é o mesmo dia)." },
      { "id": "CA2", "descricao": "Criar pela quick add na tela Hoje adiciona o item na lista sem recarregar a página." }
    ],
    "testes_dos_criterios": [
      { "id": "T1", "criterio": "CA1", "tipo": "integracao", "arquivo": "backend/src/test/java/com/porganization/today/TodayControllerIT.java", "cenario": "Dado Clock fixo em 2026-10-02T02:30Z e timezone America/Sao_Paulo, quando GET /api/today, então date == 2026-10-01 e vêm os compromissos de 01/10." },
      { "id": "T2", "criterio": "CA2", "tipo": "e2e", "arquivo": "frontend/e2e/today-commitments.spec.ts", "cenario": "Dado usuário logado em /hoje, quando cria 'Reunião' 15:00 pela quick add, então 'Reunião' aparece na lista sem reload." }
    ]
  }
]
```

### Etapa 3: Estudos

```json
[
  {
    "id": "E01",
    "etapa": "3-estudos",
    "titulo": "Modelo de matérias e tags",
    "acao": "Criar migração com subjects (id, user_id, name, color, priority_order int, sessions_per_week int, lesson_minutes int padrão 50, archived) , tags (id, user_id, name único por usuário) e subject_tags; entidades e repositórios.",
    "story": "Como Pedro, quero cadastrar matérias como 'Java Advanced' com tags como 'faculdade' ou 'línguas'.",
    "arquivos": ["backend/src/main/resources/db/migration/V4__subjects_tags.sql", "backend/src/main/java/com/porganization/studies/Subject.java", "backend/src/main/java/com/porganization/studies/Tag.java", "backend/src/main/java/com/porganization/studies/SubjectRepository.java", "backend/src/main/java/com/porganization/studies/TagRepository.java"],
    "dependencias": ["B05"],
    "status": "pendente",
    "criterios_de_aceite": [
      { "id": "CA1", "descricao": "Nome de tag é único por usuário, sem diferenciar maiúsculas." },
      { "id": "CA2", "descricao": "sessions_per_week aceita 0 a 21 e lesson_minutes 5 a 240 (check constraints)." }
    ],
    "testes_dos_criterios": [
      { "id": "T1", "criterio": "CA1", "tipo": "integracao", "arquivo": "backend/src/test/java/com/porganization/studies/TagRepositoryTest.java", "cenario": "Dado tag 'Faculdade' do usuário A, quando salva 'faculdade' para A, então viola unicidade; para B, então salva." },
      { "id": "T2", "criterio": "CA2", "tipo": "integracao", "arquivo": "backend/src/test/java/com/porganization/studies/SubjectRepositoryTest.java", "cenario": "Dado sessions_per_week=22 ou lesson_minutes=3, quando save e flush, então DataIntegrityViolationException." }
    ]
  },
  {
    "id": "E02",
    "etapa": "3-estudos",
    "titulo": "API de matérias, tags e prioridade",
    "acao": "Criar CRUD /api/subjects (com tagIds), CRUD /api/tags, GET /api/subjects?tag=... e PUT /api/subjects/order recebendo a lista ordenada de ids para gravar priority_order.",
    "story": "Como Pedro, quero gerenciar matérias e definir a ordem de prioridade entre elas.",
    "arquivos": ["backend/src/main/java/com/porganization/studies/SubjectController.java", "backend/src/main/java/com/porganization/studies/SubjectService.java", "backend/src/main/java/com/porganization/studies/TagController.java", "backend/src/main/java/com/porganization/studies/dto/SubjectRequest.java"],
    "dependencias": ["E01"],
    "status": "pendente",
    "criterios_de_aceite": [
      { "id": "CA1", "descricao": "Nova matéria entra no fim da ordem de prioridade." },
      { "id": "CA2", "descricao": "PUT /order grava priority_order 1..N na ordem recebida e rejeita (400) lista com id faltando, repetido ou de outro usuário." },
      { "id": "CA3", "descricao": "GET /api/subjects?tag=línguas filtra pelas matérias com aquela tag." }
    ],
    "testes_dos_criterios": [
      { "id": "T1", "criterio": "CA1", "tipo": "integracao", "arquivo": "backend/src/test/java/com/porganization/studies/SubjectControllerIT.java", "cenario": "Dado 2 matérias, quando cria a terceira, então priority_order == 3." },
      { "id": "T2", "criterio": "CA2", "tipo": "integracao", "arquivo": "backend/src/test/java/com/porganization/studies/SubjectControllerIT.java", "cenario": "Dado [c,a,b], então GET retorna nessa ordem; dado [a,a,b], então 400." },
      { "id": "T3", "criterio": "CA3", "tipo": "integracao", "arquivo": "backend/src/test/java/com/porganization/studies/SubjectControllerIT.java", "cenario": "Dado Inglês com tag línguas e Java com faculdade, quando filtra línguas, então só Inglês." }
    ]
  },
  {
    "id": "E03",
    "etapa": "3-estudos",
    "titulo": "Tela de matérias e prioridades",
    "acao": "Criar SubjectsPage com lista ordenada por prioridade reordenável por arrastar (CDK DragDrop), chips de tags com filtro, campo de sessões por semana editável na linha e diálogo de criar/editar matéria com seletor de tags (criando tag nova no próprio campo).",
    "story": "Como Pedro, quero arrastar as matérias para definir prioridade e ajustar quantas sessões por semana cada uma recebe.",
    "arquivos": ["frontend/src/app/features/studies/subjects/subjects.page.ts", "frontend/src/app/features/studies/subjects/subject-form.dialog.ts", "frontend/src/app/features/studies/data/studies.service.ts", "frontend/src/app/features/studies/data/study.model.ts"],
    "dependencias": ["E02", "B09"],
    "conceito_angular": "Angular CDK DragDrop (cdkDropList/cdkDrag) e atualização otimista: a lista muda na hora e volta ao estado anterior se a API falhar.",
    "status": "pendente",
    "criterios_de_aceite": [
      { "id": "CA1", "descricao": "Arrastar uma matéria chama PUT /order com a nova ordem." },
      { "id": "CA2", "descricao": "Se o PUT falhar, a lista volta para a ordem anterior e mostra erro." },
      { "id": "CA3", "descricao": "Clicar num chip de tag filtra a lista." }
    ],
    "testes_dos_criterios": [
      { "id": "T1", "criterio": "CA1", "tipo": "componente", "arquivo": "frontend/src/app/features/studies/subjects/subjects.page.spec.ts", "cenario": "Dado [A,B,C], quando dispara drop de índice 2 para 0, então PUT /order com [C,A,B]." },
      { "id": "T2", "criterio": "CA2", "tipo": "componente", "arquivo": "frontend/src/app/features/studies/subjects/subjects.page.spec.ts", "cenario": "Dado resposta 500 no PUT, então a lista renderizada volta a [A,B,C] e aparece snackbar de erro." },
      { "id": "T3", "criterio": "CA3", "tipo": "componente", "arquivo": "frontend/src/app/features/studies/subjects/subjects.page.spec.ts", "cenario": "Dado chip 'línguas' clicado, então só Inglês é renderizado." }
    ]
  },
  {
    "id": "E04",
    "etapa": "3-estudos",
    "titulo": "Modelo de aulas e sessões de estudo",
    "acao": "Criar migração com lessons (id, user_id, subject_id, title, notes, studied_at, duration_minutes) e study_sessions (id, user_id, subject_id, lesson_id null, type LESSON|REVIEW, started_at, ended_at, paused_seconds, status RUNNING|PAUSED|FINISHED|ABANDONED).",
    "story": "Como Pedro, quero registrar o que estudei e quanto tempo levei para o app planejar minhas revisões.",
    "arquivos": ["backend/src/main/resources/db/migration/V5__lessons_sessions.sql", "backend/src/main/java/com/porganization/studies/Lesson.java", "backend/src/main/java/com/porganization/studies/StudySession.java", "backend/src/main/java/com/porganization/studies/StudySessionRepository.java"],
    "dependencias": ["E01"],
    "status": "pendente",
    "criterios_de_aceite": [
      { "id": "CA1", "descricao": "Existe no máximo uma sessão RUNNING ou PAUSED por usuário (índice único parcial)." },
      { "id": "CA2", "descricao": "A duração efetiva é ended_at - started_at - paused_seconds." }
    ],
    "testes_dos_criterios": [
      { "id": "T1", "criterio": "CA1", "tipo": "integracao", "arquivo": "backend/src/test/java/com/porganization/studies/StudySessionRepositoryTest.java", "cenario": "Dado uma sessão RUNNING do usuário A, quando salva outra RUNNING para A, então violação de unicidade." },
      { "id": "T2", "criterio": "CA2", "tipo": "unitario", "arquivo": "backend/src/test/java/com/porganization/studies/StudySessionTest.java", "cenario": "Dado início 10:00, fim 10:55 e 300s pausados, então effectiveMinutes() == 50." }
    ]
  },
  {
    "id": "E05",
    "etapa": "3-estudos",
    "titulo": "Núcleo FSRS",
    "acao": "Implementar em Java puro (pacote studies.fsrs) o algoritmo FSRS (versão atual publicada pelo projeto open-spaced-repetition, com os parâmetros padrão) com estados New/Learning/Review, cálculo de stability, difficulty e próximo intervalo para retenção desejada 0.9; mapear a nota do app: DIFICIL=Hard(2), OK=Good(3), FACIL=Easy(4). Sem 'Again' porque a revisão é uma mini aula.",
    "story": "Como Pedro, quero que as revisões sejam agendadas por um algoritmo de repetição espaçada confiável.",
    "arquivos": ["backend/src/main/java/com/porganization/studies/fsrs/Fsrs.java", "backend/src/main/java/com/porganization/studies/fsrs/FsrsParameters.java", "backend/src/main/java/com/porganization/studies/fsrs/FsrsCard.java", "backend/src/main/java/com/porganization/studies/fsrs/ReviewGrade.java"],
    "dependencias": [],
    "status": "pendente",
    "criterios_de_aceite": [
      { "id": "CA1", "descricao": "Os resultados batem com a implementação de referência (py-fsrs ou ts-fsrs) para a mesma sequência de notas e datas." },
      { "id": "CA2", "descricao": "Para o mesmo estado, o intervalo de FACIL > OK > DIFICIL." },
      { "id": "CA3", "descricao": "O intervalo nunca é menor que 1 dia nem maior que o máximo configurado (365 dias por padrão)." }
    ],
    "testes_dos_criterios": [
      { "id": "T1", "criterio": "CA1", "tipo": "unitario", "arquivo": "backend/src/test/java/com/porganization/studies/fsrs/FsrsReferenceTest.java", "cenario": "Dado fixture JSON gerado com a biblioteca de referência (sequência OK, OK, DIFICIL, FACIL em datas fixas), então stability, difficulty e due batem com tolerância de 1e-4." },
      { "id": "T2", "criterio": "CA2", "tipo": "unitario", "arquivo": "backend/src/test/java/com/porganization/studies/fsrs/FsrsTest.java", "cenario": "Dado um card após duas revisões, quando simula as três notas, então interval(FACIL) > interval(OK) > interval(DIFICIL)." },
      { "id": "T3", "criterio": "CA3", "tipo": "unitario", "arquivo": "backend/src/test/java/com/porganization/studies/fsrs/FsrsTest.java", "cenario": "Dado 30 revisões FACIL seguidas, então todos os intervalos estão em [1, 365]." }
    ]
  },
  {
    "id": "E06",
    "etapa": "3-estudos",
    "titulo": "Timer de estudo (API)",
    "acao": "Criar endpoints POST /api/study/sessions (start com subjectId e type), POST /{id}/pause, /{id}/resume, /{id}/finish (para LESSON recebe title e notes da aula e cria o Lesson) e /{id}/abandon; GET /api/study/sessions/active para retomar após recarregar.",
    "story": "Como Pedro, quero iniciar, pausar e concluir um estudo com timer e não perder o tempo se fechar a aba.",
    "arquivos": ["backend/src/main/java/com/porganization/studies/StudySessionController.java", "backend/src/main/java/com/porganization/studies/StudySessionService.java", "backend/src/main/java/com/porganization/studies/dto/FinishLessonRequest.java"],
    "dependencias": ["E04"],
    "status": "pendente",
    "criterios_de_aceite": [
      { "id": "CA1", "descricao": "Pausar e retomar acumula paused_seconds corretamente." },
      { "id": "CA2", "descricao": "Iniciar com outra sessão ativa responde 409." },
      { "id": "CA3", "descricao": "Finish de LESSON cria Lesson com duration_minutes igual à duração efetiva." }
    ],
    "testes_dos_criterios": [
      { "id": "T1", "criterio": "CA1", "tipo": "integracao", "arquivo": "backend/src/test/java/com/porganization/studies/StudySessionControllerIT.java", "cenario": "Dado Clock controlável, start 10:00, pause 10:20, resume 10:25, finish 10:55, então paused_seconds == 300 e duração 50 min." },
      { "id": "T2", "criterio": "CA2", "tipo": "integracao", "arquivo": "backend/src/test/java/com/porganization/studies/StudySessionControllerIT.java", "cenario": "Dado sessão RUNNING, quando POST start de novo, então 409." },
      { "id": "T3", "criterio": "CA3", "tipo": "integracao", "arquivo": "backend/src/test/java/com/porganization/studies/StudySessionControllerIT.java", "cenario": "Dado finish com title 'Streams', então existe Lesson 'Streams' com duration_minutes 50 ligada à sessão." }
    ]
  },
  {
    "id": "E07",
    "etapa": "3-estudos",
    "titulo": "Agendamento das revisões (mini aula)",
    "acao": "Criar tabela review_items (lesson_id, user_id, fsrs_state, stability, difficulty, reps, due_date, review_minutes, last_grade); ao finalizar uma LESSON criar o review_item com due no dia seguinte e review_minutes = ceil(duração da aula / 2), mínimo 5; ao finalizar uma REVIEW com grade (DIFICIL|OK|FACIL) aplicar FSRS e reagendar.",
    "story": "Como Pedro, quero que cada aula gere revisões em mini aulas com metade do tempo, reagendadas conforme eu avalio a dificuldade.",
    "arquivos": ["backend/src/main/resources/db/migration/V6__review_items.sql", "backend/src/main/java/com/porganization/studies/ReviewItem.java", "backend/src/main/java/com/porganization/studies/ReviewScheduler.java", "backend/src/main/java/com/porganization/studies/StudySessionService.java"],
    "dependencias": ["E05", "E06"],
    "status": "pendente",
    "criterios_de_aceite": [
      { "id": "CA1", "descricao": "Aula de 50 min gera review_item com review_minutes 25 e due amanhã." },
      { "id": "CA2", "descricao": "Finish de REVIEW sem grade responde 400; com grade, due_date e estado são atualizados pelo FSRS." },
      { "id": "CA3", "descricao": "Aula de 7 min gera revisão de 5 min (mínimo)." }
    ],
    "testes_dos_criterios": [
      { "id": "T1", "criterio": "CA1", "tipo": "integracao", "arquivo": "backend/src/test/java/com/porganization/studies/ReviewSchedulerIT.java", "cenario": "Dado aula finalizada em 2026-10-01 com 50 min, então review_item.due_date == 2026-10-02 e review_minutes == 25." },
      { "id": "T2", "criterio": "CA2", "tipo": "integracao", "arquivo": "backend/src/test/java/com/porganization/studies/ReviewSchedulerIT.java", "cenario": "Dado revisão finalizada com grade OK, então due_date == hoje + Fsrs.next(card, GOOD).interval e reps == 1; sem grade, então 400." },
      { "id": "T3", "criterio": "CA3", "tipo": "unitario", "arquivo": "backend/src/test/java/com/porganization/studies/ReviewSchedulerTest.java", "cenario": "Dado duração 7, então reviewMinutes(7) == 5; dado 51, então 26." }
    ]
  },
  {
    "id": "E08",
    "etapa": "3-estudos",
    "titulo": "Plano de estudo do dia",
    "acao": "Criar DailyStudyPlanner e GET /api/study/today: primeiro as revisões vencidas (due_date <= hoje, mais atrasadas primeiro), depois as matérias por priority_order que ainda não cumpriram sessions_per_week na semana corrente (seg-dom), sugerindo lesson_minutes.",
    "story": "Como Pedro, quero abrir o app e saber o que estudar hoje, com revisões atrasadas em primeiro lugar.",
    "arquivos": ["backend/src/main/java/com/porganization/studies/DailyStudyPlanner.java", "backend/src/main/java/com/porganization/studies/StudyTodayController.java", "backend/src/main/java/com/porganization/studies/dto/StudyTodayResponse.java"],
    "dependencias": ["E07", "E02"],
    "status": "pendente",
    "criterios_de_aceite": [
      { "id": "CA1", "descricao": "Revisões vencidas vêm antes de qualquer aula nova, ordenadas da mais atrasada para a mais recente." },
      { "id": "CA2", "descricao": "Matéria que já cumpriu as sessões da semana não aparece como aula sugerida." },
      { "id": "CA3", "descricao": "Aulas sugeridas seguem a ordem de prioridade." }
    ],
    "testes_dos_criterios": [
      { "id": "T1", "criterio": "CA1", "tipo": "unitario", "arquivo": "backend/src/test/java/com/porganization/studies/DailyStudyPlannerTest.java", "cenario": "Dado revisões com due 28/09 e 30/09 e hoje 01/10, então itens [rev 28/09, rev 30/09, aulas...]." },
      { "id": "T2", "criterio": "CA2", "tipo": "unitario", "arquivo": "backend/src/test/java/com/porganization/studies/DailyStudyPlannerTest.java", "cenario": "Dado Java com sessions_per_week 2 e 2 LESSON na semana, então Java não está nas aulas sugeridas." },
      { "id": "T3", "criterio": "CA3", "tipo": "integracao", "arquivo": "backend/src/test/java/com/porganization/studies/StudyTodayControllerIT.java", "cenario": "Dado Inglês prioridade 1 e Java 2, sem sessões na semana, então aulas [Inglês, Java]." }
    ]
  },
  {
    "id": "E09",
    "etapa": "3-estudos",
    "titulo": "Tela do estudo do dia com timer",
    "acao": "Criar StudyTodayPage (lista do plano do dia) e StudySessionComponent: clicar numa matéria ou revisão abre o timer (contagem regressiva do tempo sugerido, pausar/retomar/concluir); concluir aula pede título e notas; concluir revisão pede a nota em 3 botões (Difícil, Ok, Fácil); ao recarregar, retoma a sessão ativa.",
    "story": "Como Pedro, quero clicar na matéria, ver o timer correndo e registrar a aula ou avaliar a revisão ao terminar.",
    "arquivos": ["frontend/src/app/features/studies/today/study-today.page.ts", "frontend/src/app/features/studies/session/study-session.component.ts", "frontend/src/app/features/studies/session/timer.ts", "frontend/src/app/features/studies/session/grade-buttons.component.ts"],
    "dependencias": ["E08", "E03"],
    "conceito_angular": "Timer com signals e effect(): um signal guarda o tempo restante, atualizado por um intervalo que é limpo no DestroyRef quando o componente sai da tela.",
    "status": "pendente",
    "criterios_de_aceite": [
      { "id": "CA1", "descricao": "O timer decrementa a cada segundo, para quando pausado e avisa (som/notificação visual) ao chegar a zero, sem encerrar sozinho." },
      { "id": "CA2", "descricao": "Revisão só pode ser concluída escolhendo uma das 3 notas, que é enviada no finish." },
      { "id": "CA3", "descricao": "Recarregar a página com sessão ativa reabre o timer com o tempo correto." }
    ],
    "testes_dos_criterios": [
      { "id": "T1", "criterio": "CA1", "tipo": "componente", "arquivo": "frontend/src/app/features/studies/session/study-session.component.spec.ts", "cenario": "Dado vi.useFakeTimers() e sessão de 25 min, quando vi.advanceTimersByTime(3000) e fixture.whenStable(), então mostra 24:57; pausa e avança 5000, continua 24:57; ao zerar, classe 'finished' aparece e a sessão continua aberta (sem fakeAsync: o app é zoneless)." },
      { "id": "T2", "criterio": "CA2", "tipo": "componente", "arquivo": "frontend/src/app/features/studies/session/study-session.component.spec.ts", "cenario": "Dado sessão REVIEW, quando clica em 'Fácil', então POST finish com {grade:'FACIL'}; sem clicar, o botão concluir não existe." },
      { "id": "T3", "criterio": "CA3", "tipo": "e2e", "arquivo": "frontend/e2e/study-timer.spec.ts", "cenario": "Dado sessão iniciada há 2 min, quando page.reload(), então o timer mostra aproximadamente o tempo sugerido menos 2 min." }
    ]
  },
  {
    "id": "E10",
    "etapa": "3-estudos",
    "titulo": "Histórico e estatísticas de estudo",
    "acao": "Criar GET /api/study/stats?from&to (minutos por matéria, sessões por semana vs meta, revisões feitas) e a aba 'Histórico' com lista de aulas por matéria e gráfico simples de minutos por semana.",
    "story": "Como Pedro, quero ver quanto estudei de cada matéria e se estou cumprindo a meta semanal.",
    "arquivos": ["backend/src/main/java/com/porganization/studies/StudyStatsService.java", "backend/src/main/java/com/porganization/studies/StudyStatsController.java", "frontend/src/app/features/studies/history/study-history.page.ts"],
    "dependencias": ["E09"],
    "conceito_angular": "Resource/rxResource (ou toSignal) para carregar dados assíncronos direto como signal, com estados de carregando e erro no template.",
    "status": "pendente",
    "criterios_de_aceite": [
      { "id": "CA1", "descricao": "Minutos por matéria somam só sessões FINISHED no intervalo." },
      { "id": "CA2", "descricao": "A aba mostra, por matéria, 'X de Y sessões nesta semana'." }
    ],
    "testes_dos_criterios": [
      { "id": "T1", "criterio": "CA1", "tipo": "integracao", "arquivo": "backend/src/test/java/com/porganization/studies/StudyStatsIT.java", "cenario": "Dado sessões FINISHED de 50 e 25 min e uma ABANDONED de 30, então total da matéria == 75." },
      { "id": "T2", "criterio": "CA2", "tipo": "componente", "arquivo": "frontend/src/app/features/studies/history/study-history.page.spec.ts", "cenario": "Dado stats com Java 1 de 3, então o texto '1 de 3 sessões' aparece na linha de Java." }
    ]
  },
  {
    "id": "E11",
    "etapa": "3-estudos",
    "titulo": "Tela Hoje: seção de estudos",
    "acao": "Incluir o plano de estudo do dia no GET /api/today e criar a seção 'Estudos' na TodayPage com revisões vencidas destacadas primeiro e botão que abre o timer.",
    "story": "Como Pedro, quero ver na tela Hoje o que estudar, com as revisões atrasadas no topo.",
    "arquivos": ["backend/src/main/java/com/porganization/today/TodayService.java", "backend/src/main/java/com/porganization/today/TodayResponse.java", "frontend/src/app/features/today/sections/today-studies.component.ts"],
    "dependencias": ["E09", "C10"],
    "conceito_angular": "Pipes e computed(): os dias de atraso são derivados da due_date com computed() e o texto '1 dia' / '2 dias' é pluralizado no template com I18nPluralPipe.",
    "status": "pendente",
    "criterios_de_aceite": [
      { "id": "CA1", "descricao": "GET /api/today inclui studies.reviews e studies.lessons na ordem do planner." },
      { "id": "CA2", "descricao": "Revisões vencidas aparecem antes das aulas, com selo 'Revisão · X dias de atraso' quando atrasadas." }
    ],
    "testes_dos_criterios": [
      { "id": "T1", "criterio": "CA1", "tipo": "integracao", "arquivo": "backend/src/test/java/com/porganization/today/TodayControllerIT.java", "cenario": "Dado 1 revisão vencida e 1 matéria pendente, então response.studies.reviews tem 1 e lessons tem 1." },
      { "id": "T2", "criterio": "CA2", "tipo": "componente", "arquivo": "frontend/src/app/features/today/sections/today-studies.component.spec.ts", "cenario": "Dado revisão com due há 2 dias, então o primeiro item mostra 'Revisão · 2 dias de atraso'." }
    ]
  }
]
```

### Etapa 4: Finanças

```json
[
  {
    "id": "F01",
    "etapa": "4-financas",
    "titulo": "Contas",
    "acao": "Criar migração e CRUD /api/finance/accounts (name, type CHECKING|SAVINGS|CASH|INVESTMENT, initial_balance NUMERIC(14,2), archived) e cálculo de saldo atual = saldo inicial + transações efetivadas. Configurar o Jackson para serializar BigDecimal como string com 2 casas (o Jackson 3 do Boot 4 serializa como número por padrão), num único ponto em config/JacksonConfig.",
    "story": "Como Pedro, quero cadastrar minhas contas e ver o saldo de cada uma.",
    "arquivos": ["backend/src/main/resources/db/migration/V7__accounts.sql", "backend/src/main/java/com/porganization/finance/accounts/Account.java", "backend/src/main/java/com/porganization/finance/accounts/AccountController.java", "backend/src/main/java/com/porganization/finance/accounts/AccountService.java", "backend/src/main/java/com/porganization/config/JacksonConfig.java"],
    "dependencias": ["B05"],
    "status": "pendente",
    "criterios_de_aceite": [
      { "id": "CA1", "descricao": "Valores trafegam como string decimal no JSON e são BigDecimal com escala 2 no Java." },
      { "id": "CA2", "descricao": "Conta com transações não pode ser excluída (409), só arquivada." }
    ],
    "testes_dos_criterios": [
      { "id": "T1", "criterio": "CA1", "tipo": "integracao", "arquivo": "backend/src/test/java/com/porganization/finance/accounts/AccountControllerIT.java", "cenario": "Dado POST initialBalance '1000.10', então GET devolve '1000.10' (não 1000.1 nem float)." },
      { "id": "T2", "criterio": "CA2", "tipo": "integracao", "arquivo": "backend/src/test/java/com/porganization/finance/accounts/AccountControllerIT.java", "cenario": "Dado conta com 1 transação, quando DELETE, então 409; PATCH archived=true, então 200." }
    ]
  },
  {
    "id": "F02",
    "etapa": "4-financas",
    "titulo": "Categorias e tags financeiras",
    "acao": "Criar categories (name, kind INCOME|EXPENSE, color, icon) com seed de categorias padrão criadas no primeiro acesso do usuário, e finance_tags; CRUD /api/finance/categories e /api/finance/tags.",
    "story": "Como Pedro, quero classificar gastos e rendas por categoria e tags livres.",
    "arquivos": ["backend/src/main/resources/db/migration/V8__categories_tags.sql", "backend/src/main/java/com/porganization/finance/categories/Category.java", "backend/src/main/java/com/porganization/finance/categories/CategoryService.java", "backend/src/main/java/com/porganization/finance/categories/CategoryController.java"],
    "dependencias": ["F01"],
    "status": "pendente",
    "criterios_de_aceite": [
      { "id": "CA1", "descricao": "No primeiro GET de categorias o usuário recebe o conjunto padrão (Alimentação, Transporte, Moradia, Lazer, Saúde, Educação, Salário, Outros), uma única vez." },
      { "id": "CA2", "descricao": "Categoria em uso não pode ser excluída (409)." }
    ],
    "testes_dos_criterios": [
      { "id": "T1", "criterio": "CA1", "tipo": "integracao", "arquivo": "backend/src/test/java/com/porganization/finance/categories/CategoryControllerIT.java", "cenario": "Dado usuário novo, quando GET duas vezes, então as duas respostas têm as mesmas 8 categorias e o banco tem 8 linhas." },
      { "id": "T2", "criterio": "CA2", "tipo": "integracao", "arquivo": "backend/src/test/java/com/porganization/finance/categories/CategoryControllerIT.java", "cenario": "Dado transação na categoria Lazer, quando DELETE Lazer, então 409." }
    ]
  },
  {
    "id": "F03",
    "etapa": "4-financas",
    "titulo": "Transações de renda e gasto",
    "acao": "Criar transactions (account_id, type INCOME|EXPENSE|TRANSFER, amount > 0, date, description, category_id, paid boolean, card_statement_id uuid null sem FK, pois card_statements só nasce na F05, installment info null) com CRUD /api/finance/transactions e listagem filtrável por mês, conta, categoria, tag e tipo.",
    "story": "Como Pedro, quero registrar rendas e gastos e filtrá-los por mês e categoria.",
    "arquivos": ["backend/src/main/resources/db/migration/V9__transactions.sql", "backend/src/main/java/com/porganization/finance/transactions/Transaction.java", "backend/src/main/java/com/porganization/finance/transactions/TransactionController.java", "backend/src/main/java/com/porganization/finance/transactions/TransactionService.java", "backend/src/main/java/com/porganization/finance/transactions/TransactionSpecifications.java"],
    "dependencias": ["F02"],
    "status": "pendente",
    "criterios_de_aceite": [
      { "id": "CA1", "descricao": "amount <= 0 responde 400; a categoria precisa ser do mesmo kind do tipo (gasto com categoria de gasto)." },
      { "id": "CA2", "descricao": "Saldo da conta = inicial + rendas pagas - gastos pagos." },
      { "id": "CA3", "descricao": "Filtros combinados (mês + categoria + tag) retornam só o que casa com todos." }
    ],
    "testes_dos_criterios": [
      { "id": "T1", "criterio": "CA1", "tipo": "integracao", "arquivo": "backend/src/test/java/com/porganization/finance/transactions/TransactionControllerIT.java", "cenario": "Dado amount '0.00', então 400; dado EXPENSE com categoria Salário, então 400." },
      { "id": "T2", "criterio": "CA2", "tipo": "integracao", "arquivo": "backend/src/test/java/com/porganization/finance/transactions/TransactionControllerIT.java", "cenario": "Dado inicial 1000.00, renda 2500.00 paga, gasto 300.55 pago e gasto 100.00 não pago, então saldo == 3199.45." },
      { "id": "T3", "criterio": "CA3", "tipo": "integracao", "arquivo": "backend/src/test/java/com/porganization/finance/transactions/TransactionControllerIT.java", "cenario": "Dado 4 transações variando mês, categoria e tag, quando filtra month=2026-10&category=Lazer&tag=viagem, então só 1." }
    ]
  },
  {
    "id": "F04",
    "etapa": "4-financas",
    "titulo": "Transferências entre contas",
    "acao": "Implementar POST /api/finance/transfers criando atomicamente a saída e a entrada ligadas por transfer_group_id; editar ou excluir uma perna afeta as duas.",
    "story": "Como Pedro, quero mover dinheiro entre minhas contas sem contar como renda ou gasto.",
    "arquivos": ["backend/src/main/java/com/porganization/finance/transactions/TransferService.java", "backend/src/main/java/com/porganization/finance/transactions/TransferController.java", "backend/src/main/resources/db/migration/V10__transfer_group.sql"],
    "dependencias": ["F03"],
    "status": "pendente",
    "criterios_de_aceite": [
      { "id": "CA1", "descricao": "Transferir 200.00 de A para B reduz A em 200.00 e aumenta B em 200.00; o total geral não muda." },
      { "id": "CA2", "descricao": "Transferências não entram nos totais de renda e gasto do mês." },
      { "id": "CA3", "descricao": "Conta de origem igual à de destino responde 400." }
    ],
    "testes_dos_criterios": [
      { "id": "T1", "criterio": "CA1", "tipo": "integracao", "arquivo": "backend/src/test/java/com/porganization/finance/transactions/TransferIT.java", "cenario": "Dado A=500.00 e B=0.00, quando transfere 200.00, então A=300.00, B=200.00 e soma=500.00." },
      { "id": "T2", "criterio": "CA2", "tipo": "integracao", "arquivo": "backend/src/test/java/com/porganization/finance/transactions/TransferIT.java", "cenario": "Dado só uma transferência no mês, então o resumo mensal tem income=0.00 e expense=0.00." },
      { "id": "T3", "criterio": "CA3", "tipo": "integracao", "arquivo": "backend/src/test/java/com/porganization/finance/transactions/TransferIT.java", "cenario": "Dado from == to, então 400." }
    ]
  },
  {
    "id": "F05",
    "etapa": "4-financas",
    "titulo": "Cartão de crédito e fatura correta",
    "acao": "Criar credit_cards (name, limit, closing_day, due_day, payment_account_id) e card_statements (card_id, reference_month, closing_date, due_date, status OPEN|CLOSED|PAID); adicionar na V11 a FK transactions.card_statement_id → card_statements(id); criar StatementResolver que, dada a data da compra, encontra ou cria a fatura certa (compra no dia do fechamento ou depois vai para a próxima).",
    "story": "Como Pedro, quero lançar compras do cartão manualmente e que caiam na fatura certa.",
    "arquivos": ["backend/src/main/resources/db/migration/V11__credit_cards.sql", "backend/src/main/java/com/porganization/finance/cards/CreditCard.java", "backend/src/main/java/com/porganization/finance/cards/CardStatement.java", "backend/src/main/java/com/porganization/finance/cards/StatementResolver.java", "backend/src/main/java/com/porganization/finance/cards/CreditCardController.java"],
    "dependencias": ["F03"],
    "status": "pendente",
    "criterios_de_aceite": [
      { "id": "CA1", "descricao": "Cartão com fechamento dia 5 e vencimento dia 12: compra em 04/10 vai para a fatura que vence 12/10; compra em 05/10 vai para a que vence 12/11." },
      { "id": "CA2", "descricao": "Fechamento em dia inexistente no mês (31) usa o último dia do mês." },
      { "id": "CA3", "descricao": "Compra no cartão não altera saldo de conta até o pagamento da fatura." }
    ],
    "testes_dos_criterios": [
      { "id": "T1", "criterio": "CA1", "tipo": "unitario", "arquivo": "backend/src/test/java/com/porganization/finance/cards/StatementResolverTest.java", "cenario": "Dado closing 5 e due 12, então resolve(2026-10-04).due == 2026-10-12 e resolve(2026-10-05).due == 2026-11-12." },
      { "id": "T2", "criterio": "CA2", "tipo": "unitario", "arquivo": "backend/src/test/java/com/porganization/finance/cards/StatementResolverTest.java", "cenario": "Dado closing 31, então o fechamento de fevereiro/2027 é 2027-02-28." },
      { "id": "T3", "criterio": "CA3", "tipo": "integracao", "arquivo": "backend/src/test/java/com/porganization/finance/cards/CreditCardIT.java", "cenario": "Dado compra de 150.00 no cartão, então o saldo da conta de pagamento continua igual e a fatura soma 150.00." }
    ]
  },
  {
    "id": "F06",
    "etapa": "4-financas",
    "titulo": "Compras parceladas",
    "acao": "Permitir compra no cartão com installments N (1..48): criar N transações ligadas por purchase_id, cada uma na fatura do mês correspondente, dividindo o valor em centavos com o resto na primeira parcela; excluir a compra remove todas as parcelas.",
    "story": "Como Pedro, quero lançar uma compra parcelada e ver cada parcela nas faturas futuras.",
    "arquivos": ["backend/src/main/java/com/porganization/finance/cards/InstallmentCalculator.java", "backend/src/main/java/com/porganization/finance/cards/CardPurchaseService.java", "backend/src/main/resources/db/migration/V12__installments.sql"],
    "dependencias": ["F05"],
    "status": "pendente",
    "criterios_de_aceite": [
      { "id": "CA1", "descricao": "100.00 em 3x gera 33.34, 33.33, 33.33 e a soma é exatamente 100.00." },
      { "id": "CA2", "descricao": "Cada parcela cai em uma fatura consecutiva, com descrição 'Loja (2/3)'." },
      { "id": "CA3", "descricao": "Excluir a compra remove todas as parcelas das faturas." }
    ],
    "testes_dos_criterios": [
      { "id": "T1", "criterio": "CA1", "tipo": "unitario", "arquivo": "backend/src/test/java/com/porganization/finance/cards/InstallmentCalculatorTest.java", "cenario": "Dado 100.00 e 3, então [33.34, 33.33, 33.33]; teste parametrizado com 0.10 em 3x e 999.99 em 12x verifica soma exata." },
      { "id": "T2", "criterio": "CA2", "tipo": "integracao", "arquivo": "backend/src/test/java/com/porganization/finance/cards/CardPurchaseIT.java", "cenario": "Dado compra em 01/10 em 3x (fechamento 5), então faturas out, nov, dez têm uma parcela cada, com '(1/3)', '(2/3)', '(3/3)'." },
      { "id": "T3", "criterio": "CA3", "tipo": "integracao", "arquivo": "backend/src/test/java/com/porganization/finance/cards/CardPurchaseIT.java", "cenario": "Dado a compra acima, quando DELETE /purchases/{id}, então as 3 faturas ficam com total 0.00." }
    ]
  },
  {
    "id": "F07",
    "etapa": "4-financas",
    "titulo": "Consulta e pagamento de fatura",
    "acao": "Criar GET /api/finance/cards/{id}/statements?month=YYYY-MM (itens, total, status, limite disponível) e POST /statements/{id}/pay que cria um EXPENSE na conta de pagamento e marca PAID; faturas passam a CLOSED automaticamente após a data de fechamento (calculado na leitura).",
    "story": "Como Pedro, quero ver a fatura do mês, quanto do limite usei e registrar o pagamento.",
    "arquivos": ["backend/src/main/java/com/porganization/finance/cards/StatementService.java", "backend/src/main/java/com/porganization/finance/cards/StatementController.java", "backend/src/main/java/com/porganization/finance/cards/dto/StatementResponse.java"],
    "dependencias": ["F06"],
    "status": "pendente",
    "criterios_de_aceite": [
      { "id": "CA1", "descricao": "Limite disponível = limite - soma de todas as parcelas de faturas não pagas." },
      { "id": "CA2", "descricao": "Pagar a fatura cria gasto do valor total na conta de pagamento e status vira PAID; pagar de novo responde 409." },
      { "id": "CA3", "descricao": "Após a data de fechamento o status retornado é CLOSED e novas compras vão para a próxima fatura." }
    ],
    "testes_dos_criterios": [
      { "id": "T1", "criterio": "CA1", "tipo": "integracao", "arquivo": "backend/src/test/java/com/porganization/finance/cards/StatementIT.java", "cenario": "Dado limite 3000.00 e compra 100.00 em 3x, então disponível == 2900.00; após pagar a 1ª fatura, 2933.34." },
      { "id": "T2", "criterio": "CA2", "tipo": "integracao", "arquivo": "backend/src/test/java/com/porganization/finance/cards/StatementIT.java", "cenario": "Dado fatura 450.00, quando pay, então conta reduz 450.00 e status PAID; segundo pay, então 409." },
      { "id": "T3", "criterio": "CA3", "tipo": "integracao", "arquivo": "backend/src/test/java/com/porganization/finance/cards/StatementIT.java", "cenario": "Dado Clock em 06/10 com fechamento 05, então fatura de outubro vem CLOSED." }
    ]
  },
  {
    "id": "F08",
    "etapa": "4-financas",
    "titulo": "Gastos e rendas fixos recorrentes",
    "acao": "Criar recurring_transactions (template com tipo, valor, conta ou cartão, categoria, day_of_month, start/end) e RecurringGenerator idempotente que cria as transações do mês (paid=false) quando o mês é consultado ou pelo job diário; editar o template afeta só meses ainda não gerados.",
    "story": "Como Pedro, quero cadastrar aluguel, assinaturas e salário uma vez e vê-los todo mês.",
    "arquivos": ["backend/src/main/resources/db/migration/V13__recurring_transactions.sql", "backend/src/main/java/com/porganization/finance/recurring/RecurringTransaction.java", "backend/src/main/java/com/porganization/finance/recurring/RecurringGenerator.java", "backend/src/main/java/com/porganization/finance/recurring/RecurringController.java"],
    "dependencias": ["F05"],
    "status": "pendente",
    "criterios_de_aceite": [
      { "id": "CA1", "descricao": "Gerar o mesmo mês duas vezes não duplica lançamentos (unique recurring_id + month)." },
      { "id": "CA2", "descricao": "day_of_month 31 em fevereiro gera no último dia." },
      { "id": "CA3", "descricao": "Recorrente no cartão cai na fatura correta via StatementResolver." }
    ],
    "testes_dos_criterios": [
      { "id": "T1", "criterio": "CA1", "tipo": "integracao", "arquivo": "backend/src/test/java/com/porganization/finance/recurring/RecurringGeneratorIT.java", "cenario": "Dado template de aluguel, quando generate(2026-10) duas vezes, então 1 transação em outubro." },
      { "id": "T2", "criterio": "CA2", "tipo": "unitario", "arquivo": "backend/src/test/java/com/porganization/finance/recurring/RecurringGeneratorTest.java", "cenario": "Dado day 31, então dateFor(2027-02) == 2027-02-28." },
      { "id": "T3", "criterio": "CA3", "tipo": "integracao", "arquivo": "backend/src/test/java/com/porganization/finance/recurring/RecurringGeneratorIT.java", "cenario": "Dado assinatura 39.90 no cartão dia 7 com fechamento 5, então vai para a fatura que vence no mês seguinte." }
    ]
  },
  {
    "id": "F09",
    "etapa": "4-financas",
    "titulo": "Orçamento por categoria com alerta",
    "acao": "Criar budgets (category_id, month ou recorrente mensal, amount) e GET /api/finance/budgets?month= com gasto realizado (contas + cartão pela data da compra), percentual e nível de alerta OK (<80%), ATENCAO (80-99%), ESTOURADO (>=100%).",
    "story": "Como Pedro, quero limitar quanto gasto por categoria e ser avisado quando estiver perto do limite.",
    "arquivos": ["backend/src/main/resources/db/migration/V14__budgets.sql", "backend/src/main/java/com/porganization/finance/budgets/Budget.java", "backend/src/main/java/com/porganization/finance/budgets/BudgetService.java", "backend/src/main/java/com/porganization/finance/budgets/BudgetController.java"],
    "dependencias": ["F07"],
    "status": "pendente",
    "criterios_de_aceite": [
      { "id": "CA1", "descricao": "Orçamento de 500.00 com 400.00 gastos retorna 80% e ATENCAO; com 500.00 retorna ESTOURADO." },
      { "id": "CA2", "descricao": "Compras no cartão contam no mês da compra, não no da fatura." }
    ],
    "testes_dos_criterios": [
      { "id": "T1", "criterio": "CA1", "tipo": "unitario", "arquivo": "backend/src/test/java/com/porganization/finance/budgets/BudgetServiceTest.java", "cenario": "Teste parametrizado: (500.00, 399.99)→OK, (500.00, 400.00)→ATENCAO, (500.00, 500.00)→ESTOURADO." },
      { "id": "T2", "criterio": "CA2", "tipo": "integracao", "arquivo": "backend/src/test/java/com/porganization/finance/budgets/BudgetIT.java", "cenario": "Dado compra de 100.00 no cartão em 20/10 (fatura de novembro), então o orçamento de outubro conta 100.00." }
    ]
  },
  {
    "id": "F10",
    "etapa": "4-financas",
    "titulo": "Metas de economia",
    "acao": "Criar savings_goals (name, target_amount, target_date, account_id opcional) e goal_contributions (amount, date); GET com progresso (%), valor que falta e aporte mensal necessário até o prazo.",
    "story": "Como Pedro, quero definir metas de economia e acompanhar quanto falta e quanto guardar por mês.",
    "arquivos": ["backend/src/main/resources/db/migration/V15__savings_goals.sql", "backend/src/main/java/com/porganization/finance/goals/SavingsGoal.java", "backend/src/main/java/com/porganization/finance/goals/GoalService.java", "backend/src/main/java/com/porganization/finance/goals/GoalController.java"],
    "dependencias": ["F01"],
    "status": "pendente",
    "criterios_de_aceite": [
      { "id": "CA1", "descricao": "Meta de 6000.00 com aportes de 1500.00 mostra 25% e faltam 4500.00." },
      { "id": "CA2", "descricao": "Aporte mensal necessário = falta / meses restantes (arredondado para cima no centavo); prazo vencido mostra o valor total que falta." }
    ],
    "testes_dos_criterios": [
      { "id": "T1", "criterio": "CA1", "tipo": "integracao", "arquivo": "backend/src/test/java/com/porganization/finance/goals/GoalIT.java", "cenario": "Dado meta 6000.00 e aportes 1000.00 + 500.00, então progress 25.00 e remaining 4500.00." },
      { "id": "T2", "criterio": "CA2", "tipo": "unitario", "arquivo": "backend/src/test/java/com/porganization/finance/goals/GoalServiceTest.java", "cenario": "Dado falta 1000.00 e 3 meses, então 333.34; dado prazo no passado, então 1000.00." }
    ]
  },
  {
    "id": "F11",
    "etapa": "4-financas",
    "titulo": "API do dashboard financeiro",
    "acao": "Criar GET /api/finance/dashboard?month= com saldo total das contas, renda x gasto do mês, gasto por categoria, faturas abertas por cartão, orçamentos em alerta, progresso das metas e evolução dos últimos 6 meses.",
    "story": "Como Pedro, quero um resumo do mês em um só lugar.",
    "arquivos": ["backend/src/main/java/com/porganization/finance/dashboard/DashboardService.java", "backend/src/main/java/com/porganization/finance/dashboard/DashboardController.java", "backend/src/main/java/com/porganization/finance/dashboard/DashboardResponse.java"],
    "dependencias": ["F08", "F09", "F10"],
    "status": "pendente",
    "criterios_de_aceite": [
      { "id": "CA1", "descricao": "Os números do dashboard batem com os endpoints individuais para o mesmo cenário." },
      { "id": "CA2", "descricao": "A série de 6 meses traz meses sem movimento com 0.00." }
    ],
    "testes_dos_criterios": [
      { "id": "T1", "criterio": "CA1", "tipo": "integracao", "arquivo": "backend/src/test/java/com/porganization/finance/dashboard/DashboardIT.java", "cenario": "Dado cenário fixo (2 contas, 5 transações, 1 cartão parcelado, 1 orçamento, 1 meta), então cada campo do dashboard é igual ao do endpoint correspondente." },
      { "id": "T2", "criterio": "CA2", "tipo": "integracao", "arquivo": "backend/src/test/java/com/porganization/finance/dashboard/DashboardIT.java", "cenario": "Dado movimento só em outubro, então a série de mai a out tem 6 pontos e 5 com 0.00." }
    ]
  },
  {
    "id": "F12",
    "etapa": "4-financas",
    "titulo": "Telas de contas e transações",
    "acao": "Criar FinanceService no Angular e as telas: lista de contas com saldo, lista de transações do mês com filtros (conta, categoria, tag, tipo) e navegação de mês, formulário rápido de transação (valor, descrição, categoria, conta, data) e transferência.",
    "story": "Como Pedro, quero lançar gastos rapidamente pelo celular e ver o extrato do mês.",
    "arquivos": ["frontend/src/app/features/finance/data/finance.service.ts", "frontend/src/app/features/finance/data/finance.model.ts", "frontend/src/app/features/finance/accounts/accounts.page.ts", "frontend/src/app/features/finance/transactions/transactions.page.ts", "frontend/src/app/features/finance/transactions/transaction-form.component.ts", "frontend/src/app/shared/money-input/money-input.component.ts"],
    "dependencias": ["F04", "B09"],
    "conceito_angular": "ControlValueAccessor: o MoneyInput é um componente próprio que funciona como campo de formulário (formControlName), exibindo 'R$ 1.234,56' e entregando '1234.56' ao form. Filtros ficam em query params da rota para o link ser compartilhável.",
    "status": "pendente",
    "criterios_de_aceite": [
      { "id": "CA1", "descricao": "Digitar 123456 no campo de valor exibe 'R$ 1.234,56' e envia amount '1234.56'." },
      { "id": "CA2", "descricao": "Mudar filtros atualiza os query params e recarregar a página mantém os filtros." },
      { "id": "CA3", "descricao": "Salvar uma transação atualiza a lista e o saldo da conta sem recarregar." }
    ],
    "testes_dos_criterios": [
      { "id": "T1", "criterio": "CA1", "tipo": "componente", "arquivo": "frontend/src/app/shared/money-input/money-input.component.spec.ts", "cenario": "Dado host com FormControl, quando digita '123456', então o input mostra 'R$ 1.234,56' e control.value == '1234.56'." },
      { "id": "T2", "criterio": "CA2", "tipo": "e2e", "arquivo": "frontend/e2e/finance-transactions.spec.ts", "cenario": "Dado filtro categoria Lazer, então a URL tem ?category=...; após reload, o filtro continua selecionado." },
      { "id": "T3", "criterio": "CA3", "tipo": "e2e", "arquivo": "frontend/e2e/finance-transactions.spec.ts", "cenario": "Dado conta com 1000,00, quando lança gasto de 50,00, então a linha aparece e o saldo mostra R$ 950,00." }
    ]
  },
  {
    "id": "F13",
    "etapa": "4-financas",
    "titulo": "Telas de cartão e faturas",
    "acao": "Criar tela de cartões (limite usado/disponível em barra), cadastro de cartão, lançamento de compra com número de parcelas e prévia das parcelas, e visão de fatura por mês com itens, total, status e botão Pagar.",
    "story": "Como Pedro, quero acompanhar a fatura do cartão e ver o impacto das parcelas.",
    "arquivos": ["frontend/src/app/features/finance/cards/cards.page.ts", "frontend/src/app/features/finance/cards/card-purchase-form.component.ts", "frontend/src/app/features/finance/cards/statement.page.ts"],
    "dependencias": ["F07", "F12"],
    "conceito_angular": "Rotas com parâmetros (/financas/cartoes/:id/faturas/:mes) lidos com input() via withComponentInputBinding().",
    "status": "pendente",
    "criterios_de_aceite": [
      { "id": "CA1", "descricao": "A prévia das parcelas mostra os mesmos valores que a API vai gerar (resto na primeira)." },
      { "id": "CA2", "descricao": "Pagar a fatura pede confirmação e depois mostra status 'Paga'." }
    ],
    "testes_dos_criterios": [
      { "id": "T1", "criterio": "CA1", "tipo": "componente", "arquivo": "frontend/src/app/features/finance/cards/card-purchase-form.component.spec.ts", "cenario": "Dado 100,00 em 3x, então a prévia lista 33,34 / 33,33 / 33,33." },
      { "id": "T2", "criterio": "CA2", "tipo": "componente", "arquivo": "frontend/src/app/features/finance/cards/statement.page.spec.ts", "cenario": "Dado fatura CLOSED, quando clica Pagar e confirma, então POST /pay e o selo muda para 'Paga'." }
    ]
  },
  {
    "id": "F14",
    "etapa": "4-financas",
    "titulo": "Telas de fixos, orçamentos e metas",
    "acao": "Criar telas de lançamentos fixos (lista e formulário), orçamentos do mês (barra por categoria colorida por nível de alerta) e metas (card com progresso, falta e aporte mensal, botão de aporte).",
    "story": "Como Pedro, quero gerenciar meus fixos, orçamentos e metas em telas simples.",
    "arquivos": ["frontend/src/app/features/finance/recurring/recurring.page.ts", "frontend/src/app/features/finance/budgets/budgets.page.ts", "frontend/src/app/features/finance/goals/goals.page.ts"],
    "dependencias": ["F11", "F12"],
    "conceito_angular": "Pipes personalizados: um pipe budgetLevel transforma o nível de alerta em classe CSS e texto, deixando o template limpo.",
    "status": "pendente",
    "criterios_de_aceite": [
      { "id": "CA1", "descricao": "Barra de orçamento fica verde em OK, amarela em ATENCAO e vermelha em ESTOURADO." },
      { "id": "CA2", "descricao": "Registrar aporte atualiza o progresso da meta na tela." }
    ],
    "testes_dos_criterios": [
      { "id": "T1", "criterio": "CA1", "tipo": "unitario", "arquivo": "frontend/src/app/features/finance/budgets/budget-level.pipe.spec.ts", "cenario": "Dado 'ATENCAO', então transform retorna 'budget--warn'; 'ESTOURADO', 'budget--over'." },
      { "id": "T2", "criterio": "CA2", "tipo": "componente", "arquivo": "frontend/src/app/features/finance/goals/goals.page.spec.ts", "cenario": "Dado meta 25%, quando aporte de 1500,00 e a API devolve 50%, então a barra mostra 50%." }
    ]
  },
  {
    "id": "F15",
    "etapa": "4-financas",
    "titulo": "Dashboard financeiro na tela",
    "acao": "Criar FinanceDashboardPage com cards de saldo total e renda x gasto, gráfico de pizza de gastos por categoria e gráfico de barras dos últimos 6 meses (Chart.js via ng2-charts), lista de faturas abertas e alertas de orçamento.",
    "story": "Como Pedro, quero bater o olho e entender como está meu mês.",
    "arquivos": ["frontend/src/app/features/finance/dashboard/finance-dashboard.page.ts", "frontend/src/app/features/finance/dashboard/charts/category-chart.component.ts", "frontend/src/app/features/finance/dashboard/charts/monthly-chart.component.ts"],
    "dependencias": ["F14"],
    "conceito_angular": "Integração de biblioteca externa em componente: os dados vêm por input() e um computed() monta a configuração do gráfico.",
    "status": "pendente",
    "criterios_de_aceite": [
      { "id": "CA1", "descricao": "O gráfico de categorias recebe as categorias e valores do dashboard, maiores primeiro." },
      { "id": "CA2", "descricao": "Mês sem dados mostra um estado vazio em vez de gráfico quebrado." }
    ],
    "testes_dos_criterios": [
      { "id": "T1", "criterio": "CA1", "tipo": "componente", "arquivo": "frontend/src/app/features/finance/dashboard/charts/category-chart.component.spec.ts", "cenario": "Dado [{Lazer, 50}, {Moradia, 1200}], então chartData.labels == ['Moradia','Lazer']." },
      { "id": "T2", "criterio": "CA2", "tipo": "componente", "arquivo": "frontend/src/app/features/finance/dashboard/finance-dashboard.page.spec.ts", "cenario": "Dado dashboard com byCategory vazio, então aparece 'Nenhum gasto neste mês' e nenhum <canvas>." }
    ]
  },
  {
    "id": "F16",
    "etapa": "4-financas",
    "titulo": "Tela Hoje: seção de finanças",
    "acao": "Incluir no GET /api/today: fixos e faturas que vencem hoje ou nos próximos 3 dias, orçamentos em ATENCAO ou ESTOURADO e gasto do dia; criar a seção 'Finanças' na TodayPage com lançamento rápido de gasto.",
    "story": "Como Pedro, quero ver na tela Hoje o que vence e se algum orçamento está estourando.",
    "arquivos": ["backend/src/main/java/com/porganization/today/TodayService.java", "backend/src/main/java/com/porganization/today/TodayResponse.java", "frontend/src/app/features/today/sections/today-finance.component.ts"],
    "dependencias": ["F11", "E11"],
    "conceito_angular": "Reuso entre features: a seção importa o TransactionFormComponent standalone da feature de finanças em modo compacto, configurado por input(), sem duplicar o formulário.",
    "status": "pendente",
    "criterios_de_aceite": [
      { "id": "CA1", "descricao": "Fatura com vencimento em 2 dias e não paga aparece em finance.dueSoon." },
      { "id": "CA2", "descricao": "Seção mostra alerta vermelho para orçamento ESTOURADO." }
    ],
    "testes_dos_criterios": [
      { "id": "T1", "criterio": "CA1", "tipo": "integracao", "arquivo": "backend/src/test/java/com/porganization/today/TodayControllerIT.java", "cenario": "Dado hoje 10/10 e fatura vencendo 12/10 OPEN, então finance.dueSoon contém a fatura; se PAID, não contém." },
      { "id": "T2", "criterio": "CA2", "tipo": "componente", "arquivo": "frontend/src/app/features/today/sections/today-finance.component.spec.ts", "cenario": "Dado budget Lazer ESTOURADO, então existe elemento .alert--over com 'Lazer'." }
    ]
  }
]
```

### Etapa 5: Integrações (lembretes, e-mail e Google Calendar)

Observação importante: no plano gratuito o Render desliga a API após um tempo sem acesso, então um `@Scheduled` interno não é confiável para lembretes. As specs abaixo usam um endpoint de disparo protegido por segredo, chamado a cada 5 minutos por um cron externo (ex.: cron-job.org ou `pg_cron` + `pg_net` do Supabase). Se a API ficar num plano sempre ligado, o mesmo serviço pode ser chamado por `@Scheduled`.

```json
[
  {
    "id": "I01",
    "etapa": "5-integracoes",
    "titulo": "Lembretes e disparo agendado",
    "acao": "Criar reminders (commitment_id, minutes_before, channels) e notification_log (reminder_id, occurrence_date, channel, sent_at, unique); criar ReminderDispatcher que encontra lembretes devidos na janela [agora-10min, agora] e POST /internal/reminders/dispatch protegido pelo header X-Cron-Secret.",
    "story": "Como Pedro, quero definir com quanto tempo de antecedência ser lembrado de um compromisso.",
    "arquivos": ["backend/src/main/resources/db/migration/V16__reminders.sql", "backend/src/main/java/com/porganization/notifications/Reminder.java", "backend/src/main/java/com/porganization/notifications/ReminderDispatcher.java", "backend/src/main/java/com/porganization/notifications/InternalCronController.java", "backend/src/main/java/com/porganization/notifications/NotificationChannel.java"],
    "dependencias": ["C05"],
    "status": "pendente",
    "criterios_de_aceite": [
      { "id": "CA1", "descricao": "Compromisso às 15:00 com lembrete de 30 min é disparado por uma chamada entre 14:30 e 14:40, inclusive para ocorrências de recorrentes." },
      { "id": "CA2", "descricao": "Chamar o dispatch duas vezes não envia duplicado (notification_log único)." },
      { "id": "CA3", "descricao": "Sem o segredo correto o endpoint responde 401; ocorrência cancelada ou concluída não dispara." }
    ],
    "testes_dos_criterios": [
      { "id": "T1", "criterio": "CA1", "tipo": "integracao", "arquivo": "backend/src/test/java/com/porganization/notifications/ReminderDispatcherIT.java", "cenario": "Dado Clock 14:31 e série diária às 15:00 com lembrete 30, então o canal fake recebe 1 notificação para a ocorrência de hoje." },
      { "id": "T2", "criterio": "CA2", "tipo": "integracao", "arquivo": "backend/src/test/java/com/porganization/notifications/ReminderDispatcherIT.java", "cenario": "Dado o cenário acima, quando dispatch roda 2 vezes, então o canal fake recebeu 1 chamada." },
      { "id": "T3", "criterio": "CA3", "tipo": "integracao", "arquivo": "backend/src/test/java/com/porganization/notifications/InternalCronControllerIT.java", "cenario": "Dado header ausente, então 401; dado ocorrência com done=true, então 0 notificações." }
    ]
  },
  {
    "id": "I02",
    "etapa": "5-integracoes",
    "titulo": "Lembrete por e-mail",
    "acao": "Adicionar spring-boot-starter-mail, spring-boot-starter-thymeleaf e GreenMail (teste) ao pom.xml. Implementar EmailChannel com Spring Mail (SMTP configurável por variáveis: MAIL_HOST, MAIL_USERNAME, MAIL_PASSWORD, MAIL_FROM) e template HTML simples com título, data, hora e link para o app; o e-mail do usuário vem de user_settings.email, preenchido pelo GET /api/me a partir do claim email (B04).",
    "story": "Como Pedro, quero receber no e-mail o lembrete dos compromissos importantes.",
    "arquivos": ["backend/src/main/java/com/porganization/notifications/email/EmailChannel.java", "backend/src/main/resources/templates/reminder-email.html", "backend/src/main/resources/application.yml", "backend/pom.xml"],
    "dependencias": ["I01"],
    "status": "pendente",
    "criterios_de_aceite": [
      { "id": "CA1", "descricao": "O e-mail chega com assunto 'Lembrete: <título> às <hora>' para o endereço do usuário." },
      { "id": "CA2", "descricao": "Falha no SMTP é registrada no log e não impede os demais lembretes do lote." }
    ],
    "testes_dos_criterios": [
      { "id": "T1", "criterio": "CA1", "tipo": "integracao", "arquivo": "backend/src/test/java/com/porganization/notifications/email/EmailChannelIT.java", "cenario": "Dado GreenMail como SMTP de teste, quando envia lembrete de 'Dentista' às 15:00, então 1 mensagem com o assunto esperado para o e-mail do usuário." },
      { "id": "T2", "criterio": "CA2", "tipo": "unitario", "arquivo": "backend/src/test/java/com/porganization/notifications/ReminderDispatcherTest.java", "cenario": "Dado 2 lembretes e JavaMailSender que lança na primeira chamada, então a segunda é enviada e o log tem a falha." }
    ]
  },
  {
    "id": "I03",
    "etapa": "5-integracoes",
    "titulo": "Lembrete no navegador (Web Push)",
    "acao": "Adicionar @angular/service-worker e Web Push com chaves VAPID: tabela push_subscriptions, endpoints POST/DELETE /api/push/subscriptions, PushChannel no backend (biblioteca nl.martijndwars:web-push + BouncyCastle; WireMock como dependência de teste) e botão 'Ativar notificações' nas Configurações.",
    "story": "Como Pedro, quero receber notificação no navegador ou celular mesmo com o app fechado.",
    "arquivos": ["frontend/ngsw-config.json", "frontend/src/app/core/push/push.service.ts", "frontend/src/app/features/settings/settings.page.ts", "backend/src/main/resources/db/migration/V17__push_subscriptions.sql", "backend/src/main/java/com/porganization/notifications/push/PushChannel.java", "backend/src/main/java/com/porganization/notifications/push/PushSubscriptionController.java"],
    "dependencias": ["I01"],
    "conceito_angular": "Service worker do Angular (SwPush): um script que roda fora da página e recebe notificações push mesmo com o app fechado.",
    "status": "pendente",
    "criterios_de_aceite": [
      { "id": "CA1", "descricao": "Ativar notificações salva a assinatura no backend; desativar a remove." },
      { "id": "CA2", "descricao": "Assinatura que o provedor responde como expirada (404/410) é apagada automaticamente." },
      { "id": "CA3", "descricao": "Clicar na notificação abre o app na tela Hoje." }
    ],
    "testes_dos_criterios": [
      { "id": "T1", "criterio": "CA1", "tipo": "unitario", "arquivo": "frontend/src/app/core/push/push.service.spec.ts", "cenario": "Dado SwPush mockado devolvendo subscription, quando enable(), então POST /api/push/subscriptions com endpoint e keys; disable() faz DELETE." },
      { "id": "T2", "criterio": "CA2", "tipo": "integracao", "arquivo": "backend/src/test/java/com/porganization/notifications/push/PushChannelIT.java", "cenario": "Dado WireMock simulando o push service com 410, quando envia, então a linha de push_subscriptions é removida." },
      { "id": "T3", "criterio": "CA3", "tipo": "e2e", "arquivo": "frontend/e2e/push.spec.ts", "cenario": "Dado evento notificationclick simulado no service worker com data.url '/hoje', então uma janela do app é aberta ou focada em /hoje (validar manualmente em produção também)." }
    ]
  },
  {
    "id": "I04",
    "etapa": "5-integracoes",
    "titulo": "Preferências de notificação e lembretes no formulário",
    "acao": "Adicionar em user_settings: canais padrão, antecedência padrão e horário do resumo diário; tela de Configurações para editá-los; no formulário e na criação rápida de compromisso, aplicar o lembrete padrão e permitir mudar ou remover.",
    "story": "Como Pedro, quero que meus compromissos já tenham lembrete padrão sem eu precisar configurar cada um.",
    "arquivos": ["backend/src/main/resources/db/migration/V18__notification_settings.sql", "backend/src/main/java/com/porganization/notifications/SettingsController.java", "frontend/src/app/features/settings/settings.page.ts", "frontend/src/app/features/commitments/form/commitment-form.dialog.ts"],
    "dependencias": ["I02", "I03", "C09"],
    "conceito_angular": "FormArray e valores iniciais vindos de serviço: os lembretes do compromisso são um FormArray pré-preenchido com o padrão das configurações (exposto como signal), e o usuário pode adicionar ou remover itens.",
    "status": "pendente",
    "criterios_de_aceite": [
      { "id": "CA1", "descricao": "Compromisso criado pela quick add recebe o lembrete padrão (ex.: 30 min, push)." },
      { "id": "CA2", "descricao": "Remover o lembrete no formulário apaga o reminder daquele compromisso." }
    ],
    "testes_dos_criterios": [
      { "id": "T1", "criterio": "CA1", "tipo": "integracao", "arquivo": "backend/src/test/java/com/porganization/commitments/CommitmentControllerIT.java", "cenario": "Dado settings com padrão 30/PUSH, quando POST de criação rápida, então existe reminder minutes_before=30 channels=[PUSH]." },
      { "id": "T2", "criterio": "CA2", "tipo": "componente", "arquivo": "frontend/src/app/features/commitments/form/commitment-form.dialog.spec.ts", "cenario": "Dado compromisso com lembrete, quando remove e salva, então o body enviado tem reminders: []." }
    ]
  },
  {
    "id": "I05",
    "etapa": "5-integracoes",
    "titulo": "Resumo diário de estudos e finanças",
    "acao": "Criar DailyDigestService disparado pelo mesmo endpoint de cron no horário configurado: envia um push e/ou e-mail com compromissos do dia, revisões vencidas e contas ou faturas vencendo, reutilizando o TodayService; uma vez por dia por usuário.",
    "story": "Como Pedro, quero um lembrete de manhã com o que tenho no dia, o que revisar e o que pagar.",
    "arquivos": ["backend/src/main/java/com/porganization/notifications/DailyDigestService.java", "backend/src/main/resources/templates/daily-digest-email.html", "backend/src/main/java/com/porganization/notifications/InternalCronController.java"],
    "dependencias": ["I04", "F16"],
    "status": "pendente",
    "criterios_de_aceite": [
      { "id": "CA1", "descricao": "Com horário 07:00, a primeira chamada de cron a partir das 07:00 envia o resumo; as seguintes no mesmo dia não." },
      { "id": "CA2", "descricao": "O conteúdo lista as contagens: N compromissos, N revisões, N vencimentos." }
    ],
    "testes_dos_criterios": [
      { "id": "T1", "criterio": "CA1", "tipo": "integracao", "arquivo": "backend/src/test/java/com/porganization/notifications/DailyDigestIT.java", "cenario": "Dado Clock 06:55, então 0 envios; 07:02, então 1; 07:07, então continua 1." },
      { "id": "T2", "criterio": "CA2", "tipo": "unitario", "arquivo": "backend/src/test/java/com/porganization/notifications/DailyDigestServiceTest.java", "cenario": "Dado TodayResponse com 2 compromissos, 3 revisões e 1 fatura, então o texto contém '2 compromissos', '3 revisões', '1 vencimento'." }
    ]
  },
  {
    "id": "I06",
    "etapa": "5-integracoes",
    "titulo": "Conectar Google Calendar (OAuth)",
    "acao": "Criar fluxo OAuth 2.0 authorization code com escopo calendar.events: GET /api/integrations/google/auth-url, callback que troca o code por tokens, guarda o refresh token criptografado (AES-GCM com chave em GOOGLE_TOKEN_KEY) em google_connections, e botão Conectar/Desconectar nas Configurações.",
    "story": "Como Pedro, quero conectar minha conta Google para meus compromissos aparecerem no Google Agenda.",
    "arquivos": ["backend/src/main/resources/db/migration/V19__google_connections.sql", "backend/src/main/java/com/porganization/integrations/google/GoogleOAuthController.java", "backend/src/main/java/com/porganization/integrations/google/GoogleOAuthService.java", "backend/src/main/java/com/porganization/integrations/google/TokenCrypto.java", "frontend/src/app/features/settings/google-calendar.component.ts"],
    "dependencias": ["I04"],
    "conceito_angular": "Navegação para fora do app e retorno: o botão Conectar leva para a URL do Google, e a rota de Configurações lê o query param de retorno (?google=ok|erro) via input() com withComponentInputBinding() para mostrar o resultado.",
    "status": "pendente",
    "criterios_de_aceite": [
      { "id": "CA1", "descricao": "O parâmetro state do OAuth é assinado e ligado ao usuário; state inválido no callback responde 400." },
      { "id": "CA2", "descricao": "O refresh token nunca é salvo em texto puro e nunca volta em resposta da API." },
      { "id": "CA3", "descricao": "Desconectar revoga o token no Google e apaga a conexão." }
    ],
    "testes_dos_criterios": [
      { "id": "T1", "criterio": "CA1", "tipo": "integracao", "arquivo": "backend/src/test/java/com/porganization/integrations/google/GoogleOAuthIT.java", "cenario": "Dado WireMock como token endpoint, quando callback com state adulterado, então 400 e nenhuma conexão salva." },
      { "id": "T2", "criterio": "CA2", "tipo": "unitario", "arquivo": "backend/src/test/java/com/porganization/integrations/google/TokenCryptoTest.java", "cenario": "Dado token 'abc', então encrypt != 'abc', decrypt(encrypt) == 'abc' e dois encrypts geram cifras diferentes." },
      { "id": "T3", "criterio": "CA3", "tipo": "integracao", "arquivo": "backend/src/test/java/com/porganization/integrations/google/GoogleOAuthIT.java", "cenario": "Dado conexão existente, quando DELETE, então WireMock recebeu POST /revoke e a tabela não tem a linha." }
    ]
  },
  {
    "id": "I07",
    "etapa": "5-integracoes",
    "titulo": "Sincronizar do app para o Google Calendar",
    "acao": "Ao criar, editar ou excluir compromisso (incluindo recorrência convertida para RRULE e exceções), publicar evento no calendário escolhido via Google Calendar API, guardando google_event_id (migração V20 adiciona em commitments google_event_id text null e sync_pending boolean not null default false); usar evento de domínio após commit (@TransactionalEventListener) para não travar a resposta.",
    "story": "Como Pedro, quero que o que eu criar no app apareça automaticamente no Google Agenda.",
    "arquivos": ["backend/src/main/java/com/porganization/integrations/google/GoogleCalendarClient.java", "backend/src/main/java/com/porganization/integrations/google/CommitmentSyncListener.java", "backend/src/main/java/com/porganization/integrations/google/RRuleMapper.java", "backend/src/main/resources/db/migration/V20__google_sync_columns.sql"],
    "dependencias": ["I06"],
    "status": "pendente",
    "criterios_de_aceite": [
      { "id": "CA1", "descricao": "Criar compromisso gera 1 evento no Google e guarda o id; editar faz PATCH no mesmo evento; excluir faz DELETE." },
      { "id": "CA2", "descricao": "RecurrenceRule semanal seg/qua até 31/12 vira 'RRULE:FREQ=WEEKLY;BYDAY=MO,WE;UNTIL=20261231T235959Z'." },
      { "id": "CA3", "descricao": "Falha na API do Google não impede salvar o compromisso; fica marcado sync_pending para nova tentativa no próximo cron." }
    ],
    "testes_dos_criterios": [
      { "id": "T1", "criterio": "CA1", "tipo": "integracao", "arquivo": "backend/src/test/java/com/porganization/integrations/google/CommitmentSyncIT.java", "cenario": "Dado WireMock da Calendar API, quando cria, edita e exclui, então recebe POST, PATCH e DELETE com o mesmo eventId." },
      { "id": "T2", "criterio": "CA2", "tipo": "unitario", "arquivo": "backend/src/test/java/com/porganization/integrations/google/RRuleMapperTest.java", "cenario": "Dado a regra descrita, então toRRule retorna exatamente a string esperada; também testa DAILY count=5 e MONTHLY." },
      { "id": "T3", "criterio": "CA3", "tipo": "integracao", "arquivo": "backend/src/test/java/com/porganization/integrations/google/CommitmentSyncIT.java", "cenario": "Dado WireMock respondendo 500, quando POST compromisso, então 201 e o compromisso tem sync_pending=true; após cron com WireMock 200, sync_pending=false." }
    ]
  },
  {
    "id": "I08",
    "etapa": "5-integracoes",
    "titulo": "Importar do Google Calendar para o app",
    "acao": "No cron, buscar mudanças do calendário com syncToken (sincronização incremental) e criar, atualizar ou remover compromissos de origem GOOGLE; eventos criados pelo próprio app (marcados com extendedProperties.private.porganizationId) são ignorados para não duplicar; tratar 410 refazendo a sincronização completa.",
    "story": "Como Pedro, quero ver no app os eventos que criei direto no Google Agenda.",
    "arquivos": ["backend/src/main/java/com/porganization/integrations/google/GoogleImportService.java", "backend/src/main/resources/db/migration/V21__google_sync_state.sql", "backend/src/main/java/com/porganization/notifications/InternalCronController.java"],
    "dependencias": ["I07"],
    "status": "pendente",
    "criterios_de_aceite": [
      { "id": "CA1", "descricao": "Evento novo no Google vira compromisso com source=GOOGLE; evento cancelado remove o compromisso." },
      { "id": "CA2", "descricao": "Evento que o app mesmo criou não é importado de volta." },
      { "id": "CA3", "descricao": "Resposta 410 (syncToken expirado) dispara sincronização completa sem duplicar." }
    ],
    "testes_dos_criterios": [
      { "id": "T1", "criterio": "CA1", "tipo": "integracao", "arquivo": "backend/src/test/java/com/porganization/integrations/google/GoogleImportIT.java", "cenario": "Dado WireMock devolvendo 1 evento confirmado e depois status cancelled, então 1 compromisso GOOGLE e em seguida 0." },
      { "id": "T2", "criterio": "CA2", "tipo": "integracao", "arquivo": "backend/src/test/java/com/porganization/integrations/google/GoogleImportIT.java", "cenario": "Dado evento com extendedProperties.private.porganizationId, então nenhum compromisso novo é criado." },
      { "id": "T3", "criterio": "CA3", "tipo": "integracao", "arquivo": "backend/src/test/java/com/porganization/integrations/google/GoogleImportIT.java", "cenario": "Dado 410 no incremental e 2 eventos no full sync, com 1 já importado antes, então total de compromissos GOOGLE == 2." }
    ]
  }
]
```

## 5. Resumo da ordem

| Etapa | Specs | Resultado ao final |
|---|---|---|
| 1 Base | B01 a B13 | Login funcionando, navegação, CI, deploy no Render/Vercel/Supabase e proteção contra vazamento de segredos |
| 2 Compromissos | C01 a C10 | Criação rápida, recorrência, visões Hoje/Semana/Mês/Ano, tela Hoje com compromissos |
| 3 Estudos | E01 a E11 | Matérias com tags e prioridade, timer, revisões em mini aula agendadas pelo FSRS |
| 4 Finanças | F01 a F16 | Contas, transações, cartão com parcelas e faturas, fixos, orçamentos, metas e dashboard |
| 5 Integrações | I01 a I08 | Lembretes por push e e-mail, resumo diário e Google Calendar nos dois sentidos |

Total: 58 specs. A B13 (proteção de segredos) entrou depois do plano original e vem logo após a B01, antes de qualquer credencial existir. A E05 (FSRS) não depende de nada e pode ser feita a qualquer momento, inclusive como exercício de Java puro antes da etapa 3.

As versões das migrações (V1 a V21) assumem a ordem das etapas. Se uma spec for feita fora de ordem, renumere as migrações pendentes antes do merge para o Flyway não encontrar versões fora de sequência.

## 6. Revisão da stack (2026-09-29)

Correções aplicadas depois de conferir o plano contra Spring Boot 4, Angular 21+ e Supabase:

| # | Problema | Correção | Specs |
|---|---|---|---|
| 1 | Tabelas em `public` ficavam expostas pela Data API do Supabase com a anon key do frontend | RLS ativo sem policies em toda tabela, com teste que barra migração nova sem RLS | B03 (CA4) |
| 2 | JWKS do Supabase vem vazio com a chave legada HS256; issuer e audience não eram validados | JWT Signing Keys assimétricas, `SUPABASE_ISSUER` e audience `authenticated` | B04 (CA4), B11 |
| 3 | Conexão direta do Supabase é só IPv6 (o Render não tem) e o pooler transaction quebra JDBC e Flyway | Session pooler na porta 5432, pool pequeno, Testcontainers com Postgres 17 | B03, B11 |
| 4 | Boot 4 modularizou os starters; só `flyway-core` não é autoconfigurado | Starters modulares, `spring-boot-starter-flyway` + `flyway-database-postgresql` | B02 |
| 5 | Jackson 3 serializa `BigDecimal` como número | `JacksonConfig` com `BigDecimal` como string | F01 |
| 6 | Dependências usadas mas fora da stack | Spring Mail, Thymeleaf, GreenMail, web-push + BouncyCastle, WireMock | I02, I03 |
| 7 | `fakeAsync`/`tick` não funcionam em app zoneless com Vitest | Teste do timer com `vi.useFakeTimers()` | E09 (T1) |
| 8 | `ng new` gera `app.ts` sem sufixo e sem `environments/` | Schematics com sufixo de tipo e `ng generate environments` | B06 |
| 9 | FK de `card_statement_id` antes de existir `card_statements` | Coluna sem FK na F03; FK adicionada na V11 | F03, F05 |
| 10 | `user_settings` não tinha a coluna de e-mail usada pelos lembretes | `email` na V1, preenchido pelo GET /api/me | B03, B04 (CA5), I02 |
| 11 | `sync_pending` não aparecia em nenhuma migração | V20 adiciona `google_event_id` e `sync_pending` | I07 |
| 12 | Specs com componentes Angular sem `conceito_angular` | Conceito adicionado | E11, F16, I04, I06 |
| 13 | Repositório público sem barreiras contra commit de segredos | Spec nova com check-secrets, hook pre-commit, gitleaks no CI, push protection e ruleset da main; Actuator sem valores; publishable key no frontend; segredos do Render com `sync: false` | B13, B02, B03, B06, B11 |
