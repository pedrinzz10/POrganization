import { HttpClient } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBar } from '@angular/material/snack-bar';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../environments/environment';
import { problemMessage } from '../../core/http/problem';
import { ConfirmData, ConfirmDialog } from '../../shared/confirm-dialog/confirm.dialog';
import { TaskTimerService } from '../tasks/data/task-timer.service';

interface Secao {
  id: 'commitments' | 'tasks' | 'studies' | 'finance';
  nome: string;
  icone: string;
  apaga: string;
}

const SECOES: Secao[] = [
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
 * As telas buscam os dados de novo quando abertas, então não há cache para limpar (só os
 * cronômetros das tarefas, que ficam neste aparelho).
 */
@Component({
  selector: 'app-data-reset',
  imports: [MatButtonModule, MatIconModule],
  template: `
    <p class="dica">Apaga tudo o que você cadastrou numa seção. Não dá para desfazer.</p>
    <ul class="secoes">
      @for (secao of secoes; track secao.id) {
        <li class="secao">
          <mat-icon aria-hidden="true">{{ secao.icone }}</mat-icon>
          <span class="secao__nome">{{ secao.nome }}</span>
          <button
            mat-stroked-button
            type="button"
            class="apagar"
            [disabled]="apagando() !== null"
            (click)="apagar(secao)"
          >
            Apagar {{ secao.nome.toLowerCase() }}
          </button>
        </li>
      }
    </ul>
  `,
  styles: `
    .dica {
      color: var(--mat-sys-on-surface-variant);
    }
    .secoes {
      list-style: none;
      margin: 0;
      padding: 0;
    }
    .secao {
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 8px 0;
      border-top: 1px solid var(--mat-sys-outline-variant);
    }
    .secao__nome {
      flex: 1;
    }
    .apagar:not(:disabled) {
      color: var(--mat-sys-error);
      border-color: var(--mat-sys-error);
    }
  `,
})
export class DataResetComponent {
  private readonly http = inject(HttpClient);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);
  private readonly timers = inject(TaskTimerService);

  protected readonly secoes = SECOES;
  protected readonly apagando = signal<Secao['id'] | null>(null);

  protected async apagar(secao: Secao): Promise<void> {
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
      return;
    }
    this.apagando.set(secao.id);
    try {
      await firstValueFrom(this.http.delete(`${environment.apiUrl}/data/${secao.id}`));
      if (secao.id === 'tasks') {
        Object.keys(this.timers.timers()).forEach((id) => this.timers.cancelar(id));
      }
      this.snackBar.open(`${secao.nome}: dados apagados.`, 'OK', { duration: 4000 });
    } catch (error) {
      this.snackBar.open(problemMessage(error, 'Não foi possível apagar. Tente de novo.'), 'OK', {
        duration: 5000,
      });
    } finally {
      this.apagando.set(null);
    }
  }
}
