import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { IsoDate, Occurrence } from '../../commitments/data/commitment.model';
import { Balances, BudgetStatus, ScheduledOccurrence } from '../../finance/data/finance.model';
import { StudyToday } from '../../studies/data/study.model';

/** Resposta de GET /api/today. */
export interface TodayResponse {
  /** Hoje no fuso do usuário (decidido pela API). */
  date: IsoDate;
  timezone: string;
  commitments: Occurrence[];
  /** Plano de estudo do dia: revisões vencidas primeiro, depois as aulas. */
  studies: Pick<StudyToday, 'reviews' | 'lessons'>;
  /** O que vence em breve, orçamentos em alerta e o gasto do dia. */
  finance: FinanceToday;
}

/** Fatura de cartão (STATEMENT) ou gasto pendente numa conta (BILL) que vence hoje ou nos próximos 3 dias. */
export interface DueItem {
  kind: 'STATEMENT' | 'BILL';
  id: string;
  title: string;
  dueDate: IsoDate;
  amount: string;
  cardId: string | null;
  referenceMonth: string | null;
}

export interface FinanceToday {
  dueSoon: DueItem[];
  /** Orçamentos do mês em ATENCAO ou ESTOURADO. */
  budgetAlerts: BudgetStatus[];
  spentToday: string;
  /** Agendados em conta para confirmar hoje ou atrasados. */
  toConfirm: ScheduledOccurrence[];
  /** Saldo por conta, total e previsto do fim do mês. */
  balances: Balances | null;
}

@Injectable({ providedIn: 'root' })
export class TodayService {
  private readonly http = inject(HttpClient);

  load(): Observable<TodayResponse> {
    return this.http.get<TodayResponse>(`${environment.apiUrl}/today`);
  }
}
