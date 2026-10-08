import { Component, computed, effect, inject, input, linkedSignal, output, signal, untracked } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { firstValueFrom, Observable } from 'rxjs';
import { problemMessage } from '../../../core/http/problem';
import { StudiesService } from '../data/studies.service';
import { ReviewGrade, StudySession } from '../data/study.model';
import { GradeButtonsComponent } from './grade-buttons.component';
import { beep, elapsedSeconds, formatCountdown, tickingNow } from './timer';

/**
 * Timer de uma sessão de estudo: contagem regressiva do tempo sugerido, pausar/retomar,
 * concluir. Ao zerar avisa (bipe e destaque) mas não encerra: quem decide é o usuário.
 */
@Component({
  selector: 'app-study-session',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressBarModule,
    GradeButtonsComponent,
  ],
  templateUrl: './study-session.component.html',
  styleUrl: './study-session.component.scss',
})
export class StudySessionComponent {
  private readonly studies = inject(StudiesService);

  readonly session = input.required<StudySession>();
  /** Sessão terminou (concluída ou abandonada): o pai recarrega o plano do dia. */
  readonly ended = output<StudySession>();

  /** Estado atual; muda com as respostas da API e é refeito se o pai trocar a sessão. */
  protected readonly atual = linkedSignal(() => this.session());

  private readonly now = tickingNow();
  /** Base da contagem: o tempo que a API informou e quando ele chegou. */
  private readonly base = linkedSignal(() => ({
    elapsedSeconds: this.atual().elapsedSeconds,
    running: this.atual().status === 'RUNNING',
    receivedAt: Date.now(),
  }));
  private readonly elapsed = elapsedSeconds(this.now, this.base);

  protected readonly restante = computed(() => this.atual().plannedMinutes * 60 - this.elapsed());
  protected readonly tempo = computed(() => formatCountdown(this.restante()));
  protected readonly terminouTempo = computed(() => this.restante() <= 0);
  protected readonly progresso = computed(() =>
    Math.min(100, (this.elapsed() / Math.max(1, this.atual().plannedMinutes * 60)) * 100),
  );
  protected readonly rodando = computed(() => this.atual().status === 'RUNNING');
  protected readonly revisao = computed(() => this.atual().type === 'REVIEW');

  protected readonly concluindo = signal(false);
  protected readonly ocupado = signal(false);
  protected readonly erro = signal<string | null>(null);

  protected readonly aula = inject(FormBuilder).nonNullable.group({
    title: ['', [Validators.required, Validators.pattern(/\S/), Validators.maxLength(200)]],
    notes: [''],
  });

  constructor() {
    // Aula da lista (E16): o título já vem preenchido com o nome dela, e dá para mudar
    effect(() => {
      const titulo = this.atual().plannedLessonTitle;
      if (titulo && !this.aula.controls.title.value) {
        untracked(() => this.aula.controls.title.setValue(titulo));
      }
    });
    // Avisa uma vez quando o tempo sugerido acaba (bipe); o destaque visual vem do template
    let avisou = false;
    effect(() => {
      if (this.terminouTempo() && this.rodando() && !avisou) {
        avisou = true;
        beep();
      }
    });
  }

  protected pausar(): Promise<void> {
    return this.executar(this.studies.pause(this.atual().id));
  }

  protected retomar(): Promise<void> {
    return this.executar(this.studies.resume(this.atual().id));
  }

  protected async abandonar(): Promise<void> {
    await this.executar(this.studies.abandon(this.atual().id), true);
  }

  protected async salvarAula(): Promise<void> {
    if (this.aula.invalid) {
      this.aula.markAllAsTouched();
      return;
    }
    const { title, notes } = this.aula.getRawValue();
    await this.executar(this.studies.finish(this.atual().id, { title: title.trim(), notes: notes.trim() }), true);
  }

  protected async avaliar(grade: ReviewGrade): Promise<void> {
    await this.executar(this.studies.finish(this.atual().id, { grade }), true);
  }

  private async executar(request: Observable<StudySession>, encerra = false): Promise<void> {
    this.ocupado.set(true);
    this.erro.set(null);
    try {
      const resposta = await firstValueFrom(request);
      this.atual.set(resposta);
      if (encerra) {
        this.ended.emit(resposta);
      }
    } catch (error) {
      this.erro.set(problemMessage(error));
    } finally {
      this.ocupado.set(false);
    }
  }
}
