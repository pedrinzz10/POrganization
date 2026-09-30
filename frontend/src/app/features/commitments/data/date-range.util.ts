import { IsoDate } from './commitment.model';

/** Data local no formato da API ("YYYY-MM-DD"), sem passar por UTC. */
export function toIsoDate(date: Date): IsoDate {
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, '0');
  const d = String(date.getDate()).padStart(2, '0');
  return `${y}-${m}-${d}`;
}

/** Converte "YYYY-MM-DD" em Date local (meio-dia, para não virar o dia anterior em nenhum fuso). */
export function parseIsoDate(iso: IsoDate): Date {
  const [y, m, d] = iso.split('-').map(Number);
  return new Date(y, m - 1, d, 12);
}

export function today(): IsoDate {
  return toIsoDate(new Date());
}
