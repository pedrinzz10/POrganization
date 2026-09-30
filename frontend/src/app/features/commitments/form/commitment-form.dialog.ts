import { Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { AbstractControl, FormArray, FormBuilder, FormControl, FormGroup, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatRadioModule } from '@angular/material/radio';
import { MatSelectModule } from '@angular/material/select';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { firstValueFrom, startWith } from 'rxjs';
import { problemMessage } from '../../../core/http/problem';
import { PreferencesService, REMINDER_OPTIONS } from '../../../core/settings/preferences.service';
import { Commitment, CommitmentRequest, IsoDate, NotifyChannel, OccurrencePatch, ReminderSpec } from '../data/commitment.model';
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

type ReminderGroup = FormGroup<{
  minutesBefore: FormControl<number>;
  push: FormControl<boolean>;
  email: FormControl<boolean>;
}>;

/** Pelo menos um canal marcado em cada lembrete. */
function atLeastOneChannel(group: AbstractControl): ValidationErrors | null {
  const { push, email } = group.value as { push: boolean; email: boolean };
  return push || email ? null : { semCanal: true };
}

/** Máximo de lembretes por compromisso (a API aceita até 5). */
const MAX_REMINDERS = 5;

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
    MatSelectModule,
    MatIconModule,
    RecurrenceEditorComponent,
  ],
  templateUrl: './commitment-form.dialog.html',
  styleUrl: './commitment-form.dialog.scss',
})
export class CommitmentFormDialog {
  private readonly data = inject<CommitmentFormData>(MAT_DIALOG_DATA);
  private readonly dialogRef = inject<MatDialogRef<CommitmentFormDialog, CommitmentFormResult>>(MatDialogRef);
  private readonly commitments = inject(CommitmentsService);
  private readonly preferences = inject(PreferencesService);
  private readonly fb = inject(FormBuilder);

  protected readonly reminderOptions = REMINDER_OPTIONS;
  protected readonly maxReminders = MAX_REMINDERS;

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
    /** Lembretes: editando, os do compromisso; criando, o padrão das Configurações (signal do PreferencesService). */
    reminders: this.fb.array<ReminderGroup>([]),
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
      this.setReminders(c.reminders ?? []);
    } else {
      // Compromisso novo começa com o lembrete padrão, se o usuário ainda não mexeu nos lembretes
      this.preferences.load().then(
        (p) => {
          if (!this.form.controls.reminders.dirty && p.defaultReminderMinutes !== null) {
            this.setReminders([{ minutesBefore: p.defaultReminderMinutes, channels: p.channels }]);
          }
        },
        () => undefined,
      );
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

  get reminders(): FormArray<ReminderGroup> {
    return this.form.controls.reminders;
  }

  addReminder(): void {
    const last = this.reminders.at(this.reminders.length - 1)?.getRawValue();
    this.reminders.push(this.reminderGroup({ minutesBefore: 30, channels: last ? channelsOf(last) : ['PUSH'] }));
    this.reminders.markAsDirty();
  }

  removeReminder(index: number): void {
    this.reminders.removeAt(index);
    this.reminders.markAsDirty();
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
      // Criando sem mexer nos lembretes, o campo vai vazio e a API aplica o padrão (mesmo que o mostrado)
      ...(this.data.commitment || this.reminders.dirty
        ? { reminders: v.reminders.map((r) => ({ minutesBefore: r.minutesBefore, channels: channelsOf(r) })) }
        : {}),
    };
  }

  private setReminders(reminders: ReminderSpec[]): void {
    this.reminders.clear({ emitEvent: false });
    reminders.forEach((r) => this.reminders.push(this.reminderGroup(r), { emitEvent: false }));
    this.reminders.updateValueAndValidity();
  }

  private reminderGroup(r: ReminderSpec): ReminderGroup {
    return this.fb.nonNullable.group(
      {
        minutesBefore: [r.minutesBefore],
        push: [r.channels.includes('PUSH')],
        email: [r.channels.includes('EMAIL')],
      },
      { validators: atLeastOneChannel },
    );
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

function channelsOf(r: { push: boolean; email: boolean }): NotifyChannel[] {
  return [...(r.push ? (['PUSH'] as const) : []), ...(r.email ? (['EMAIL'] as const) : [])];
}
