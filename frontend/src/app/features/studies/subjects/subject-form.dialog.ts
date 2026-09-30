import { Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatAutocompleteModule, MatAutocompleteSelectedEvent } from '@angular/material/autocomplete';
import { MatButtonModule } from '@angular/material/button';
import { MatChipsModule } from '@angular/material/chips';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { firstValueFrom, startWith } from 'rxjs';
import { problemMessage } from '../../../core/http/problem';
import { StudiesService } from '../data/studies.service';
import { Subject, SubjectRequest, Tag } from '../data/study.model';

export interface SubjectFormData {
  /** Matéria a editar; sem ela, cria uma nova. */
  subject?: Subject;
}

/** Criar ou editar matéria, com seletor de tags que cria tag nova no próprio campo. */
@Component({
  selector: 'app-subject-form-dialog',
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatChipsModule,
    MatAutocompleteModule,
    MatIconModule,
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
    .erro {
      color: var(--mat-sys-error);
    }
    .espaco {
      flex: 1;
    }
    .perigo {
      color: var(--mat-sys-error);
    }
  `,
})
export class SubjectFormDialog {
  private readonly data = inject<SubjectFormData>(MAT_DIALOG_DATA);
  private readonly dialogRef = inject<MatDialogRef<SubjectFormDialog, boolean>>(MatDialogRef);
  private readonly studies = inject(StudiesService);

  protected readonly editing = this.data.subject;

  readonly form = inject(FormBuilder).nonNullable.group({
    name: [this.editing?.name ?? '', [Validators.required, Validators.pattern(/\S/), Validators.maxLength(100)]],
    color: [this.editing?.color ?? '#3F51B5'],
    sessionsPerWeek: [this.editing?.sessionsPerWeek ?? 2, [Validators.required, Validators.min(0), Validators.max(21)]],
    lessonMinutes: [this.editing?.lessonMinutes ?? 50, [Validators.required, Validators.min(5), Validators.max(240)]],
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
