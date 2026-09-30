import { Occurrence } from '../data/commitment.model';

/** Mesma ordem da API: data, dia todo primeiro, horário, título. */
export function compareOccurrences(a: Occurrence, b: Occurrence): number {
  return (
    a.occurrenceDate.localeCompare(b.occurrenceDate) ||
    Number(!a.allDay) - Number(!b.allDay) ||
    (a.startTime ?? '').localeCompare(b.startTime ?? '') ||
    a.title.localeCompare(b.title, 'pt-BR')
  );
}

/** Chave única de uma ocorrência (a mesma série aparece em vários dias). */
export function occurrenceKey(o: Occurrence): string {
  return `${o.commitmentId}|${o.occurrenceDate}`;
}
