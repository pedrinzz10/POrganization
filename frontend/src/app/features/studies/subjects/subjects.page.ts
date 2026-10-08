import { CdkDrag, CdkDragDrop, CdkDragHandle, CdkDropList, moveItemInArray } from '@angular/cdk/drag-drop';
import { Component, computed, inject, linkedSignal, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatChipsModule } from '@angular/material/chips';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSnackBar } from '@angular/material/snack-bar';
import { firstValueFrom } from 'rxjs';
import { problemMessage } from '../../../core/http/problem';
import { StudiesService } from '../data/studies.service';
import { Subject, toSubjectRequest } from '../data/study.model';
import { PlannedLessonsData, PlannedLessonsDialog } from './planned-lessons.dialog';
import { SubjectFormData, SubjectFormDialog } from './subject-form.dialog';

/**
 * Matérias em ordem de prioridade. Arrastar reordena na hora (atualização otimista) e volta ao
 * estado anterior se a API falhar. Chips de tag filtram a lista; sessões por semana mudam na linha.
 */
@Component({
  selector: 'app-subjects-page',
  imports: [
    CdkDropList,
    CdkDrag,
    CdkDragHandle,
    MatButtonModule,
    MatChipsModule,
    MatIconModule,
    MatProgressBarModule,
  ],
  templateUrl: './subjects.page.html',
  styleUrl: './subjects.page.scss',
})
export class SubjectsPage {
  private readonly studies = inject(StudiesService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly dialog = inject(MatDialog);

  private readonly materias = rxResource({ stream: () => this.studies.listSubjects() });
  protected readonly tags = rxResource({ stream: () => this.studies.listTags() });

  /** Cópia local da lista: muda na hora ao arrastar e é refeita quando a API devolve dados novos. */
  protected readonly lista = linkedSignal(() => (this.materias.hasValue() ? this.materias.value() : []));

  /** Nome da tag do chip selecionado (null = todas). */
  readonly filtro = signal<string | null>(null);

  protected readonly visiveis = computed(() => {
    const tag = this.filtro();
    return tag ? this.lista().filter((s) => s.tags.some((t) => t.name === tag)) : this.lista();
  });

  protected readonly carregando = computed(() => this.materias.isLoading() && !this.materias.hasValue());
  protected readonly erro = computed(() =>
    this.materias.error() ? problemMessage(this.materias.error(), 'Não foi possível carregar as matérias.') : null,
  );

  async drop(event: CdkDragDrop<Subject[]>): Promise<void> {
    if (event.previousIndex === event.currentIndex) {
      return;
    }
    const antes = this.lista();
    const depois = [...antes];
    moveItemInArray(depois, event.previousIndex, event.currentIndex);
    this.lista.set(depois);
    try {
      await firstValueFrom(this.studies.reorder(depois.map((s) => s.id)));
    } catch (error) {
      this.lista.set(antes);
      this.snackBar.open(problemMessage(error, 'Não foi possível salvar a nova ordem.'), 'OK', { duration: 5000 });
    }
  }

  protected alternarFiltro(tag: string): void {
    this.filtro.update((atual) => (atual === tag ? null : tag));
  }

  protected async mudarSessoes(materia: Subject, delta: number): Promise<void> {
    const sessoes = Math.min(21, Math.max(0, materia.sessionsPerWeek + delta));
    if (sessoes === materia.sessionsPerWeek) {
      return;
    }
    try {
      const salva = await firstValueFrom(
        this.studies.updateSubject(materia.id, toSubjectRequest(materia, { sessionsPerWeek: sessoes })),
      );
      this.lista.update((lista) => lista.map((s) => (s.id === salva.id ? salva : s)));
    } catch (error) {
      this.snackBar.open(problemMessage(error, 'Não foi possível salvar.'), 'OK', { duration: 5000 });
    }
  }

  /** Lista de aulas da matéria; ao fechar, atualiza a contagem "Aulas 2/10". */
  protected abrirAulas(materia: Subject): void {
    this.dialog
      .open<PlannedLessonsDialog, PlannedLessonsData>(PlannedLessonsDialog, {
        data: { subject: materia },
        width: 'min(560px, 95vw)',
      })
      .afterClosed()
      .subscribe(() => this.materias.reload());
  }

  protected abrir(materia?: Subject): void {
    this.dialog
      .open<SubjectFormDialog, SubjectFormData, boolean>(SubjectFormDialog, { data: { subject: materia } })
      .afterClosed()
      .subscribe((mudou) => {
        if (mudou) {
          this.materias.reload();
          this.tags.reload();
        }
      });
  }
}
