import { Component, computed, inject, input } from '@angular/core';
import { ChartConfiguration, ChartData } from 'chart.js';
import { BaseChartDirective } from 'ng2-charts';
import { ThemeService } from '../../../../core/theme/theme.service';
import { MonthPoint } from '../../data/finance.model';
import { chartTheme } from './chart-theme';

const MESES = ['jan', 'fev', 'mar', 'abr', 'mai', 'jun', 'jul', 'ago', 'set', 'out', 'nov', 'dez'];

/** Barras de renda x gasto dos últimos 6 meses (os dados vêm por input()), nas cores de status do tema. */
@Component({
  selector: 'app-monthly-chart',
  imports: [BaseChartDirective],
  template: `<canvas
    baseChart
    type="bar"
    [data]="chartData()"
    [options]="options()"
    aria-label="Renda e gasto por mês"
  ></canvas>`,
  styles: `
    :host {
      display: block;
      position: relative;
    }
  `,
})
export class MonthlyChartComponent {
  readonly points = input.required<MonthPoint[]>();

  private readonly themes = inject(ThemeService);
  private readonly tema = computed(() => chartTheme(this.themes.theme()));

  readonly chartData = computed<ChartData<'bar', number[], string>>(() => {
    const pontos = this.points();
    const cores = this.tema();
    return {
      labels: pontos.map((p) => MESES[Number(p.month.slice(5, 7)) - 1]),
      datasets: [
        {
          label: 'Renda',
          data: pontos.map((p) => Number(p.income)),
          backgroundColor: cores.positive,
          borderRadius: 6,
        },
        {
          label: 'Gasto',
          data: pontos.map((p) => Number(p.expense)),
          backgroundColor: cores.negative,
          borderRadius: 6,
        },
      ],
    };
  });

  protected readonly options = computed<ChartConfiguration<'bar'>['options']>(() => {
    const { text, grid, font } = this.tema();
    return {
      responsive: true,
      plugins: { legend: { position: 'bottom', labels: { color: text, font: { family: font } } } },
      scales: {
        x: { ticks: { color: text, font: { family: font } }, grid: { display: false } },
        y: {
          beginAtZero: true,
          grid: { color: grid },
          border: { display: false },
          ticks: {
            color: text,
            font: { family: font },
            callback: (v) => Number(v).toLocaleString('pt-BR'),
          },
        },
      },
    };
  });
}
