// Tipos dos DTOs da API de compromissos (backend: com.porganization.commitments.dto)

/** Data no formato ISO "YYYY-MM-DD". */
export type IsoDate = string;
/** Horário "HH:mm". */
export type TimeOfDay = string;

export type Frequency = 'DAILY' | 'WEEKLY' | 'MONTHLY' | 'YEARLY';
export type WeekDay = 'MON' | 'TUE' | 'WED' | 'THU' | 'FRI' | 'SAT' | 'SUN';

export interface RecurrenceRule {
  freq: Frequency;
  interval?: number | null;
  byWeekDays?: WeekDay[] | null;
  until?: IsoDate | null;
  count?: number | null;
}

export interface Commitment {
  id: string;
  title: string;
  date: IsoDate;
  startTime: TimeOfDay | null;
  endTime: TimeOfDay | null;
  allDay: boolean;
  description: string | null;
  location: string | null;
  done: boolean;
  recurrenceRule: RecurrenceRule | null;
  createdAt: string;
  updatedAt: string;
}

export interface CommitmentRequest {
  title: string;
  date: IsoDate;
  startTime?: TimeOfDay | null;
  endTime?: TimeOfDay | null;
  allDay?: boolean;
  description?: string | null;
  location?: string | null;
  recurrenceRule?: RecurrenceRule | null;
}

/** Um compromisso num dia; séries recorrentes viram uma ocorrência por dia (mesmo commitmentId). */
export interface Occurrence {
  commitmentId: string;
  occurrenceDate: IsoDate;
  title: string;
  startTime: TimeOfDay | null;
  endTime: TimeOfDay | null;
  allDay: boolean;
  done: boolean;
  recurring: boolean;
  description: string | null;
  location: string | null;
}

/** Ajuste de uma ocorrência: só os campos enviados mudam. */
export interface OccurrencePatch {
  done?: boolean;
  cancelled?: boolean;
  title?: string;
  startTime?: TimeOfDay;
}
