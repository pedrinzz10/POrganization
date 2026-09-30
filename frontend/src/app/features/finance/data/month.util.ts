/** Meses no formato da API ("2026-10") e datas locais ("2026-10-15"), sem fuso no meio. */

function pad(n: number): string {
  return String(n).padStart(2, '0');
}

export function todayIso(now = new Date()): string {
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`;
}

export function currentMonth(now = new Date()): string {
  return todayIso(now).slice(0, 7);
}

export function addMonths(month: string, delta: number): string {
  const [ano, mes] = month.split('-').map(Number);
  const total = ano * 12 + (mes - 1) + delta;
  return `${Math.floor(total / 12)}-${pad((total % 12) + 1)}`;
}

/** "2026-10" → "outubro de 2026". */
export function monthLabel(month: string): string {
  const [ano, mes] = month.split('-').map(Number);
  return new Intl.DateTimeFormat('pt-BR', { month: 'long', year: 'numeric' }).format(new Date(ano, mes - 1, 1));
}

/** "2026-10" válido? (query param vindo da URL) */
export function isMonth(value: string | null | undefined): value is string {
  return !!value && /^\d{4}-(0[1-9]|1[0-2])$/.test(value);
}
