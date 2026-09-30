import { Component, computed, inject, input } from '@angular/core';
import { ChartConfiguration, ChartData } from 'chart.js';
import { BaseChartDirective } from 'ng2-charts';
import { ThemeService } from '../../../../core/theme/theme.service';
import { CategorySpend } from '../../data/finance.model';
import { chartTheme } from './chart-theme';

/**
 * Pizza (rosca) de gastos por categoria. Os dados chegam por input() e um computed() monta a
 * configuração do Chart.js, maiores primeiro, com a cor de cada categoria (sem cor: a paleta do
 * design system). Textos e bordas vêm dos tokens e acompanham o tema claro/escuro.
 */
@Component({
  selector: 'app-category-chart',
  imports: [BaseChartDirective],
  template: `<canvas
    baseChart
    type="doughnut"
    [data]="chartData()"
    [options]="options()"
    aria-label="Gastos por categoria"
  ></canvas>`,
  styles: `
    :host {
      display: block;
      position: relative;
      max-width: 360px;
      margin: 0 auto;
    }
  `,
})
export class CategoryChartComponent {
  readonly categories = input.required<CategorySpend[]>();

  private readonly themes = inject(ThemeService);
  private readonly tema = computed(() => chartTheme(this.themes.theme()));

  readonly chartData = computed<ChartData<'doughnut', number[], string>>(() => {
    const cores = this.tema();
    const ordenadas = [...this.categories()].sort((a, b) => Number(b.total) - Number(a.total));
    return {
      labels: ordenadas.map((c) => c.name ?? 'Sem categoria'),
      datasets: [
        {
          data: ordenadas.map((c) => Number(c.total)),
          backgroundColor: ordenadas.map(
            (c, i) => c.color ?? cores.reserve[i % cores.reserve.length],
          ),
          borderColor: cores.surface,
          borderWidth: 2,
        },
      ],
    };
  });

  protected readonly options = computed<ChartConfiguration<'doughnut'>['options']>(() => ({
    responsive: true,
    plugins: {
      legend: {
        position: 'bottom',
        labels: { color: this.tema().text, font: { family: this.tema().font } },
      },
      tooltip: {
        callbacks: {
          label: (item) =>
            ` ${item.label}: ${Number(item.raw).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })}`,
        },
      },
    },
  }));
}
