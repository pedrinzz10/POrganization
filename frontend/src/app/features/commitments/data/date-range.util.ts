import { IsoDate } from './commitment.model';

/** Intervalo de datas inclusivo, como a API recebe (from/to). */
export interface DateRange {
  from: IsoDate;
  to: IsoDate;
}

/** Data local no formato da API ("YYYY-MM-DD"), sem passar por UTC. */
export function toIsoDate(date: Date): IsoDate {
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, '0');
  const d = String(date.getDate()).padStart(2, '0');
  return `${y}-${m}-${d}`;
}

/** Converte "YYYY-MM-DD" em Date local ao meio-dia: mudanças de horário não trocam o dia. */
export function parseIsoDate(iso: IsoDate): Date {
  const [y, m, d] = iso.split('-').map(Number);
  return new Date(y, m - 1, d, 12);
}

export function today(): IsoDate {
  return toIsoDate(new Date());
}

export function addDays(iso: IsoDate, days: number): IsoDate {
  const date = parseIsoDate(iso);
  date.setDate(date.getDate() + days);
  return toIsoDate(date);
}

/** Semana de segunda a domingo que contém a data. */
export function weekRange(iso: IsoDate): DateRange {
  const weekday = parseIsoDate(iso).getDay(); // 0 = domingo
  const from = addDays(iso, -((weekday + 6) % 7));
  return { from, to: addDays(from, 6) };
}

export function nextWeek(range: DateRange): DateRange {
  return { from: addDays(range.from, 7), to: addDays(range.to, 7) };
}

export function previousWeek(range: DateRange): DateRange {
  return { from: addDays(range.from, -7), to: addDays(range.to, -7) };
}

/** Todos os dias de from a to, em ordem. */
export function daysOf(range: DateRange): IsoDate[] {
  const days: IsoDate[] = [];
  for (let day = range.from; day <= range.to; day = addDays(day, 1)) {
    days.push(day);
  }
  return days;
}
