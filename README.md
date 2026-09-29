# POrganization

Organizador pessoal que reúne **compromissos**, **estudos** e **finanças** em um só app, com uma tela **Hoje** que mostra o que fazer, o que revisar e o que pagar no dia.

> Projeto em construção. O README completo, com arquitetura, execução local e deploy, chega na spec B12.

## Funcionalidades planejadas

- **Compromissos:** criação rápida (título, dia e hora), recorrência, visões de dia, semana, mês e ano.
- **Estudos:** matérias com tags e prioridade, timer de estudo e revisões em mini aulas agendadas por repetição espaçada (FSRS).
- **Finanças:** contas, transações, cartão de crédito com fatura e parcelas, gastos fixos, orçamentos com alerta, metas de economia e dashboard.
- **Integrações:** lembretes por e-mail e push, resumo diário e sincronização com o Google Calendar.

## Stack

| Camada | Tecnologia |
|---|---|
| Backend | Java 21, Spring Boot 4, Spring Security (OAuth2 Resource Server), Data JPA, Flyway |
| Frontend | Angular 21+ (standalone, signals, zoneless), Angular Material |
| Banco e auth | Supabase (Postgres e Supabase Auth) |
| Deploy | Render (API em Docker), Vercel (frontend) |
| Testes | JUnit 5, Testcontainers, MockMvc, Vitest, Playwright |

## Estrutura

```
backend/    API Spring Boot
frontend/   app Angular
specs/      esquema e fluxo das specs
scripts/    verificações e utilitários do repositório
specs.json  todas as specs do plano, geradas a partir do PLANO_IMPLEMENTACAO.md
```

## Como o projeto é desenvolvido

O trabalho é dividido em 57 specs descritas no [`PLANO_IMPLEMENTACAO.md`](PLANO_IMPLEMENTACAO.md). Cada spec tem critérios de aceite e os testes que os provam, e vira um branch e um PR. O esquema e o fluxo estão em [`specs/README.md`](specs/README.md).
