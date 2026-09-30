import { Component, inject, signal } from '@angular/core';
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
import { firstValueFrom } from 'rxjs';
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
        <div class="linha">
          <mat-form-field class="emoji">
            <mat-label>Emoji</mat-label>
            <input matInput formControlName="emoji" maxlength="16" placeholder="📚" />
          </mat-form-field>
          <mat-form-field class="titulo">
            <mat-label>Tarefa</mat-label>
            <input
              matInput
              formControlName="title"
              maxlength="100"
              placeholder="Ex.: Ler 20 min"
              required
            />
          </mat-form-field>
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
    .linha {
      display: flex;
      gap: 12px;
    }
    .emoji {
      width: 90px;
    }
    .titulo {
      flex: 1;
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

  readonly form = inject(FormBuilder).nonNullable.group({
    title: [this.editing?.title ?? '', [Validators.required, Validators.pattern(/\S/)]],
    emoji: [this.editing?.emoji ?? ''],
    weekDays: [this.editing?.weekDays ?? DIAS.map((d) => d.dia), atLeastOneDay],
    reminderTime: [this.editing?.reminderTime ?? ''],
  });

  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);

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
