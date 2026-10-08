import { HttpClient } from '@angular/common/http';
import { inject, Injectable, signal } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../environments/environment';
import { problemMessage } from '../../core/http/problem';
import { ConfirmData, ConfirmDialog } from '../../shared/confirm-dialog/confirm.dialog';
import { TaskTimerService } from '../tasks/data/task-timer.service';

export type SecaoId = 'commitments' | 'tasks' | 'studies' | 'finance';

export interface Secao {
  id: SecaoId;
  nome: string;
  icone: string;
  apaga: string;
}

export const SECOES: Secao[] = [
  {
    id: 'commitments',
    nome: 'Compromissos',
    icone: 'event',
    apaga:
      'todos os compromissos e lembretes. Eventos já enviados ao Google Calendar continuam lá.',
  },
  {
    id: 'tasks',
    nome: 'Tarefas',
    icone: 'checklist',
    apaga: 'todas as tarefas diárias e o histórico de feitas.',
  },
  {
    id: 'studies',
    nome: 'Estudos',
    icone: 'school',
    apaga: 'todas as matérias, aulas, sessões, revisões e tags de estudo.',
  },
  {
    id: 'finance',
    nome: 'Finanças',
    icone: 'account_balance_wallet',
    apaga:
      'contas, lançamentos, cartões e faturas, agendados, orçamentos, metas e tags. As categorias voltam para as padrão.',
  },
];

/**
 * Apagar os dados de uma seção (B14). Sem volta, então o diálogo pede para digitar APAGAR.
 * Usado pelo bloco de Configurações e pelo botão no título de cada seção.
 */
@Injectable({ providedIn: 'root' })
export class DataResetService {
  private readonly http = inject(HttpClient);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);
  private readonly timers = inject(TaskTimerService);

  readonly apagando = signal<SecaoId | null>(null);

  /** true quando apagou; false se cancelou ou deu erro (o erro já aparece no aviso). */
  async apagar(id: SecaoId): Promise<boolean> {
    const secao = SECOES.find((s) => s.id === id)!;
    const confirmou = await firstValueFrom(
      this.dialog
        .open<ConfirmDialog, ConfirmData, boolean>(ConfirmDialog, {
          data: {
            title: `Apagar ${secao.nome.toLowerCase()}?`,
            message: `Apaga ${secao.apaga}`,
            confirmLabel: 'Apagar',
            typeToConfirm: 'APAGAR',
          },
        })
        .afterClosed(),
    );
    if (!confirmou) {
      return false;
    }
    this.apagando.set(id);
    try {
      await firstValueFrom(this.http.delete(`${environment.apiUrl}/data/${id}`));
      if (id === 'tasks') {
        // Cronômetros ficam neste aparelho, fora da API
        Object.keys(this.timers.timers()).forEach((taskId) => this.timers.cancelar(taskId));
      }
      this.snackBar.open(`${secao.nome}: dados apagados.`, 'OK', { duration: 4000 });
      return true;
    } catch (error) {
      this.snackBar.open(problemMessage(error, 'Não foi possível apagar. Tente de novo.'), 'OK', {
        duration: 5000,
      });
      return false;
    } finally {
      this.apagando.set(null);
    }
  }
}
