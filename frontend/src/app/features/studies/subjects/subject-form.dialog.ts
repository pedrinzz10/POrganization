import { Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { AbstractControl, FormBuilder, FormControl, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { MatAutocompleteModule, MatAutocompleteSelectedEvent } from '@angular/material/autocomplete';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatChipsModule } from '@angular/material/chips';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { firstValueFrom, startWith } from 'rxjs';
import { problemMessage } from '../../../core/http/problem';
import { WeekDay } from '../../commitments/data/commitment.model';
import { StudiesService } from '../data/studies.service';
import { LessonMode, Subject, SubjectRequest, Tag } from '../data/study.model';

export interface SubjectFormData {
  /** Matéria a editar; sem ela, cria uma nova. */
  subject?: Subject;
}

const DIAS: { dia: WeekDay; rotulo: string; nome: string }[] = [
  { dia: 'MON', rotulo: 'S', nome: 'Segunda' },
  { dia: 'TUE', rotulo: 'T', nome: 'Terça' },
  { dia: 'WED', rotulo: 'Q', nome: 'Quarta' },
  { dia: 'THU', rotulo: 'Q', nome: 'Quinta' },
  { dia: 'FRI', rotulo: 'S', nome: 'Sexta' },
  { dia: 'SAT', rotulo: 'S', nome: 'Sábado' },
  { dia: 'SUN', rotulo: 'D', nome: 'Domingo' },
];

function atLeastOneDay(control: AbstractControl): ValidationErrors | null {
  return Array.isArray(control.value) && control.value.length > 0 ? null : { semDia: true };
}

/**
 * Criar ou editar matéria, com seletor de tags que cria tag nova no próprio campo, os dias em que
 * a matéria pode ter aula (todos marcados = qualquer dia) e o tipo: livre ou com aulas definidas.
 * A lista de aulas é cadastrada depois, pelo botão "Aulas" da matéria.
 */
@Component({
  selector: 'app-subject-form-dialog',
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatButtonToggleModule,
    MatChipsModule,
    MatAutocompleteModule,
    MatIconModule,
    MatSelectModule,
    MatSlideToggleModule,
  ],
  templateUrl: './subject-form.dialog.html',
  styles: `
    .form {
      display: flex;
      flex-direction: column;
      gap: 4px;
      min-width: min(420px, 80vw);
      padding-top: 8px;
    }
    .linha {
      display: flex;
      gap: 12px;
      flex-wrap: wrap;
    }
    .cor {
      width: 64px;
    }
    .rotulo {
      font: var(--mat-sys-label-large);
    }
    .dias {
      align-self: flex-start;
    }
    .dica {
      margin: 4px 0 8px;
      font: var(--mat-sys-body-small);
      color: var(--mat-sys-on-surface-variant);
    }
    .erro {
      color: var(--mat-sys-error);
    }
    .espaco {
      flex: 1;
    }
    .perigo {
      color: var(--mat-sys-error);
    }
    .concluida {
      margin-top: 8px;
    }
  `,
})
export class SubjectFormDialog {
  private readonly data = inject<SubjectFormData>(MAT_DIALOG_DATA);
  private readonly dialogRef = inject<MatDialogRef<SubjectFormDialog, boolean>>(MatDialogRef);
  private readonly studies = inject(StudiesService);

  protected readonly editing = this.data.subject;
  protected readonly dias = DIAS;

  readonly form = inject(FormBuilder).nonNullable.group({
    name: [this.editing?.name ?? '', [Validators.required, Validators.pattern(/\S/), Validators.maxLength(100)]],
    color: [this.editing?.color ?? '#3F51B5'],
    sessionsPerWeek: [this.editing?.sessionsPerWeek ?? 2, [Validators.required, Validators.min(0), Validators.max(21)]],
    lessonMinutes: [this.editing?.lessonMinutes ?? 50, [Validators.required, Validators.min(5), Validators.max(240)]],
    studyDays: [this.editing?.studyDays?.length ? this.editing.studyDays : DIAS.map((d) => d.dia), atLeastOneDay],
    lessonMode: [this.editing?.lessonMode ?? ('FREE' as LessonMode)],
    prerequisiteIds: [this.editing?.prerequisiteIds ?? ([] as string[])],
    completed: [this.editing?.completed ?? false],
  });

  /** Outras matérias, para o "Depende de" (E17). */
  protected readonly outras = signal<Subject[]>([]);

  private readonly valores = toSignal(this.form.valueChanges.pipe(startWith(this.form.getRawValue())), {
    requireSync: true,
  });

  /** Meta maior que os dias escolhidos: só cabe uma aula por dia. */
  protected readonly cabem = computed(() => {
    const { studyDays = [], sessionsPerWeek = 0 } = this.valores();
    return studyDays.length < 7 && sessionsPerWeek > studyDays.length ? studyDays.length : null;
  });

  protected readonly selecionadas = signal<Tag[]>(this.editing?.tags ?? []);
  protected readonly todas = signal<Tag[]>([]);
  protected readonly busca = new FormControl('', { nonNullable: true });
  private readonly termo = toSignal(this.busca.valueChanges.pipe(startWith('')), { requireSync: true });

  /** Sugestões: tags existentes ainda não escolhidas que contêm o texto digitado. */
  protected readonly sugestoes = computed(() => {
    const termo = this.termo().trim().toLowerCase();
    const escolhidas = new Set(this.selecionadas().map((t) => t.id));
    return this.todas().filter((t) => !escolhidas.has(t.id) && t.name.toLowerCase().includes(termo));
  });

  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);

  constructor() {
    firstValueFrom(this.studies.listTags()).then(
      (tags) => this.todas.set(tags),
      () => this.todas.set([]),
    );
    firstValueFrom(this.studies.listSubjects()).then(
      (materias) => this.outras.set(materias.filter((m) => m.id !== this.editing?.id)),
      () => this.outras.set([]),
    );
  }

  protected escolher(event: MatAutocompleteSelectedEvent): void {
    this.adicionar(event.option.value as Tag);
  }

  /** Enter no campo: usa a tag existente com esse nome ou cria uma nova. */
  protected async criarOuEscolher(event: Event): Promise<void> {
    event.preventDefault();
    const nome = this.busca.value.trim();
    if (!nome) {
      return;
    }
    const existente = this.todas().find((t) => t.name.toLowerCase() === nome.toLowerCase());
    if (existente) {
      this.adicionar(existente);
      return;
    }
    try {
      const nova = await firstValueFrom(this.studies.createTag(nome));
      this.todas.update((tags) => [...tags, nova]);
      this.adicionar(nova);
    } catch (error) {
      this.error.set(problemMessage(error, 'Não foi possível criar a tag.'));
    }
  }

  protected remover(tag: Tag): void {
    this.selecionadas.update((tags) => tags.filter((t) => t.id !== tag.id));
  }

  private adicionar(tag: Tag): void {
    if (!this.selecionadas().some((t) => t.id === tag.id)) {
      this.selecionadas.update((tags) => [...tags, tag]);
    }
    this.busca.setValue('');
  }

  async save(): Promise<void> {
    if (this.form.invalid || this.saving()) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    const request: SubjectRequest = {
      name: v.name.trim(),
      color: v.color,
      sessionsPerWeek: v.sessionsPerWeek,
      lessonMinutes: v.lessonMinutes,
      tagIds: this.selecionadas().map((t) => t.id),
      archived: this.editing?.archived ?? false,
      // Todos os dias = qualquer dia (lista vazia); senão, na ordem da semana
      studyDays: v.studyDays.length === 7 ? [] : DIAS.map((d) => d.dia).filter((d) => v.studyDays.includes(d)),
      lessonMode: v.lessonMode,
      prerequisiteIds: v.prerequisiteIds,
      // Só a livre é marcada à mão; a com aulas definidas termina quando todas são estudadas
      completed: v.lessonMode === 'FREE' ? v.completed : false,
    };
    await this.run(() =>
      firstValueFrom(
        this.editing ? this.studies.updateSubject(this.editing.id, request) : this.studies.createSubject(request),
      ),
    );
  }

  async archive(): Promise<void> {
    const s = this.editing!;
    await this.run(() =>
      firstValueFrom(
        this.studies.updateSubject(s.id, {
          name: s.name,
          color: s.color,
          sessionsPerWeek: s.sessionsPerWeek,
          lessonMinutes: s.lessonMinutes,
          tagIds: s.tags.map((t) => t.id),
          archived: true,
        }),
      ),
    );
  }

  private async run(action: () => Promise<unknown>): Promise<void> {
    this.saving.set(true);
    this.error.set(null);
    try {
      await action();
      this.dialogRef.close(true);
    } catch (error) {
      this.error.set(problemMessage(error));
    } finally {
      this.saving.set(false);
    }
  }
}
