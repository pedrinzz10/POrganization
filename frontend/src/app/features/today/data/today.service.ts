import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { IsoDate, Occurrence } from '../../commitments/data/commitment.model';
import { StudyToday } from '../../studies/data/study.model';

/** Resposta de GET /api/today. A etapa 4 acrescenta finanças. */
export interface TodayResponse {
  /** Hoje no fuso do usuário (decidido pela API). */
  date: IsoDate;
  timezone: string;
  commitments: Occurrence[];
  /** Plano de estudo do dia: revisões vencidas primeiro, depois as aulas. */
  studies: Pick<StudyToday, 'reviews' | 'lessons'>;
}

@Injectable({ providedIn: 'root' })
export class TodayService {
  private readonly http = inject(HttpClient);

  load(): Observable<TodayResponse> {
    return this.http.get<TodayResponse>(`${environment.apiUrl}/today`);
  }
}
