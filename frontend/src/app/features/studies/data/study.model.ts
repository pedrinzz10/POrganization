// Tipos dos DTOs da API de estudos (backend: com.porganization.studies.dto)

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
}

export interface SubjectRequest {
  name: string;
  color?: string | null;
  sessionsPerWeek?: number;
  lessonMinutes?: number;
  tagIds?: string[];
  archived?: boolean;
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
