import { Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatRadioModule } from '@angular/material/radio';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { firstValueFrom, startWith } from 'rxjs';
import { problemMessage } from '../../../core/http/problem';
import { Commitment, CommitmentRequest, IsoDate, OccurrencePatch } from '../data/commitment.model';
import { CommitmentsService } from '../data/commitments.service';
import { today } from '../data/date-range.util';
import { createRecurrenceGroup, fromRecurrenceRule, RecurrenceEditorComponent, toRecurrenceRule } from './recurrence-editor.component';

export interface CommitmentFormData {
  /** Compromisso a editar; sem ele, o diálogo cria um novo. */
  commitment?: Commitment;
  /** Dia da ocorrência clicada, quando se edita um recorrente a partir de uma visão. */
  occurrenceDate?: IsoDate;
  /** Dia sugerido para um compromisso novo. */
  date?: IsoDate;
}

/** Resultado ao fechar: o pai recarrega a lista se algo mudou. */
export type CommitmentFormResult = 'saved' | 'deleted' | undefined;

type Scope = 'this' | 'series';

/**
 * Formulário completo em diálogo: todos os campos, repetição e exclusão. Ao editar um dia de um
 * compromisso recorrente, pergunta se a mudança vale só para esta ocorrência ou para a série.
 */
@Component({
  selector: 'app-commitment-form-dialog',
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatCheckboxModule,
    MatSlideToggleModule,
    MatRadioModule,
    RecurrenceEditorComponent,
  ],
  templateUrl: './commitment-form.dialog.html',
  styleUrl: './commitment-form.dialog.scss',
})
export class CommitmentFormDialog {
  private readonly data = inject<CommitmentFormData>(MAT_DIALOG_DATA);
  private readonly dialogRef = inject<MatDialogRef<CommitmentFormDialog, CommitmentFormResult>>(MatDialogRef);
  private readonly commitments = inject(CommitmentsService);
  private readonly fb = inject(FormBuilder);

  protected readonly editing = this.data.commitment;
  /** Editando um dia de uma série: mostra a escolha "só esta" / "toda a série". */
  protected readonly isOccurrenceOfSeries = !!this.data.commitment?.recurrenceRule && !!this.data.occurrenceDate;

  // A partir de uma ocorrência, começa no menos destrutivo: só esta
  readonly scope = this.fb.nonNullable.control<Scope>(this.isOccurrenceOfSeries ? 'this' : 'series');

  readonly form = this.fb.nonNullable.group({
    title: ['', [Validators.required, Validators.pattern(/\S/), Validators.maxLength(200)]],
    date: [this.data.date ?? today(), Validators.required],
    allDay: [false],
    startTime: [''],
    endTime: [''],
    location: ['', Validators.maxLength(200)],
    description: ['', Validators.maxLength(2000)],
    repeat: [false],
    recurrence: createRecurrenceGroup(this.fb),
  });

  protected readonly saving = signal(false);
  protected readonly confirmingDelete = signal(false);
  protected readonly error = signal<string | null>(null);

  private readonly scopeValue = toSignal(this.scope.valueChanges.pipe(startWith(this.scope.value)), {
    requireSync: true,
  });
  /** "Só esta ocorrência": a API aceita mudar apenas título e horário daquele dia. */
  protected readonly onlyThis = computed(() => this.isOccurrenceOfSeries && this.scopeValue() === 'this');
  protected readonly repeat = toSignal(this.form.controls.repeat.valueChanges.pipe(startWith(false)), {
    requireSync: true,
  });
  protected readonly allDay = toSignal(this.form.controls.allDay.valueChanges.pipe(startWith(false)), {
    requireSync: true,
  });

  constructor() {
    const c = this.data.commitment;
    if (c) {
      this.form.patchValue({
        title: c.title,
        date: c.date,
        allDay: c.allDay,
        startTime: c.startTime ?? '',
        endTime: c.endTime ?? '',
        location: c.location ?? '',
        description: c.description ?? '',
        repeat: !!c.recurrenceRule,
      });
      if (c.recurrenceRule) {
        this.form.controls.recurrence.setValue(fromRecurrenceRule(c.recurrenceRule));
      }
    }
    // Validação condicional: o subgrupo de repetição só conta quando "repetir" está ligado
    this.form.controls.repeat.valueChanges
      .pipe(startWith(this.form.controls.repeat.value), takeUntilDestroyed())
      .subscribe((repeat) => {
        const recurrence = this.form.controls.recurrence;
        if (repeat) {
          recurrence.enable({ emitEvent: false });
        } else {
          recurrence.disable({ emitEvent: false });
        }
      });
  }

  async save(): Promise<void> {
    if (this.form.invalid || this.saving()) {
      this.form.markAllAsTouched();
      return;
    }
    await this.run(async () => {
      const c = this.data.commitment;
      if (c && this.onlyThis()) {
        const v = this.form.getRawValue();
        const patch: OccurrencePatch = { title: v.title.trim() };
        if (v.startTime) {
          patch.startTime = v.startTime;
        }
        await firstValueFrom(this.commitments.patchOccurrence(c.id, this.data.occurrenceDate!, patch));
      } else if (c) {
        await firstValueFrom(this.commitments.update(c.id, this.toRequest()));
      } else {
        await firstValueFrom(this.commitments.create(this.toRequest()));
      }
      this.dialogRef.close('saved');
    });
  }

  askDelete(): void {
    this.confirmingDelete.set(true);
  }

  async confirmDelete(): Promise<void> {
    const c = this.data.commitment!;
    await this.run(async () => {
      if (this.onlyThis()) {
        await firstValueFrom(this.commitments.patchOccurrence(c.id, this.data.occurrenceDate!, { cancelled: true }));
      } else {
        await firstValueFrom(this.commitments.delete(c.id));
      }
      this.dialogRef.close('deleted');
    });
  }

  private toRequest(): CommitmentRequest {
    const v = this.form.getRawValue();
    const timed = !v.allDay && !!v.startTime;
    return {
      title: v.title.trim(),
      date: v.date,
      allDay: !timed,
      startTime: timed ? v.startTime : null,
      endTime: timed && v.endTime ? v.endTime : null,
      location: v.location.trim() || null,
      description: v.description.trim() || null,
      recurrenceRule: v.repeat ? toRecurrenceRule(v.recurrence) : null,
    };
  }

  private async run(action: () => Promise<void>): Promise<void> {
    this.saving.set(true);
    this.error.set(null);
    try {
      await action();
    } catch (error) {
      this.error.set(problemMessage(error));
    } finally {
      this.saving.set(false);
    }
  }
}
