# Intelly Design System

Painel clínico suave: sidebar escura, cards grandes arredondados, botões em pílula, formas decorativas nos cards de métrica. Plano, sem sombras.

## Como usar

```html
<link rel="stylesheet" href="design-system/tokens.css">
<link rel="stylesheet" href="design-system/components.css">
<html data-theme="light"> <!-- ou "dark" -->
```

Nunca escreva cor, espaçamento ou raio direto. Use sempre `var(--token)`.

## Paleta (4 cores base)

| Token | Valor | Papel |
| --- | --- | --- |
| `--lavender` | #d3d5fd | Destaque: cards de ênfase, dia selecionado, ícones em círculo |
| `--steel` | #929aab | Card neutro de ênfase média; texto de apoio só sobre fundo escuro |
| `--slate` | #474a56 | Texto secundário no claro, bordas de controle, card escuro |
| `--ink` | #0b0b0d | Texto principal, sidebar, botão primário, fundo do tema escuro |

Derivados (calculados, não vieram da paleta): `--lavender-50` #f3f4ff (fundo de página), `--lavender-100` #e7e8fe (cards neutros), `--ink-800` #18181c (superfícies no escuro).

## Tokens semânticos (use estes nos componentes)

- Fundo: `--bg-page`, `--bg-card`, `--bg-sidebar`
- Texto: `--fg-primary`, `--fg-secondary`, `--fg-on-dark`, `--fg-on-dark-muted`
- Ação: `--action-bg` / `--action-fg` (botão primário), `--accent` / `--fg-on-accent`
- Cards de ênfase, sempre em par: `--surface-lavender` + `--on-surface-lavender`, `--surface-steel` + `--on-surface-steel`, `--surface-slate` + `--on-surface-slate`, `--surface-ink` + `--on-surface-ink`
- Bordas: `--border-soft` (só divisória decorativa), `--border-strong` (controles, passa 3:1)
- Foco: `--focus-ring` (2px, offset 2px em todo controle)

## Regras de contraste

- `--steel` nunca é texto sobre fundo claro (2,4:1) nem sobre `--slate` (3,2:1). Só sobre ink/ink-800 (7:1).
- Sobre `--surface-steel` use só ink, nunca slate.
- Pares verificados: ink/lavender ≈ 14:1, ink/steel ≈ 7:1, slate/lavender ≈ 6:1, lavender/ink ≈ 13:1.
- Não misture pares `on-surface-*` com outras superfícies.

## Espaçamento e raios

Espaço: 4, 8, 12, 16, 20, 24, 32, 40 (`--space-1` a `--space-10`, sem o 7 e o 9). Card: padding `--space-5`, gap entre cards `--space-4`.
Raios: `--radius-sm` 8, `--radius-md` 14 (itens, linhas, eventos), `--radius-lg` 20 (cards), `--radius-xl` 28 (sidebar, moldura), `--radius-pill` (botões, busca, chips, tags, avatares).

## Tipografia

Manrope (fonte hospedada, cai para system-ui). Classes prontas: `.display` 40, `.heading-1` 28, `.heading-2` 20, `.heading-3` 16, `.stat` 28/700, `.body` 14, `.body-strong` 14/600, `.caption` 12, `.micro` 10.

## Componentes (classes em components.css)

- **Botão:** `.ds-btn` (+ `--accent`, `--quiet`, `--muted`, `--sm`, `--icon`). Um primário por área. Só ícone exige `aria-label`.
- **Busca:** `.ds-search` com `.ds-go`, `input`, `.ds-in`, `.ds-chip` (`--on`).
- **Sidebar:** `.ds-sidenav` com `.ds-logo`, `.ds-label`, `.ds-navitem` (ativo: `aria-current="page"`).
- **Card de métrica:** `.ds-card` + `--lavender|--steel|--slate|--ink`, `.ds-stat`, `.ds-unit`, forma decorativa `.ds-shape` + `--burst|--tri|--disc` (`aria-hidden`).
- **Evento:** `.ds-event` (+ `--lavender`, `--steel`) com `.t`, `.m`, `.row`.
- **Avatar:** `.ds-avatar` (+ `--steel|--slate|--ink|--lg`) dentro de `.ds-avatars`.
- **Tag:** `.ds-tag` (+ `--lavender|--steel|--ink`).
- **Linha de lista:** `.ds-listrow` (+ `--active`) com `.ic`, `.n`, `.s`, `.ds-time`.
- **Mini calendário:** `.ds-cal` com `.hd`, `.mo`, `.g`, `.w`, `.d` (`.o` fora do mês, `.sel` selecionado).
- **Seletor segmentado:** `.ds-seg` com `button[aria-selected]`.
- Ícones: traço, 18px, 1,75px, pontas arredondadas, `currentColor` (`.ds-icon`).

## Não coberto

Cores de status (sucesso/alerta/erro), logo original, gráficos de barra e linha.
