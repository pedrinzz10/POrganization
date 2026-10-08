import { I18nPluralPipe } from '@angular/common';
import { Component, computed, inject, input, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Router, RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { problemMessage } from '../../../core/http/problem';
import { parseIsoDate } from '../../commitments/data/date-range.util';
import { StudiesService } from '../../studies/data/studies.service';
import { StudyToday } from '../../studies/data/study.model';

const DIA = 86_400_000;

/**
 * Seção "Estudos" da tela Hoje: revisões vencidas no topo (com os dias de atraso) e depois as
 * aulas sugeridas. O botão começa a sessão e abre o timer na seção Estudos.
 */
@Component({
  selector: 'app-today-studies',
  imports: [I18nPluralPipe, MatButtonModule, MatIconModule, RouterLink],
  template: `
    <section class="secao" aria-labelledby="hoje-estudos">
      <div class="secao__topo">
        <h2 id="hoje-estudos" class="secao__titulo">Estudos</h2>
        <a mat-button routerLink="/estudos">Ver estudos</a>
      </div>
      <ul class="lista">
        @for (r of revisoes(); track r.lessonId) {
          <li class="estudo" [class.estudo--atrasado]="r.atraso > 0">
            <div class="estudo__texto">
              <span class="estudo__titulo">{{ r.lessonTitle }}</span>
              <span class="estudo__selo">
                Revisão{{ r.atraso > 0 ? ' · ' + (r.atraso | i18nPlural: diasDeAtraso) : '' }}
              </span>
              <span class="estudo__detalhe">{{ r.subjectName }} · {{ r.reviewMinutes }} min</span>
            </div>
            <button mat-flat-button type="button" [disabled]="iniciando()" (click)="revisar(r.subjectId, r.lessonId)">Revisar</button>
          </li>
        }
        @for (l of plan().lessons; track l.subjectId) {
          <li class="estudo">
            <div class="estudo__texto">
              <span class="estudo__titulo">
                {{ l.subjectName }}{{ l.plannedLessonTitle ? ': ' + l.plannedLessonTitle : '' }}
              </span>
              <span class="estudo__selo">Aula</span>
              <span class="estudo__detalhe">{{ l.suggestedMinutes }} min · {{ l.doneThisWeek }} de {{ l.sessionsPerWeek }} nesta semana</span>
            </div>
            <button mat-stroked-button type="button" [disabled]="iniciando()" (click)="estudar(l.subjectId, l.plannedLessonId)">Estudar</button>
          </li>
        } @empty {
          @if (revisoes().length === 0) {
            <li class="vazio">Nada para estudar hoje.</li>
          }
        }
      </ul>
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
    .lista {
      list-style: none;
      margin: 0;
      padding: 0;
    }
    .estudo {
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 8px 12px;
      margin-bottom: 6px;
      border-radius: 8px;
      background: var(--mat-sys-surface-container-low);
    }
    .estudo--atrasado {
      border-left: 4px solid var(--mat-sys-error);
    }
    .estudo__texto {
      display: flex;
      flex-direction: column;
      flex: 1;
    }
    .estudo__titulo {
      font: var(--mat-sys-title-small);
    }
    .estudo__selo {
      font: var(--mat-sys-label-medium);
      color: var(--mat-sys-primary);
    }
    .estudo--atrasado .estudo__selo {
      color: var(--mat-sys-error);
    }
    .estudo__detalhe,
    .vazio {
      font: var(--mat-sys-body-small);
      color: var(--mat-sys-on-surface-variant);
    }
  `,
})
export class TodayStudiesComponent {
  private readonly studies = inject(StudiesService);
  private readonly router = inject(Router);
  private readonly snackBar = inject(MatSnackBar);

  /** Hoje segundo a API (fuso do usuário). */
  readonly today = input.required<string>();
  readonly plan = input.required<Pick<StudyToday, 'reviews' | 'lessons'>>();

  protected readonly diasDeAtraso = { '=1': '1 dia de atraso', other: '# dias de atraso' };
  protected readonly iniciando = signal(false);

  /** Dias de atraso derivados do vencimento e de hoje; recalcula quando qualquer um muda. */
  protected readonly revisoes = computed(() => {
    const hoje = parseIsoDate(this.today()).getTime();
    return this.plan().reviews.map((r) => ({
      ...r,
      atraso: Math.max(0, Math.round((hoje - parseIsoDate(r.dueDate).getTime()) / DIA)),
    }));
  });

  protected revisar(subjectId: string, lessonId: string): Promise<void> {
    return this.iniciar(() => firstValueFrom(this.studies.start(subjectId, 'REVIEW', lessonId)));
  }

  protected estudar(subjectId: string, plannedLessonId?: string | null): Promise<void> {
    return this.iniciar(() =>
      firstValueFrom(this.studies.start(subjectId, 'LESSON', undefined, plannedLessonId)),
    );
  }

  private async iniciar(start: () => Promise<unknown>): Promise<void> {
    this.iniciando.set(true);
    try {
      await start();
      await this.router.navigate(['/estudos']);
    } catch (error) {
      this.snackBar.open(problemMessage(error, 'Não foi possível iniciar.'), 'OK', { duration: 5000 });
    } finally {
      this.iniciando.set(false);
    }
  }
}
