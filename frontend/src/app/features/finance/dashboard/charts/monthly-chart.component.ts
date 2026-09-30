import { Component, computed, input } from '@angular/core';
import { ChartConfiguration, ChartData } from 'chart.js';
import { BaseChartDirective } from 'ng2-charts';
import { MonthPoint } from '../../data/finance.model';

const MESES = ['jan', 'fev', 'mar', 'abr', 'mai', 'jun', 'jul', 'ago', 'set', 'out', 'nov', 'dez'];

/** Barras de renda x gasto dos últimos 6 meses (os dados vêm por input()). */
@Component({
  selector: 'app-monthly-chart',
  imports: [BaseChartDirective],
  template: `<canvas baseChart type="bar" [data]="chartData()" [options]="options" aria-label="Renda e gasto por mês"></canvas>`,
  styles: `
    :host {
      display: block;
      position: relative;
    }
  `,
})
export class MonthlyChartComponent {
  readonly points = input.required<MonthPoint[]>();

  readonly chartData = computed<ChartData<'bar', number[], string>>(() => {
    const pontos = this.points();
    return {
      labels: pontos.map((p) => MESES[Number(p.month.slice(5, 7)) - 1]),
      datasets: [
        { label: 'Renda', data: pontos.map((p) => Number(p.income)), backgroundColor: '#43A047' },
        { label: 'Gasto', data: pontos.map((p) => Number(p.expense)), backgroundColor: '#E53935' },
      ],
    };
  });

  protected readonly options: ChartConfiguration<'bar'>['options'] = {
    responsive: true,
    plugins: { legend: { position: 'bottom' } },
    scales: {
      y: { beginAtZero: true, ticks: { callback: (v) => Number(v).toLocaleString('pt-BR') } },
    },
  };
}
