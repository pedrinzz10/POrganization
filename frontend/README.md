# POrganization: frontend

App Angular 22 (standalone, zoneless, signals) com Angular Material.

| Comando | O que faz |
|---|---|
| `npm start` | servidor de desenvolvimento em http://localhost:4200 |
| `npm test -- --watch=false` | testes unitários e de componente (Vitest) |
| `npm run build` | build de produção em `dist/` |

As URLs da API e do Supabase ficam em `src/environments/`. Só valores públicos entram ali: a URL do projeto e a **publishable key** (`sb_publishable_...`) do Supabase, nunca a secret key.

Novos arquivos seguem a convenção com sufixo (`*.component.ts`, `*.service.ts`), já configurada no `angular.json`: `npx ng generate component features/x` gera `x.component.ts` com a classe `XComponent`.
