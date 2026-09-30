import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { Subject, SubjectRequest, Tag } from './study.model';

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
}
