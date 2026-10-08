import {
  CdkDrag,
  CdkDragDrop,
  CdkDragHandle,
  CdkDropList,
  moveItemInArray,
} from '@angular/cdk/drag-drop';
import { Component, computed, inject, linkedSignal, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { firstValueFrom } from 'rxjs';
import { problemMessage } from '../../../core/http/problem';
import { StudiesService } from '../data/studies.service';
import { PlannedLesson, Subject } from '../data/study.model';

export interface PlannedLessonsData {
  subject: Subject;
}

/**
 * Aulas definidas de uma matéria (E14), na ordem do curso. Arrastar muda a sequência (na hora,
 * voltando se a API falhar); o campo de baixo inclui várias de uma vez, uma por linha. As já
 * estudadas aparecem marcadas.
 */
@Component({
  selector: 'app-planned-lessons-dialog',
  imports: [
    CdkDrag,
    CdkDragHandle,
    CdkDropList,
    FormsModule,
    MatButtonModule,
    MatDialogModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressBarModule,
  ],
  template: `
    <h2 mat-dialog-title>Aulas de {{ data.subject.name }}</h2>
    <mat-dialog-content>
      <p class="dica">
        Na ordem do curso: a agenda sempre pega a próxima que falta. Arraste para mudar a ordem.
      </p>
      @if (aulas.isLoading() && !aulas.hasValue()) {
        <mat-progress-bar mode="indeterminate" />
      }
      @if (erro(); as mensagem) {
        <p class="erro" role="alert">{{ mensagem }}</p>
      }

      @if (lista().length > 0) {
        <p class="progresso">{{ estudadas() }} de {{ lista().length }} estudadas</p>
      }
      <ol
        class="lista"
        cdkDropList
        (cdkDropListDropped)="soltar($event)"
        aria-label="Aulas na ordem"
      >
        @for (aula of lista(); track aula.id) {
          <li class="aula" cdkDrag [class.aula--feita]="aula.lessonId">
            <mat-icon class="aula__alca" cdkDragHandle aria-hidden="true">drag_indicator</mat-icon>
            <span class="aula__posicao">{{ aula.position }}</span>
            @if (editando() === aula.id) {
              <input
                class="aula__editar"
                [attr.aria-label]="'Novo nome da aula ' + aula.position"
                [ngModel]="aula.title"
                (keydown.enter)="renomear(aula, $any($event.target).value)"
                (keydown.escape)="editando.set(null)"
                (blur)="renomear(aula, $any($event.target).value)"
              />
            } @else {
              <button type="button" class="aula__titulo" (click)="editando.set(aula.id)">
                {{ aula.title }}
              </button>
            }
            @if (aula.lessonId) {
              <mat-icon class="aula__feita" aria-label="Estudada">check_circle</mat-icon>
            }
            <button
              mat-icon-button
              type="button"
              [attr.aria-label]="'Excluir ' + aula.title"
              (click)="excluir(aula)"
            >
              <mat-icon aria-hidden="true">delete</mat-icon>
            </button>
          </li>
        } @empty {
          @if (aulas.hasValue()) {
            <li class="vazio">Nenhuma aula ainda. Cole a lista do curso abaixo.</li>
          }
        }
      </ol>

      <mat-form-field class="novas">
        <mat-label>Adicionar aulas (uma por linha)</mat-label>
        <textarea
          matInput
          rows="4"
          [(ngModel)]="novas"
          placeholder="Introdução&#10;Variáveis e tipos&#10;Laços"
        ></textarea>
      </mat-form-field>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button type="button" mat-dialog-close>Fechar</button>
      <button
        mat-flat-button
        type="button"
        [disabled]="salvando() || !novas.trim()"
        (click)="adicionar()"
      >
        Adicionar
      </button>
    </mat-dialog-actions>
  `,
  styles: `
    .dica,
    .progresso {
      margin: 0 0 8px;
      color: var(--mat-sys-on-surface-variant);
    }
    .erro {
      color: var(--mat-sys-error);
    }
    .lista {
      list-style: none;
      margin: 0 0 12px;
      padding: 0;
    }
    .aula {
      display: flex;
      align-items: center;
      gap: 8px;
      padding: 2px 4px;
      margin-bottom: 4px;
      border-radius: 8px;
      background: var(--mat-sys-surface-container-low);
    }
    .aula__alca {
      cursor: grab;
      color: var(--mat-sys-on-surface-variant);
    }
    .aula__posicao {
      min-width: 24px;
      font: var(--mat-sys-label-large);
      color: var(--mat-sys-on-surface-variant);
    }
    .aula__titulo {
      flex: 1;
      background: none;
      border: none;
      padding: 8px 0;
      color: inherit;
      font: inherit;
      text-align: left;
      cursor: text;
    }
    .aula__editar {
      flex: 1;
      font: inherit;
      padding: 6px 8px;
    }
    .aula--feita .aula__titulo {
      color: var(--mat-sys-on-surface-variant);
    }
    .aula__feita {
      color: var(--status-positive, #2e7d32);
    }
    .vazio {
      padding: 8px 0;
      color: var(--mat-sys-on-surface-variant);
    }
    .novas {
      width: 100%;
    }
  `,
})
export class PlannedLessonsDialog {
  protected readonly data = inject<PlannedLessonsData>(MAT_DIALOG_DATA);
  private readonly studies = inject(StudiesService);

  protected readonly aulas = rxResource({
    stream: () => this.studies.listPlannedLessons(this.data.subject.id),
  });
  /** Cópia local: muda na hora ao arrastar, excluir ou incluir. */
  protected readonly lista = linkedSignal(() => (this.aulas.hasValue() ? this.aulas.value() : []));
  protected readonly estudadas = computed(() => this.lista().filter((a) => a.lessonId).length);

  protected readonly editando = signal<string | null>(null);
  protected readonly salvando = signal(false);
  protected readonly falha = signal<string | null>(null);
  protected readonly erro = computed(
    () =>
      this.falha() ??
      (this.aulas.error()
        ? problemMessage(this.aulas.error(), 'Não foi possível carregar as aulas.')
        : null),
  );
  protected novas = '';

  async adicionar(): Promise<void> {
    const titulos = this.novas
      .split('\n')
      .map((t) => t.trim())
      .filter((t) => t);
    if (titulos.length === 0) {
      return;
    }
    await this.executar(async () => {
      this.lista.set(
        await firstValueFrom(this.studies.addPlannedLessons(this.data.subject.id, titulos)),
      );
      this.novas = '';
    }, 'Não foi possível adicionar as aulas.');
  }

  async soltar(event: CdkDragDrop<PlannedLesson[]>): Promise<void> {
    if (event.previousIndex === event.currentIndex) {
      return;
    }
    const antes = this.lista();
    const depois = [...antes];
    moveItemInArray(depois, event.previousIndex, event.currentIndex);
    this.lista.set(depois.map((a, i) => ({ ...a, position: i + 1 })));
    await this.executar(
      async () => {
        this.lista.set(
          await firstValueFrom(
            this.studies.reorderPlannedLessons(
              this.data.subject.id,
              depois.map((a) => a.id),
            ),
          ),
        );
      },
      'Não foi possível salvar a nova ordem.',
      () => this.lista.set(antes),
    );
  }

  async renomear(aula: PlannedLesson, titulo: string): Promise<void> {
    if (this.editando() !== aula.id) {
      return; // Enter e depois blur: só a primeira vale
    }
    this.editando.set(null);
    const novo = titulo.trim();
    if (!novo || novo === aula.title) {
      return;
    }
    await this.executar(async () => {
      const salva = await firstValueFrom(
        this.studies.renamePlannedLesson(this.data.subject.id, aula.id, novo),
      );
      this.lista.update((lista) => lista.map((a) => (a.id === salva.id ? salva : a)));
    }, 'Não foi possível renomear a aula.');
  }

  async excluir(aula: PlannedLesson): Promise<void> {
    await this.executar(async () => {
      await firstValueFrom(this.studies.deletePlannedLesson(this.data.subject.id, aula.id));
      this.lista.update((lista) =>
        lista.filter((a) => a.id !== aula.id).map((a, i) => ({ ...a, position: i + 1 })),
      );
    }, 'Não foi possível excluir a aula.');
  }

  private async executar(
    acao: () => Promise<void>,
    falha: string,
    desfazer?: () => void,
  ): Promise<void> {
    this.salvando.set(true);
    this.falha.set(null);
    try {
      await acao();
    } catch (error) {
      desfazer?.();
      this.falha.set(problemMessage(error, falha));
    } finally {
      this.salvando.set(false);
    }
  }
}
