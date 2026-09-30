/**
 * Em que fatura cai uma compra feita nesta data (mesma regra do StatementResolver da API).
 * Devolve o mês de vencimento ("2026-11"), que é como as faturas são identificadas.
 * - Antes do dia de fechamento: fatura que fecha neste mês; no dia ou depois: a do mês seguinte.
 * - Vence no mesmo mês do fechamento se o vencimento é depois do fechamento; senão, no seguinte.
 */
export function statementMonthFor(closingDay: number, dueDay: number, date: string): string {
  const [ano, mes, dia] = date.split('-').map(Number);
  const ultimoDia = new Date(ano, mes, 0).getDate();
  let fechamento = ano * 12 + (mes - 1);
  if (dia >= Math.min(closingDay, ultimoDia)) {
    fechamento += 1;
  }
  const vencimento = dueDay > closingDay ? fechamento : fechamento + 1;
  return `${Math.floor(vencimento / 12)}-${String((vencimento % 12) + 1).padStart(2, '0')}`;
}
