import { Theme } from '../../../../core/theme/theme.service';

/** Cores e fonte do Intelly Design System para o Chart.js, que não lê variáveis CSS sozinho. */
export interface ChartTheme {
  text: string;
  grid: string;
  surface: string;
  positive: string;
  negative: string;
  font: string;
  /** Para categoria sem cor escolhida: a paleta do design system. */
  reserve: string[];
}

/**
 * Lê os tokens do <html> no momento de desenhar. Recebe o tema só para quem chama declarar a
 * dependência (num computed, trocar o tema redesenha o gráfico com as cores novas).
 */
export function chartTheme(
  _theme: Theme,
  root: HTMLElement = document.documentElement,
): ChartTheme {
  const css = getComputedStyle(root);
  const token = (name: string, fallback: string) => css.getPropertyValue(name).trim() || fallback;
  return {
    text: token('--fg-secondary', '#474a56'),
    grid: token('--border-soft', '#929aab66'),
    surface: token('--bg-card', '#e7e8fe'),
    positive: token('--status-positive', '#1d6b3a'),
    negative: token('--status-negative', '#b3261e'),
    font: token('--font-sans', 'system-ui, sans-serif'),
    reserve: [
      token('--slate', '#474a56'),
      token('--steel', '#929aab'),
      token('--lavender', '#d3d5fd'),
      token('--ink', '#0b0b0d'),
    ],
  };
}
