import { Component, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import {
  AbstractControl,
  FormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { firstValueFrom, startWith } from 'rxjs';
import { problemMessage } from '../../core/http/problem';
import { WeekDay } from '../commitments/data/commitment.model';
import { DailyTask, DailyTaskRequest } from './data/task.model';
import { TasksService } from './data/tasks.service';

export interface TaskFormData {
  /** Tarefa a editar; sem ela, cria uma nova. */
  task?: DailyTask;
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

/** Emojis para escolher com um clique; o primeiro é "sem emoji". */
export const EMOJIS: { emoji: string; nome: string }[] = [
  { emoji: '', nome: 'Sem emoji' },
  { emoji: '💧', nome: 'Beber água' },
  { emoji: '📚', nome: 'Leitura' },
  { emoji: '📖', nome: 'Estudo' },
  { emoji: '✍️', nome: 'Escrita' },
  { emoji: '💻', nome: 'Programação' },
  { emoji: '🏃', nome: 'Corrida' },
  { emoji: '🚶', nome: 'Caminhada' },
  { emoji: '🏋️', nome: 'Academia' },
  { emoji: '🧘', nome: 'Meditação' },
  { emoji: '🙏', nome: 'Oração' },
  { emoji: '💊', nome: 'Remédio' },
  { emoji: '🦷', nome: 'Dentes' },
  { emoji: '🥗', nome: 'Alimentação' },
  { emoji: '🍎', nome: 'Fruta' },
  { emoji: '🛏️', nome: 'Dormir cedo' },
  { emoji: '📵', nome: 'Menos celular' },
  { emoji: '🧹', nome: 'Arrumação' },
  { emoji: '🌱', nome: 'Plantas' },
  { emoji: '🐶', nome: 'Pet' },
  { emoji: '🎸', nome: 'Música' },
  { emoji: '💰', nome: 'Economizar' },
  { emoji: '☀️', nome: 'Sol' },
];

/** Pelo menos um dia da semana. */
function atLeastOneDay(control: AbstractControl): ValidationErrors | null {
  return Array.isArray(control.value) && control.value.length > 0 ? null : { semDia: true };
}

/**
 * Criar ou editar tarefa diária. Os dias da semana são um FormControl<WeekDay[]> editado por um
 * mat-button-toggle-group múltiplo, com "todos os dias" marcado por padrão e pelo menos um dia exigido.
 * Mudar os dias vale de hoje em diante (o histórico segue a regra antiga).
 */
@Component({
  selector: 'app-task-form-dialog',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatButtonToggleModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
  ],
  template: `
    <h2 mat-dialog-title>{{ editing ? 'Editar tarefa' : 'Nova tarefa' }}</h2>
    <mat-dialog-content>
      <form class="form" [formGroup]="form" (ngSubmit)="save()" id="task-form">
        <mat-form-field>
          <mat-label>Tarefa</mat-label>
          <input
            matInput
            formControlName="title"
            maxlength="100"
            placeholder="Ex.: Ler 20 min"
            required
          />
        </mat-form-field>

        <span class="rotulo" id="emoji-rotulo">Emoji</span>
        <div class="emojis" role="radiogroup" aria-labelledby="emoji-rotulo">
          @for (e of emojis; track e.emoji) {
            <button
              type="button"
              role="radio"
              class="emojis__item"
              [class.emojis__item--escolhido]="emojiEscolhido() === e.emoji"
              [attr.aria-checked]="emojiEscolhido() === e.emoji"
              [attr.aria-label]="e.nome"
              [title]="e.nome"
              (click)="escolherEmoji(e.emoji)"
            >
              @if (e.emoji) {
                {{ e.emoji }}
              } @else {
                <span class="emojis__nenhum" aria-hidden="true">∅</span>
              }
            </button>
          }
        </div>

        <span class="rotulo" id="dias-rotulo">Dias</span>
        <mat-button-toggle-group
          formControlName="weekDays"
          multiple
          aria-labelledby="dias-rotulo"
          class="dias"
        >
          @for (d of dias; track d.dia) {
            <mat-button-toggle [value]="d.dia" [aria-label]="d.nome">{{
              d.rotulo
            }}</mat-button-toggle>
          }
        </mat-button-toggle-group>
        @if (form.controls.weekDays.hasError('semDia')) {
          <p class="erro">Escolha pelo menos um dia.</p>
        } @else if (form.controls.weekDays.value.length === 7) {
          <p class="dica">Todos os dias</p>
        }

        <mat-form-field class="lembrete">
          <mat-label>Lembrete às</mat-label>
          <input matInput type="time" formControlName="reminderTime" />
          <mat-hint>Avisa se ainda não estiver feita. Vazio = sem lembrete</mat-hint>
        </mat-form-field>
        @if (editing) {
          <p class="dica">Mudar os dias vale de hoje em diante; o histórico continua como era.</p>
        }
        @if (error(); as mensagem) {
          <p class="erro" role="alert">{{ mensagem }}</p>
        }
      </form>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button type="button" mat-dialog-close>Cancelar</button>
      <button mat-flat-button type="submit" form="task-form" [disabled]="saving()">Salvar</button>
    </mat-dialog-actions>
  `,
  styles: `
    .form {
      display: flex;
      flex-direction: column;
      gap: 4px;
      min-width: min(380px, 80vw);
      padding-top: 8px;
    }
    .emojis {
      display: grid;
      grid-template-columns: repeat(auto-fill, 40px);
      gap: 4px;
      margin-bottom: 12px;
    }
    .emojis__item {
      width: 40px;
      height: 40px;
      font-size: 20px;
      line-height: 1;
      border: 1px solid transparent;
      border-radius: 8px;
      background: var(--mat-sys-surface-container);
      cursor: pointer;
    }
    .emojis__item:hover {
      background: var(--mat-sys-surface-container-high);
    }
    .emojis__item--escolhido {
      border-color: var(--mat-sys-primary);
      background: var(--mat-sys-primary-container);
    }
    .emojis__nenhum {
      font-size: 16px;
      color: var(--mat-sys-on-surface-variant);
    }
    .rotulo {
      font: var(--mat-sys-label-large);
    }
    .dias {
      align-self: flex-start;
    }
    .lembrete {
      margin-top: 12px;
    }
    .dica {
      margin: 4px 0 0;
      font: var(--mat-sys-body-small);
      color: var(--mat-sys-on-surface-variant);
    }
    .erro {
      margin: 4px 0 0;
      color: var(--mat-sys-error);
    }
  `,
})
export class TaskFormDialog {
  private readonly data = inject<TaskFormData>(MAT_DIALOG_DATA);
  private readonly dialogRef = inject<MatDialogRef<TaskFormDialog, boolean>>(MatDialogRef);
  private readonly tasks = inject(TasksService);

  protected readonly editing = this.data.task;
  protected readonly dias = DIAS;
  /** Os pré-definidos; o emoji de uma tarefa antiga que não está na lista entra logo depois do "sem emoji". */
  protected readonly emojis =
    this.editing?.emoji && !EMOJIS.some((e) => e.emoji === this.editing!.emoji)
      ? [EMOJIS[0], { emoji: this.editing.emoji, nome: 'Emoji atual' }, ...EMOJIS.slice(1)]
      : EMOJIS;

  readonly form = inject(FormBuilder).nonNullable.group({
    title: [this.editing?.title ?? '', [Validators.required, Validators.pattern(/\S/)]],
    emoji: [this.editing?.emoji ?? ''],
    weekDays: [this.editing?.weekDays ?? DIAS.map((d) => d.dia), atLeastOneDay],
    reminderTime: [this.editing?.reminderTime ?? ''],
  });

  protected readonly emojiEscolhido = toSignal(
    this.form.controls.emoji.valueChanges.pipe(startWith(this.form.controls.emoji.value)),
    { requireSync: true },
  );

  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);

  protected escolherEmoji(emoji: string): void {
    this.form.controls.emoji.setValue(emoji);
  }

  async save(): Promise<void> {
    if (this.form.invalid || this.saving()) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    // Na ordem da semana, independente da ordem dos cliques
    const request: DailyTaskRequest = {
      title: v.title.trim(),
      emoji: v.emoji.trim() || null,
      weekDays: DIAS.map((d) => d.dia).filter((d) => v.weekDays.includes(d)),
      reminderTime: v.reminderTime || null,
    };
    this.saving.set(true);
    this.error.set(null);
    try {
      await firstValueFrom(
        this.editing ? this.tasks.update(this.editing.id, request) : this.tasks.create(request),
      );
      this.dialogRef.close(true);
    } catch (error) {
      this.error.set(problemMessage(error));
    } finally {
      this.saving.set(false);
    }
  }
}
