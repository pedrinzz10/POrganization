import { Component, input } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import {
  AbstractControl,
  FormBuilder,
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatRadioModule } from '@angular/material/radio';
import { MatSelectModule } from '@angular/material/select';
import { map, startWith, switchMap } from 'rxjs';
import { Frequency, IsoDate, RecurrenceRule, WeekDay } from '../data/commitment.model';

export type EndType = 'never' | 'until' | 'count';

export type RecurrenceGroup = FormGroup<{
  freq: FormControl<Frequency>;
  interval: FormControl<number>;
  byWeekDays: FormControl<WeekDay[]>;
  endType: FormControl<EndType>;
  until: FormControl<IsoDate>;
  count: FormControl<number>;
}>;

export type RecurrenceValue = ReturnType<RecurrenceGroup['getRawValue']>;

export const WEEK_DAYS: { value: WeekDay; label: string }[] = [
  { value: 'MON', label: 'Seg' },
  { value: 'TUE', label: 'Ter' },
  { value: 'WED', label: 'Qua' },
  { value: 'THU', label: 'Qui' },
  { value: 'FRI', label: 'Sex' },
  { value: 'SAT', label: 'Sáb' },
  { value: 'SUN', label: 'Dom' },
];

/**
 * Regras que dependem de outros campos: semanal exige dias; fim por data exige a data; fim por
 * quantidade exige de 1 a 1000 (os mesmos limites da API).
 */
function recurrenceValidator(control: AbstractControl): ValidationErrors | null {
  const v = (control as RecurrenceGroup).getRawValue();
  const errors: ValidationErrors = {};
  if (v.freq === 'WEEKLY' && v.byWeekDays.length === 0) {
    errors['semDias'] = true;
  }
  if (v.endType === 'until' && !v.until) {
    errors['semDataFinal'] = true;
  }
  if (v.endType === 'count' && !(v.count >= 1 && v.count <= 1000)) {
    errors['quantidadeInvalida'] = true;
  }
  return Object.keys(errors).length ? errors : null;
}

export function createRecurrenceGroup(fb: FormBuilder): RecurrenceGroup {
  return fb.nonNullable.group(
    {
      freq: fb.nonNullable.control<Frequency>('WEEKLY'),
      interval: fb.nonNullable.control(1, [Validators.required, Validators.min(1), Validators.max(99)]),
      byWeekDays: fb.nonNullable.control<WeekDay[]>([]),
      endType: fb.nonNullable.control<EndType>('never'),
      until: fb.nonNullable.control<IsoDate>(''),
      count: fb.nonNullable.control(10),
    },
    { validators: recurrenceValidator },
  );
}

/** Valor do formulário → RecurrenceRule da API (só os campos que se aplicam). */
export function toRecurrenceRule(v: RecurrenceValue): RecurrenceRule {
  const rule: RecurrenceRule = { freq: v.freq, interval: v.interval };
  if (v.freq === 'WEEKLY') {
    const order = WEEK_DAYS.map((d) => d.value);
    rule.byWeekDays = [...v.byWeekDays].sort((a, b) => order.indexOf(a) - order.indexOf(b));
  }
  if (v.endType === 'until') {
    rule.until = v.until;
  } else if (v.endType === 'count') {
    rule.count = v.count;
  }
  return rule;
}

/** RecurrenceRule da API → valor completo do formulário. */
export function fromRecurrenceRule(rule: RecurrenceRule): RecurrenceValue {
  return {
    freq: rule.freq,
    interval: rule.interval ?? 1,
    byWeekDays: rule.byWeekDays ?? [],
    endType: rule.until ? 'until' : rule.count ? 'count' : 'never',
    until: rule.until ?? '',
    count: rule.count ?? 10,
  };
}

/** Campos da regra de repetição, ligados a um FormGroup que o pai cria e controla. */
@Component({
  selector: 'app-recurrence-editor',
  imports: [ReactiveFormsModule, MatFormFieldModule, MatInputModule, MatSelectModule, MatButtonToggleModule, MatRadioModule],
  templateUrl: './recurrence-editor.component.html',
  styleUrl: './recurrence-editor.component.scss',
})
export class RecurrenceEditorComponent {
  readonly group = input.required<RecurrenceGroup>();

  protected readonly weekDays = WEEK_DAYS;

  /** Valor atual do grupo como signal, para o template reagir à frequência e ao tipo de fim. */
  protected readonly value = toSignal(
    toObservable(this.group).pipe(
      switchMap((group) => group.valueChanges.pipe(startWith(null), map(() => group.getRawValue()))),
    ),
  );
}
