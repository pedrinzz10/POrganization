import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { DailyTask, DailyTaskRequest, DayTask, TaskStats } from './task.model';

/** API de tarefas diárias. */
@Injectable({ providedIn: 'root' })
export class TasksService {
  private readonly http = inject(HttpClient);
  private readonly api = `${environment.apiUrl}/tasks`;

  list(): Observable<DailyTask[]> {
    return this.http.get<DailyTask[]>(this.api);
  }

  /** Tarefas devidas no dia ("2026-10-07"), com feito ou não. */
  day(date: string): Observable<DayTask[]> {
    return this.http.get<DayTask[]>(`${this.api}/day`, { params: { date } });
  }

  stats(): Observable<TaskStats[]> {
    return this.http.get<TaskStats[]>(`${this.api}/stats`);
  }

  create(request: DailyTaskRequest): Observable<DailyTask> {
    return this.http.post<DailyTask>(this.api, request);
  }

  update(id: string, request: DailyTaskRequest): Observable<DailyTask> {
    return this.http.put<DailyTask>(`${this.api}/${id}`, request);
  }

  setArchived(id: string, archived: boolean): Observable<DailyTask> {
    return this.http.patch<DailyTask>(`${this.api}/${id}`, { archived });
  }

  /** Apaga a tarefa com todo o histórico. */
  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${this.api}/${id}`);
  }

  reorder(ids: string[]): Observable<DailyTask[]> {
    return this.http.put<DailyTask[]>(`${this.api}/order`, { ids });
  }

  complete(id: string, date: string): Observable<void> {
    return this.http.put<void>(`${this.api}/${id}/completions/${date}`, {});
  }

  uncomplete(id: string, date: string): Observable<void> {
    return this.http.delete<void>(`${this.api}/${id}/completions/${date}`);
  }
}
