import { TestBed } from '@angular/core/testing';
import { provideFinanceCharts } from './charts.providers';
import { CategoryChartComponent } from './category-chart.component';
import { MonthlyChartComponent } from './monthly-chart.component';

describe('gráficos do resumo', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideFinanceCharts()] });
  });

  // F15 T1 (CA1)
  it('categorias vão para o gráfico com os maiores primeiro', () => {
    const fixture = TestBed.createComponent(CategoryChartComponent);
    fixture.componentRef.setInput('categories', [
      { categoryId: 'l', name: 'Lazer', color: '#8E24AA', total: '50.00' },
      { categoryId: 'm', name: 'Moradia', color: '#6D4C41', total: '1200.00' },
    ]);

    const dados = fixture.componentInstance.chartData();
    expect(dados.labels).toEqual(['Moradia', 'Lazer']);
    expect(dados.datasets[0].data).toEqual([1200, 50]);
    expect(dados.datasets[0].backgroundColor).toEqual(['#6D4C41', '#8E24AA']);
  });

  it('categoria sem cor ganha uma cor da paleta do design system', () => {
    const fixture = TestBed.createComponent(CategoryChartComponent);
    fixture.componentRef.setInput('categories', [
      { categoryId: 'x', name: null, color: null, total: '10.00' },
    ]);

    const dados = fixture.componentInstance.chartData();
    expect(dados.labels).toEqual(['Sem categoria']);
    // Sem os tokens carregados (teste), vale o valor de reserva do --slate
    expect(dados.datasets[0].backgroundColor).toEqual(['#474a56']);
  });

  it('barras: um rótulo por mês e as séries de renda e gasto', () => {
    const fixture = TestBed.createComponent(MonthlyChartComponent);
    fixture.componentRef.setInput('points', [
      { month: '2026-09', income: '5000.00', expense: '0.00' },
      { month: '2026-10', income: '0.00', expense: '80.00' },
    ]);

    const dados = fixture.componentInstance.chartData();
    expect(dados.labels).toEqual(['set', 'out']);
    expect(dados.datasets.map((d) => d.label)).toEqual(['Renda', 'Gasto']);
    expect(dados.datasets[1].data).toEqual([0, 80]);
  });
});
