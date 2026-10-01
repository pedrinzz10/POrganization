import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { map, Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import {
  FinishSessionRequest,
  SessionType,
  StudyCalendarDay,
  StudySession,
  StudyStats,
  StudyToday,
  Subject,
  SubjectRequest,
  Tag,
} from './study.model';

/** API de estudos: matérias e tags. As specs seguintes acrescentam sessões e o plano do dia. */
@Injectable({ providedIn: 'root' })
export class StudiesService {
  private readonly http = inject(HttpClient);
  private readonly api = environment.apiUrl;

  /** Matérias não arquivadas, em ordem de prioridade. */
  listSubjects(): Observable<Subject[]> {
    return this.http.get<Subject[]>(`${this.api}/subjects`);
  }

  createSubject(request: SubjectRequest): Observable<Subject> {
    return this.http.post<Subject>(`${this.api}/subjects`, request);
  }

  updateSubject(id: string, request: SubjectRequest): Observable<Subject> {
    return this.http.put<Subject>(`${this.api}/subjects/${id}`, request);
  }

  deleteSubject(id: string): Observable<void> {
    return this.http.delete<void>(`${this.api}/subjects/${id}`);
  }

  /** Grava a prioridade na ordem dos ids (a primeira vira 1). */
  reorder(ids: string[]): Observable<Subject[]> {
    return this.http.put<Subject[]>(`${this.api}/subjects/order`, { ids });
  }

  listTags(): Observable<Tag[]> {
    return this.http.get<Tag[]>(`${this.api}/tags`);
  }

  createTag(name: string): Observable<Tag> {
    return this.http.post<Tag>(`${this.api}/tags`, { name });
  }

  // ---------- plano do dia e timer ----------

  today(): Observable<StudyToday> {
    return this.http.get<StudyToday>(`${this.api}/study/today`);
  }

  /** Sessão rodando ou pausada, ou null (a API responde 204 quando não há). */
  activeSession(): Observable<StudySession | null> {
    return this.http
      .get<StudySession>(`${this.api}/study/sessions/active`, { observe: 'response' })
      .pipe(map((response) => (response.status === 204 ? null : response.body)));
  }

  start(subjectId: string, type: SessionType, lessonId?: string): Observable<StudySession> {
    return this.http.post<StudySession>(`${this.api}/study/sessions`, { subjectId, type, lessonId });
  }

  pause(id: string): Observable<StudySession> {
    return this.http.post<StudySession>(`${this.api}/study/sessions/${id}/pause`, {});
  }

  resume(id: string): Observable<StudySession> {
    return this.http.post<StudySession>(`${this.api}/study/sessions/${id}/resume`, {});
  }

  finish(id: string, request: FinishSessionRequest): Observable<StudySession> {
    return this.http.post<StudySession>(`${this.api}/study/sessions/${id}/finish`, request);
  }

  abandon(id: string): Observable<StudySession> {
    return this.http.post<StudySession>(`${this.api}/study/sessions/${id}/abandon`, {});
  }

  /** Estatísticas de from a to ("YYYY-MM-DD"). */
  stats(from: string, to: string): Observable<StudyStats> {
    return this.http.get<StudyStats>(`${this.api}/study/stats`, { params: { from, to } });
  }

  /** Move a aula da matéria de um dia para outro na mesma semana; devolve a semana atualizada. */
  moveLesson(subjectId: string, from: string, to: string): Observable<StudyCalendarDay[]> {
    return this.http.post<StudyCalendarDay[]>(`${this.api}/study/calendar/moves`, { subjectId, from, to });
  }

  /** Solta as aulas fixadas da matéria na semana do dia informado (volta ao automático). */
  unpinLessons(subjectId: string, week: string): Observable<void> {
    return this.http.delete<void>(`${this.api}/study/calendar/pins`, { params: { subjectId, week } });
  }

  /** Agenda de estudos dia a dia, de from a to (inclusivos, no máximo 62 dias). */
  calendar(from: string, to: string): Observable<StudyCalendarDay[]> {
    return this.http.get<StudyCalendarDay[]>(`${this.api}/study/calendar`, {
      params: { from, to },
    });
  }
}
