import { Component, computed, input } from '@angular/core';
import { ChartConfiguration, ChartData } from 'chart.js';
import { BaseChartDirective } from 'ng2-charts';
import { CategorySpend } from '../../data/finance.model';

const CORES_RESERVA = ['#1976D2', '#F57C00', '#8E24AA', '#43A047', '#E53935', '#3949AB', '#6D4C41', '#757575'];

/**
 * Pizza (rosca) de gastos por categoria. Os dados chegam por input() e um computed() monta a
 * configuração do Chart.js, maiores primeiro, com a cor de cada categoria.
 */
@Component({
  selector: 'app-category-chart',
  imports: [BaseChartDirective],
  template: `<canvas baseChart type="doughnut" [data]="chartData()" [options]="options" aria-label="Gastos por categoria"></canvas>`,
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

  readonly chartData = computed<ChartData<'doughnut', number[], string>>(() => {
    const ordenadas = [...this.categories()].sort((a, b) => Number(b.total) - Number(a.total));
    return {
      labels: ordenadas.map((c) => c.name ?? 'Sem categoria'),
      datasets: [
        {
          data: ordenadas.map((c) => Number(c.total)),
          backgroundColor: ordenadas.map((c, i) => c.color ?? CORES_RESERVA[i % CORES_RESERVA.length]),
        },
      ],
    };
  });

  protected readonly options: ChartConfiguration<'doughnut'>['options'] = {
    responsive: true,
    plugins: {
      legend: { position: 'bottom' },
      tooltip: {
        callbacks: {
          label: (item) => ` ${item.label}: ${Number(item.raw).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })}`,
        },
      },
    },
  };
}
