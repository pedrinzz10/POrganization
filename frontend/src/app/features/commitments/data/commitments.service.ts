import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { Commitment, CommitmentRequest, IsoDate, Occurrence, OccurrencePatch } from './commitment.model';

/**
 * Acesso à API de compromissos. Cada método devolve um Observable "frio": a requisição
 * só acontece quando alguém faz subscribe (ou usa firstValueFrom).
 */
@Injectable({ providedIn: 'root' })
export class CommitmentsService {
  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiUrl}/commitments`;

  create(request: CommitmentRequest): Observable<Commitment> {
    return this.http.post<Commitment>(this.url, request);
  }

  get(id: string): Observable<Commitment> {
    return this.http.get<Commitment>(`${this.url}/${id}`);
  }

  update(id: string, request: CommitmentRequest): Observable<Commitment> {
    return this.http.put<Commitment>(`${this.url}/${id}`, request);
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${this.url}/${id}`);
  }

  /** Ocorrências de from a to (inclusive), já em ordem cronológica. */
  findInRange(from: IsoDate, to: IsoDate): Observable<Occurrence[]> {
    return this.http.get<Occurrence[]>(this.url, { params: { from, to } });
  }

  /** Ajusta um dia de um compromisso recorrente. */
  patchOccurrence(id: string, date: IsoDate, patch: OccurrencePatch): Observable<Occurrence> {
    return this.http.patch<Occurrence>(`${this.url}/${id}/occurrences/${date}`, patch);
  }

  /** Conclui ou desfaz um compromisso único. */
  setDone(id: string, done: boolean): Observable<Commitment> {
    return this.http.patch<Commitment>(`${this.url}/${id}/done`, { done });
  }
}
