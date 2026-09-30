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
