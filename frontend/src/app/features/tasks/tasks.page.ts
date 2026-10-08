import {
  CdkDrag,
  CdkDragDrop,
  CdkDragHandle,
  CdkDropList,
  moveItemInArray,
} from '@angular/cdk/drag-drop';
import { Component, computed, inject, linkedSignal, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSnackBar } from '@angular/material/snack-bar';
import { firstValueFrom } from 'rxjs';
import { problemMessage } from '../../core/http/problem';
import { ConfirmData, ConfirmDialog } from '../../shared/confirm-dialog/confirm.dialog';
import { WeekDay } from '../commitments/data/commitment.model';
import { DailyTask, TaskStats } from './data/task.model';
import { TasksService } from './data/tasks.service';
import { TaskFormData, TaskFormDialog } from './task-form.dialog';
import { SectionTitleComponent } from '../settings/section-title.component';

const NOMES: Record<WeekDay, string> = {
  MON: 'seg',
  TUE: 'ter',
  WED: 'qua',
  THU: 'qui',
  FRI: 'sex',
  SAT: 'sáb',
  SUN: 'dom',
};
const ORDEM: WeekDay[] = ['MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT', 'SUN'];

/**
 * Tela Tarefas: hábitos ativos em ordem (arrastar reordena, otimista, e volta se a API falhar),
 * com a sequência 🔥 e a % dos últimos 30 dias de cada um; arquivadas numa seção recolhida.
 */
@Component({
  selector: 'app-tasks-page',
  imports: [
    SectionTitleComponent,
    CdkDropList,
    CdkDrag,
    CdkDragHandle,
    MatButtonModule,
    MatIconModule,
    MatProgressBarModule,
  ],
  template: `
    <app-section-title secao="tasks">Tarefas</app-section-title>
    <div class="topo">
      <p class="dica">Hábitos do dia a dia. Marque na tela Hoje; aqui você organiza e acompanha.</p>
      <button mat-flat-button type="button" (click)="editar()">
        <mat-icon aria-hidden="true">add</mat-icon>
        Nova tarefa
      </button>
    </div>

    @if (tarefas.isLoading() && !tarefas.hasValue()) {
      <mat-progress-bar mode="indeterminate" />
    }
    @if (erro(); as mensagem) {
      <p class="erro" role="alert">{{ mensagem }}</p>
    }

    <ol class="lista" cdkDropList (cdkDropListDropped)="soltar($event)" aria-label="Tarefas ativas">
      @for (t of ativas(); track t.id) {
        <li class="tarefa" cdkDrag>
          <mat-icon class="tarefa__alca" cdkDragHandle aria-hidden="true">drag_indicator</mat-icon>
          <button type="button" class="tarefa__nome" (click)="editar(t)">
            @if (t.emoji) {
              <span aria-hidden="true">{{ t.emoji }}</span>
            }
            {{ t.title }}
          </button>
          <span class="tarefa__dias">
            {{ dias(t) }}
            @if (t.reminderTime) {
              · <mat-icon class="tarefa__sino" aria-hidden="true">notifications</mat-icon
              >{{ t.reminderTime }}
            }
            @if (t.timerMinutes) {
              · <mat-icon class="tarefa__sino" aria-hidden="true">timer</mat-icon
              >{{ t.timerMinutes }} min
            }
          </span>
          <span class="tarefa__sequencia" [attr.aria-label]="'Sequência de ' + t.title"
            >🔥 {{ estatistica(t)?.streak ?? 0 }}</span
          >
          <span class="tarefa__taxa">{{ taxa(t) }}</span>
          <button
            mat-icon-button
            type="button"
            [attr.aria-label]="'Arquivar ' + t.title"
            (click)="arquivar(t, true)"
          >
            <mat-icon aria-hidden="true">archive</mat-icon>
          </button>
          <button
            mat-icon-button
            type="button"
            [attr.aria-label]="'Excluir ' + t.title"
            (click)="excluir(t)"
          >
            <mat-icon aria-hidden="true">delete</mat-icon>
          </button>
        </li>
      } @empty {
        @if (tarefas.hasValue()) {
          <li class="vazio">Nenhuma tarefa ainda. Que tal "Beber água 💧"?</li>
        }
      }
    </ol>

    @if (arquivadas().length > 0) {
      <details class="arquivadas">
        <summary>Arquivadas ({{ arquivadas().length }})</summary>
        <ul class="lista">
          @for (t of arquivadas(); track t.id) {
            <li class="tarefa tarefa--arquivada">
              <span class="tarefa__nome">{{ t.emoji }} {{ t.title }}</span>
              <button mat-button type="button" (click)="arquivar(t, false)">Desarquivar</button>
              <button
                mat-icon-button
                type="button"
                [attr.aria-label]="'Excluir ' + t.title"
                (click)="excluir(t)"
              >
                <mat-icon aria-hidden="true">delete</mat-icon>
              </button>
            </li>
          }
        </ul>
      </details>
    }
  `,
  styles: `
    .topo {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 8px;
      flex-wrap: wrap;
      margin-bottom: 12px;
    }
    .dica {
      margin: 0;
      color: var(--mat-sys-on-surface-variant);
    }
    .erro {
      color: var(--mat-sys-error);
    }
    .lista {
      list-style: none;
      padding: 0;
      margin: 0;
    }
    .tarefa {
      display: flex;
      align-items: center;
      flex-wrap: wrap;
      gap: 8px;
      padding: 6px 8px;
      margin-bottom: 4px;
      border-radius: 8px;
      background: var(--mat-sys-surface-container-low);
    }
    .tarefa--arquivada {
      opacity: 0.7;
    }
    .tarefa__alca {
      cursor: grab;
      color: var(--mat-sys-on-surface-variant);
    }
    .tarefa__nome {
      flex: 1;
      text-align: left;
      font: var(--mat-sys-title-small);
      background: none;
      border: none;
      padding: 0;
      color: inherit;
      cursor: pointer;
    }
    .tarefa__dias,
    .tarefa__taxa {
      font: var(--mat-sys-body-small);
      color: var(--mat-sys-on-surface-variant);
    }
    .tarefa__sino {
      font-size: 14px;
      width: 14px;
      height: 14px;
      vertical-align: middle;
    }
    .tarefa__sequencia {
      font-weight: 600;
    }
    .arquivadas {
      margin-top: 16px;
    }
    .vazio {
      padding: 24px 0;
      text-align: center;
      color: var(--mat-sys-on-surface-variant);
    }
  `,
})
export class TasksPage {
  private readonly tasks = inject(TasksService);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);

  protected readonly tarefas = rxResource({ stream: () => this.tasks.list() });
  private readonly stats = rxResource({ stream: () => this.tasks.stats() });

  /** Cópia local das ativas: muda na hora ao arrastar e volta se a API falhar. */
  protected readonly ativas = linkedSignal(() =>
    (this.tarefas.value() ?? []).filter((t) => !t.archived),
  );
  protected readonly arquivadas = computed(() =>
    (this.tarefas.value() ?? []).filter((t) => t.archived),
  );
  private readonly porTarefa = computed(
    () => new Map((this.stats.value() ?? []).map((s) => [s.taskId, s])),
  );

  protected readonly erro = computed(() =>
    this.tarefas.error()
      ? problemMessage(this.tarefas.error(), 'Não foi possível carregar as tarefas.')
      : null,
  );

  protected estatistica(t: DailyTask): TaskStats | undefined {
    return this.porTarefa().get(t.id);
  }

  protected taxa(t: DailyTask): string {
    const rate = this.estatistica(t)?.completionRate;
    return rate === null || rate === undefined
      ? 'sem histórico'
      : `${rate.replace('.', ',')}% em 30 dias`;
  }

  protected dias(t: DailyTask): string {
    return t.weekDays.length === 7
      ? 'todo dia'
      : ORDEM.filter((d) => t.weekDays.includes(d))
          .map((d) => NOMES[d])
          .join(', ');
  }

  async soltar(event: CdkDragDrop<DailyTask[]>): Promise<void> {
    if (event.previousIndex === event.currentIndex) {
      return;
    }
    const antes = this.ativas();
    const depois = [...antes];
    moveItemInArray(depois, event.previousIndex, event.currentIndex);
    this.ativas.set(depois);
    try {
      await firstValueFrom(this.tasks.reorder(depois.map((t) => t.id)));
    } catch (error) {
      this.ativas.set(antes);
      this.snackBar.open(problemMessage(error, 'Não foi possível salvar a nova ordem.'), 'OK', {
        duration: 5000,
      });
    }
  }

  protected editar(task?: DailyTask): void {
    this.dialog
      .open<TaskFormDialog, TaskFormData, boolean>(TaskFormDialog, { data: { task } })
      .afterClosed()
      .subscribe((mudou) => {
        if (mudou) {
          this.recarregar();
        }
      });
  }

  async arquivar(t: DailyTask, archived: boolean): Promise<void> {
    await this.executar(
      () => firstValueFrom(this.tasks.setArchived(t.id, archived)),
      'Não foi possível arquivar.',
    );
  }

  async excluir(t: DailyTask): Promise<void> {
    const confirmou = await firstValueFrom(
      this.dialog
        .open<ConfirmDialog, ConfirmData, boolean>(ConfirmDialog, {
          data: {
            title: `Excluir "${t.title}"?`,
            message: 'O histórico da tarefa também é apagado. Para só parar de ver, use arquivar.',
            confirmLabel: 'Excluir',
          },
        })
        .afterClosed(),
    );
    if (confirmou) {
      await this.executar(
        () => firstValueFrom(this.tasks.delete(t.id)),
        'Não foi possível excluir.',
      );
    }
  }

  private async executar(acao: () => Promise<unknown>, falha: string): Promise<void> {
    try {
      await acao();
      this.recarregar();
    } catch (error) {
      this.snackBar.open(problemMessage(error, falha), 'OK', { duration: 5000 });
    }
  }

  private recarregar(): void {
    this.tarefas.reload();
    this.stats.reload();
  }
}
