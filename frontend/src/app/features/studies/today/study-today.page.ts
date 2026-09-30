import { Component, computed, inject, linkedSignal, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSnackBar } from '@angular/material/snack-bar';
import { firstValueFrom } from 'rxjs';
import { problemMessage } from '../../../core/http/problem';
import { StudiesService } from '../data/studies.service';
import { LessonSuggestion, ReviewSuggestion, StudySession } from '../data/study.model';
import { StudySessionComponent } from '../session/study-session.component';

/**
 * Estudo do dia: se há uma sessão em andamento (mesmo depois de recarregar), mostra o timer;
 * senão, o plano do dia com revisões vencidas primeiro e as aulas pela prioridade.
 */
@Component({
  selector: 'app-study-today-page',
  imports: [MatButtonModule, MatIconModule, MatProgressBarModule, StudySessionComponent],
  templateUrl: './study-today.page.html',
  styleUrl: './study-today.page.scss',
})
export class StudyTodayPage {
  private readonly studies = inject(StudiesService);
  private readonly snackBar = inject(MatSnackBar);

  private readonly ativa = rxResource({ stream: () => this.studies.activeSession() });
  protected readonly plano = rxResource({ stream: () => this.studies.today() });

  /** Sessão mostrada no timer: a ativa da API ou a que acabou de ser iniciada nesta tela. */
  protected readonly sessao = linkedSignal<StudySession | null>(() => (this.ativa.hasValue() ? this.ativa.value() : null));

  protected readonly iniciando = signal(false);
  protected readonly carregando = computed(() => this.plano.isLoading() && !this.plano.hasValue());
  protected readonly erro = computed(() =>
    this.plano.error() ? problemMessage(this.plano.error(), 'Não foi possível carregar o plano do dia.') : null,
  );
  protected readonly vazio = computed(
    () => this.plano.hasValue() && this.plano.value().reviews.length === 0 && this.plano.value().lessons.length === 0,
  );

  protected revisar(review: ReviewSuggestion): Promise<void> {
    return this.iniciar(review.subjectId, 'REVIEW', review.lessonId);
  }

  protected estudar(lesson: LessonSuggestion): Promise<void> {
    return this.iniciar(lesson.subjectId, 'LESSON');
  }

  protected encerrou(sessao: StudySession): void {
    this.sessao.set(null);
    this.snackBar.open(sessao.status === 'FINISHED' ? 'Sessão registrada!' : 'Sessão abandonada', 'OK', { duration: 3000 });
    this.plano.reload();
  }

  private async iniciar(subjectId: string, type: 'LESSON' | 'REVIEW', lessonId?: string): Promise<void> {
    this.iniciando.set(true);
    try {
      this.sessao.set(await firstValueFrom(this.studies.start(subjectId, type, lessonId)));
    } catch (error) {
      this.snackBar.open(problemMessage(error, 'Não foi possível iniciar.'), 'OK', { duration: 5000 });
    } finally {
      this.iniciando.set(false);
    }
  }
}
