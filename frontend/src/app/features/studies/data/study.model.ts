// Tipos dos DTOs da API de estudos (backend: com.porganization.studies.dto)
import { WeekDay } from '../../commitments/data/commitment.model';

export interface Tag {
  id: string;
  name: string;
}

export interface Subject {
  id: string;
  name: string;
  /** "#RRGGBB" ou null */
  color: string | null;
  /** 1 = mais importante */
  priorityOrder: number;
  sessionsPerWeek: number;
  lessonMinutes: number;
  archived: boolean;
  tags: Tag[];
  /** Dias em que a matéria pode ter aula; vazio = qualquer dia. */
  studyDays: WeekDay[];
  /** FREE: título digitado ao terminar; PLANNED: a matéria tem a lista de aulas (E14). */
  lessonMode: LessonMode;
  /** Aulas definidas: total e quantas já foram estudadas. */
  plannedTotal: number;
  plannedDone: number;
}

export type LessonMode = 'FREE' | 'PLANNED';

/** Aula definida de uma matéria, na ordem do curso; lessonId preenchido quando já foi estudada. */
export interface PlannedLesson {
  id: string;
  title: string;
  position: number;
  lessonId: string | null;
  studiedAt: string | null;
}

export interface SubjectRequest {
  name: string;
  color?: string | null;
  sessionsPerWeek?: number;
  lessonMinutes?: number;
  tagIds?: string[];
  archived?: boolean;
  /** Lista vazia = qualquer dia; ausente mantém como está. */
  studyDays?: WeekDay[];
  /** Ausente mantém como está (na criação, FREE). */
  lessonMode?: LessonMode;
}

/** Corpo completo para PUT a partir de uma matéria existente, com alterações por cima. */
export function toSubjectRequest(subject: Subject, changes: Partial<SubjectRequest> = {}): SubjectRequest {
  return {
    name: subject.name,
    color: subject.color,
    sessionsPerWeek: subject.sessionsPerWeek,
    lessonMinutes: subject.lessonMinutes,
    tagIds: subject.tags.map((t) => t.id),
    archived: subject.archived,
    ...changes,
  };
}

export type SessionType = 'LESSON' | 'REVIEW';
export type SessionStatus = 'RUNNING' | 'PAUSED' | 'FINISHED' | 'ABANDONED';
export type ReviewGrade = 'DIFICIL' | 'OK' | 'FACIL';

/** Sessão do timer, como a API devolve (elapsedSeconds já desconta as pausas). */
export interface StudySession {
  id: string;
  subjectId: string;
  subjectName: string;
  lessonId: string | null;
  type: SessionType;
  status: SessionStatus;
  startedAt: string;
  endedAt: string | null;
  pausedSeconds: number;
  pausedAt: string | null;
  elapsedSeconds: number;
  plannedMinutes: number;
}

/** Corpo do finish: título e notas para aula; nota para revisão. */
export interface FinishSessionRequest {
  title?: string;
  notes?: string;
  grade?: ReviewGrade;
}

export interface ReviewSuggestion {
  lessonId: string;
  subjectId: string;
  subjectName: string;
  lessonTitle: string;
  dueDate: string;
  daysOverdue: number;
  reviewMinutes: number;
}

export interface LessonSuggestion {
  subjectId: string;
  subjectName: string;
  color: string | null;
  priorityOrder: number;
  suggestedMinutes: number;
  doneThisWeek: number;
  sessionsPerWeek: number;
}

/** GET /api/study/today: revisões vencidas primeiro, depois as aulas sugeridas. */
export interface StudyToday {
  date: string;
  reviews: ReviewSuggestion[];
  lessons: LessonSuggestion[];
}

export interface SubjectStats {
  subjectId: string;
  name: string;
  color: string | null;
  minutes: number;
  lessons: number;
  reviews: number;
  /** Aulas terminadas na semana corrente. */
  sessionsThisWeek: number;
  sessionsPerWeek: number;
}

export interface WeekMinutes {
  /** Segunda-feira da semana. */
  weekStart: string;
  minutes: number;
}

export interface LessonEntry {
  id: string;
  subjectId: string;
  title: string;
  notes: string | null;
  studiedAt: string;
  durationMinutes: number;
}

/** GET /api/study/stats: minutos só de sessões terminadas. */
export interface StudyStats {
  from: string;
  to: string;
  totalMinutes: number;
  reviewsDone: number;
  subjects: SubjectStats[];
  weeks: WeekMinutes[];
  lessons: LessonEntry[];
}

/** Um item da agenda de estudos (GET /api/study/calendar). */
export type StudyCalendarKind = 'DONE' | 'REVIEW' | 'LESSON';

export interface StudyCalendarItem {
  /** DONE: sessão concluída; REVIEW: revisão agendada; LESSON: aula sugerida para a meta da semana. */
  kind: StudyCalendarKind;
  subjectId: string;
  subjectName: string;
  color: string | null;
  /** Título da aula (DONE e REVIEW); null na aula sugerida. */
  title: string | null;
  minutes: number;
  sessionType: SessionType;
  /** Revisão vencida antes de hoje (aparece em hoje). */
  overdue: boolean;
  /** Aula fixada pelo usuário nesse dia (arrastada na agenda). */
  pinned: boolean;
}

export interface StudyCalendarDay {
  date: string;
  items: StudyCalendarItem[];
}
