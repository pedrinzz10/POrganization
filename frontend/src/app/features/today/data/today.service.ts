import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { IsoDate, Occurrence } from '../../commitments/data/commitment.model';

/** Resposta de GET /api/today. As etapas 3 e 4 acrescentam estudos e finanças. */
export interface TodayResponse {
  /** Hoje no fuso do usuário (decidido pela API). */
  date: IsoDate;
  timezone: string;
  commitments: Occurrence[];
}

@Injectable({ providedIn: 'root' })
export class TodayService {
  private readonly http = inject(HttpClient);

  load(): Observable<TodayResponse> {
    return this.http.get<TodayResponse>(`${environment.apiUrl}/today`);
  }
}
