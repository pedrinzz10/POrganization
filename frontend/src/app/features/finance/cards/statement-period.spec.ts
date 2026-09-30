import { statementMonthFor } from './statement-period';

describe('statementMonthFor', () => {
  it.each([
    // fechamento 5, vencimento 12
    [5, 12, '2026-10-04', '2026-10'],
    [5, 12, '2026-10-05', '2026-11'],
    [5, 12, '2026-10-20', '2026-11'],
    // vencimento antes do fechamento: vence no mês seguinte ao fechamento
    [25, 5, '2026-10-10', '2026-11'],
    [25, 5, '2026-10-26', '2026-12'],
    // fechamento 31 em fevereiro fecha no último dia
    [31, 10, '2027-02-27', '2027-03'],
    [31, 10, '2027-02-28', '2027-04'],
    // virada de ano
    [5, 12, '2026-12-20', '2027-01'],
  ])('fecha %i, vence %i, compra em %s → fatura de %s', (fecha, vence, data, esperado) => {
    expect(statementMonthFor(fecha, vence, data)).toBe(esperado);
  });
});
