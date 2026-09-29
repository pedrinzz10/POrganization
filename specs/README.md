# Specs

O POrganization é construído em **specs**: entregas pequenas e verificáveis, cada uma com ação, arquivos, story, status, critérios de aceite e os testes que provam cada critério.

- **Fonte:** os blocos JSON da seção 4 do [`PLANO_IMPLEMENTACAO.md`](../PLANO_IMPLEMENTACAO.md).
- **Consolidado:** [`specs.json`](../specs.json), na raiz, gerado a partir do plano. Depois de editar uma spec no plano, rode:

  ```bash
  python scripts/build_specs.py
  ```

  O script falha se alguma spec violar as regras abaixo (critério sem teste, dependência inexistente, status ou tipo de teste inválido).

## Esquema

```json
{
  "id": "C02",
  "etapa": "2-compromissos",
  "titulo": "API de criação rápida e CRUD de compromissos",
  "acao": "O que deve ser feito, em uma ou duas frases imperativas.",
  "story": "Como Pedro, quero ... para ...",
  "arquivos": ["caminho/relativo/ao/repo (criar ou alterar)"],
  "dependencias": ["C01"],
  "conceito_angular": "Só em specs de frontend: o conceito do Angular que a spec introduz.",
  "status": "pendente",
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

## Regras

- `status` segue `pendente` → `em_andamento` → `em_revisao` → `concluida`. Use `bloqueada`, com uma nota, quando depender de algo externo.
- Todo critério tem pelo menos um teste em `testes_dos_criterios` apontando para ele pelo campo `criterio`.
- `arquivos` é a melhor previsão. Arquivos auxiliares fora da lista são permitidos, mas devem ser citados no PR.
- `dependencias` diz o que precisa estar `concluida` antes.
- Uma spec = um branch = um PR pequeno.

## Fluxo de uma spec

1. Mudar o status para `em_andamento`.
2. Escrever primeiro os testes de `testes_dos_criterios` e ver que falham.
3. Implementar até todos passarem.
4. Rodar `./mvnw verify` (backend) e/ou `npm test` (frontend).
5. Mudar o status para `em_revisao` e abrir o PR.
6. Depois do merge, mudar o status para `concluida`.

## Etapas

| Etapa | Specs | Resultado ao final |
|---|---|---|
| 1 Base | B01 a B13 | Login, navegação, CI, deploy e proteção contra vazamento de segredos |
| 2 Compromissos | C01 a C10 | Criação rápida, recorrência, visões Hoje/Semana/Mês/Ano |
| 3 Estudos | E01 a E11 | Matérias com tags e prioridade, timer, revisões agendadas pelo FSRS |
| 4 Finanças | F01 a F16 | Contas, cartão com parcelas e faturas, fixos, orçamentos, metas e dashboard |
| 5 Integrações | I01 a I08 | Lembretes por push e e-mail, resumo diário e Google Calendar |
