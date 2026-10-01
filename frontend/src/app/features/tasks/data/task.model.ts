import { WeekDay } from '../../commitments/data/commitment.model';

/** Tipos da API de tarefas diárias (/api/tasks). */

export interface DailyTask {
  id: string;
  title: string;
  emoji: string | null;
  /** Os dias que valem hoje (a regra atual). */
  weekDays: WeekDay[];
  position: number;
  archived: boolean;
  createdOn: string;
  /** Horário do lembrete (HH:mm); null = sem lembrete. */
  reminderTime: string | null;
  /** Minutos do cronômetro que conclui a tarefa; null = sem cronômetro. */
  timerMinutes: number | null;
}

/** weekDays ausente = todo dia; reminderTime null = sem lembrete. */
export interface DailyTaskRequest {
  title: string;
  emoji: string | null;
  weekDays: WeekDay[];
  reminderTime: string | null;
  timerMinutes: number | null;
}

/** Uma tarefa devida no dia, com feito ou não. */
export interface DayTask {
  id: string;
  title: string;
  emoji: string | null;
  position: number;
  done: boolean;
  timerMinutes: number | null;
}

export interface TaskStats {
  taskId: string;
  streak: number;
  /** % dos dias devidos nos últimos 30 dias; null sem nenhum dia devido ainda. */
  completionRate: string | null;
}
