import { Component, computed, effect, inject, input, linkedSignal, untracked } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSnackBar } from '@angular/material/snack-bar';
import { RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { problemMessage } from '../../../core/http/problem';
import { formatarTempo, TaskTimerService } from '../../tasks/data/task-timer.service';
import { DayTask } from '../../tasks/data/task.model';
import { TasksService } from '../../tasks/data/tasks.service';

/**
 * Seção "Tarefas do dia" da tela Hoje: checklist dos hábitos devidos hoje. Marcar é otimista:
 * a lista (um linkedSignal da resposta da API) muda na hora do clique, o progresso e a ordem
 * (feitas no fim) são computed() dela, e tudo volta se a chamada falhar. Tarefa com cronômetro
 * (T07) tem iniciar/pausar/cancelar; ao zerar, o TaskTimerService marca e a lista mostra feita.
 */
@Component({
  selector: 'app-today-tasks',
  imports: [MatButtonModule, MatCheckboxModule, MatIconModule, MatProgressBarModule, RouterLink],
  template: `
    <section class="secao" aria-labelledby="hoje-tarefas">
      <div class="secao__topo">
        <h2 id="hoje-tarefas" class="secao__titulo">Tarefas do dia</h2>
        <a mat-button routerLink="/tarefas">Ver tarefas</a>
      </div>

      @if (dados.hasValue()) {
        @if (lista().length === 0) {
          <p class="vazio">
            Nenhuma tarefa para hoje. <a routerLink="/tarefas">Crie a primeira</a>.
          </p>
        } @else {
          <div class="progresso">
            <span>{{ feitas() }}/{{ lista().length }} feitas</span>
            <mat-progress-bar
              mode="determinate"
              [value]="percentual()"
              aria-label="Progresso das tarefas do dia"
            />
          </div>
          @if (tudoFeito()) {
            <p class="parabens">Tudo feito hoje 🎉</p>
          }
          <ul class="lista">
            @for (t of ordenada(); track t.id) {
              <li class="tarefa" [class.tarefa--feita]="t.done">
                <mat-checkbox [checked]="t.done" (change)="alternar(t)">
                  @if (t.emoji) {
                    <span class="tarefa__emoji" aria-hidden="true">{{ t.emoji }}</span>
                  }
                  <span class="tarefa__titulo">{{ t.title }}</span>
                </mat-checkbox>
                @if (t.timerMinutes && !t.done) {
                  @let ms = timer.restante(t.id);
                  <span class="cronometro" [class.cronometro--ativo]="ms !== null">
                    @if (ms === null) {
                      <button
                        mat-button
                        type="button"
                        [attr.aria-label]="'Iniciar cronômetro de ' + t.title"
                        (click)="timer.iniciar(t.id, today(), t.title, t.timerMinutes)"
                      >
                        <mat-icon aria-hidden="true">timer</mat-icon>
                        {{ t.timerMinutes }} min
                      </button>
                    } @else {
                      <span
                        class="cronometro__tempo"
                        role="timer"
                        [attr.aria-label]="'Tempo restante de ' + t.title"
                        >{{ tempo(ms) }}</span
                      >
                      @if (rodando(t.id)) {
                        <button
                          mat-icon-button
                          type="button"
                          [attr.aria-label]="'Pausar ' + t.title"
                          (click)="timer.pausar(t.id)"
                        >
                          <mat-icon aria-hidden="true">pause</mat-icon>
                        </button>
                      } @else {
                        <button
                          mat-icon-button
                          type="button"
                          [attr.aria-label]="'Retomar ' + t.title"
                          (click)="timer.retomar(t.id)"
                        >
                          <mat-icon aria-hidden="true">play_arrow</mat-icon>
                        </button>
                      }
                      <button
                        mat-icon-button
                        type="button"
                        [attr.aria-label]="'Cancelar cronômetro de ' + t.title"
                        (click)="timer.cancelar(t.id)"
                      >
                        <mat-icon aria-hidden="true">stop</mat-icon>
                      </button>
                    }
                  </span>
                }
              </li>
            }
          </ul>
        }
      }
    </section>
  `,
  styles: `
    .secao__topo {
      display: flex;
      align-items: center;
      justify-content: space-between;
    }
    .secao__titulo {
      font: var(--mat-sys-title-large);
      margin: 8px 0;
    }
    .progresso {
      display: flex;
      align-items: center;
      gap: 12px;
      margin-bottom: 8px;
    }
    .progresso mat-progress-bar {
      flex: 1;
    }
    .parabens {
      margin: 0 0 8px;
      font: var(--mat-sys-title-small);
      color: #2e7d32;
    }
    .lista {
      list-style: none;
      margin: 0;
      padding: 0;
    }
    .tarefa {
      display: flex;
      align-items: center;
      flex-wrap: wrap;
      gap: 4px;
      padding: 2px 8px;
      margin-bottom: 4px;
      border-radius: 8px;
      background: var(--mat-sys-surface-container-low);
    }
    .tarefa mat-checkbox {
      flex: 1;
    }
    .cronometro {
      display: inline-flex;
      align-items: center;
    }
    .cronometro--ativo {
      padding-left: 12px;
      border-radius: var(--radius-pill, 999px);
      background: var(--mat-sys-primary-container);
    }
    .cronometro__tempo {
      font: var(--mat-sys-title-medium);
      font-variant-numeric: tabular-nums;
    }
    .tarefa__emoji {
      margin-right: 6px;
    }
    .tarefa--feita .tarefa__titulo {
      text-decoration: line-through;
      color: var(--mat-sys-on-surface-variant);
    }
    .vazio {
      font: var(--mat-sys-body-small);
      color: var(--mat-sys-on-surface-variant);
    }
  `,
})
export class TodayTasksComponent {
  private readonly tasks = inject(TasksService);
  private readonly snackBar = inject(MatSnackBar);
  protected readonly timer = inject(TaskTimerService);

  /** Hoje segundo a API (fuso do usuário). */
  readonly today = input.required<string>();

  protected readonly dados = rxResource({
    params: this.today,
    stream: ({ params }) => this.tasks.day(params),
  });

  /** Cópia local: muda na hora do clique e volta ao valor da API quando ela recarrega. */
  protected readonly lista = linkedSignal<DayTask[]>(() =>
    this.dados.hasValue() ? this.dados.value() : [],
  );

  /** Na ordem definida, com as feitas no fim. */
  protected readonly ordenada = computed(() =>
    [...this.lista()].sort((a, b) => Number(a.done) - Number(b.done) || a.position - b.position),
  );
  protected readonly feitas = computed(() => this.lista().filter((t) => t.done).length);
  protected readonly percentual = computed(() =>
    this.lista().length ? (this.feitas() * 100) / this.lista().length : 0,
  );
  protected readonly tudoFeito = computed(
    () => this.lista().length > 0 && this.feitas() === this.lista().length,
  );

  constructor() {
    // O cronômetro terminou e marcou a tarefa: mostra feita sem recarregar
    effect(() => {
      const concluidas = this.timer.concluidas();
      untracked(() => {
        if (this.lista().some((t) => concluidas.has(t.id) && !t.done)) {
          this.lista.update((lista) =>
            lista.map((t) => (concluidas.has(t.id) ? { ...t, done: true } : t)),
          );
        }
      });
    });
  }

  protected tempo(ms: number): string {
    return formatarTempo(ms);
  }

  protected rodando(taskId: string): boolean {
    return this.timer.timers()[taskId]?.endsAt != null;
  }

  async alternar(tarefa: DayTask): Promise<void> {
    const antes = this.lista();
    const feita = !tarefa.done;
    if (feita) {
      this.timer.cancelar(tarefa.id); // marcou na mão: o cronômetro não precisa mais rodar
    }
    this.lista.set(antes.map((t) => (t.id === tarefa.id ? { ...t, done: feita } : t)));
    try {
      await firstValueFrom(
        feita
          ? this.tasks.complete(tarefa.id, this.today())
          : this.tasks.uncomplete(tarefa.id, this.today()),
      );
    } catch (error) {
      this.lista.set(antes);
      this.snackBar.open(problemMessage(error, 'Não foi possível marcar a tarefa.'), 'OK', {
        duration: 5000,
      });
    }
  }
}
