import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { problemMessage } from '../../core/http/problem';
import { Preferences, PreferencesService, REMINDER_OPTIONS } from '../../core/settings/preferences.service';
import { NotifyChannel } from '../commitments/data/commitment.model';

const FUSOS = [
  'America/Sao_Paulo',
  'America/Bahia',
  'America/Fortaleza',
  'America/Recife',
  'America/Belem',
  'America/Manaus',
  'America/Cuiaba',
  'America/Campo_Grande',
  'America/Porto_Velho',
  'America/Boa_Vista',
  'America/Rio_Branco',
  'America/Noronha',
  'Europe/Lisbon',
  'UTC',
];

/** Fuso, canais, lembrete padrão dos compromissos novos e horário do resumo diário. */
@Component({
  selector: 'app-preferences-form',
  imports: [ReactiveFormsModule, MatButtonModule, MatCheckboxModule, MatFormFieldModule, MatInputModule, MatSelectModule],
  template: `
    <form class="form" [formGroup]="form" (ngSubmit)="save()">
      <mat-form-field>
        <mat-label>Fuso horário</mat-label>
        <mat-select formControlName="timezone">
          @for (fuso of fusos(); track fuso) {
            <mat-option [value]="fuso">{{ fuso.replace('_', ' ') }}</mat-option>
          }
        </mat-select>
        <mat-hint>Define o "hoje" e o horário dos lembretes</mat-hint>
      </mat-form-field>

      <div class="canais" role="group" aria-label="Canais de aviso">
        <span class="rotulo">Avisar por</span>
        <mat-checkbox formControlName="push">Push no navegador</mat-checkbox>
        <mat-checkbox formControlName="email">E-mail</mat-checkbox>
      </div>

      <mat-form-field>
        <mat-label>Lembrete padrão dos compromissos novos</mat-label>
        <mat-select formControlName="defaultReminderMinutes">
          <mat-option [value]="null">Sem lembrete</mat-option>
          @for (opcao of opcoes; track opcao.minutes) {
            <mat-option [value]="opcao.minutes">{{ opcao.label }}</mat-option>
          }
        </mat-select>
      </mat-form-field>

      <mat-form-field>
        <mat-label>Resumo diário às</mat-label>
        <input matInput type="time" formControlName="digestTime" />
        <mat-hint>Compromissos, revisões e contas do dia. Vazio = sem resumo</mat-hint>
      </mat-form-field>

      @if (erro(); as mensagem) {
        <p class="erro" role="alert">{{ mensagem }}</p>
      }
      @if (salvo()) {
        <p class="ok" role="status">Preferências salvas.</p>
      }
      <div>
        <button mat-flat-button type="submit" [disabled]="salvando()">Salvar preferências</button>
      </div>
    </form>
  `,
  styles: `
    .form {
      display: flex;
      flex-direction: column;
      gap: 8px;
      max-width: 420px;
    }
    .canais {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 4px 12px;
    }
    .rotulo {
      font: var(--mat-sys-label-large);
    }
    .erro {
      color: var(--mat-sys-error);
    }
    .ok {
      color: #2e7d32;
    }
  `,
})
export class PreferencesFormComponent {
  private readonly preferences = inject(PreferencesService);

  protected readonly opcoes = REMINDER_OPTIONS;
  protected readonly fusos = signal(FUSOS);
  protected readonly salvando = signal(false);
  protected readonly salvo = signal(false);
  protected readonly erro = signal<string | null>(null);

  readonly form = inject(FormBuilder).nonNullable.group({
    timezone: ['America/Sao_Paulo'],
    push: [true],
    email: [false],
    defaultReminderMinutes: [null as number | null],
    digestTime: [''],
  });

  constructor() {
    this.preferences.load().then(
      (p) => this.preencher(p),
      (error) => this.erro.set(problemMessage(error, 'Não foi possível carregar as preferências.')),
    );
  }

  async save(): Promise<void> {
    const v = this.form.getRawValue();
    const channels: NotifyChannel[] = [...(v.push ? (['PUSH'] as const) : []), ...(v.email ? (['EMAIL'] as const) : [])];
    if (channels.length === 0) {
      this.erro.set('Escolha push, e-mail ou os dois.');
      return;
    }
    this.salvando.set(true);
    this.salvo.set(false);
    this.erro.set(null);
    try {
      this.preencher(
        await this.preferences.save({
          timezone: v.timezone,
          channels,
          defaultReminderMinutes: v.defaultReminderMinutes,
          digestTime: v.digestTime || null,
        }),
      );
      this.salvo.set(true);
    } catch (error) {
      this.erro.set(problemMessage(error, 'Não foi possível salvar.'));
    } finally {
      this.salvando.set(false);
    }
  }

  private preencher(p: Preferences): void {
    if (!FUSOS.includes(p.timezone)) {
      this.fusos.set([p.timezone, ...FUSOS]);
    }
    this.form.setValue({
      timezone: p.timezone,
      push: p.channels.includes('PUSH'),
      email: p.channels.includes('EMAIL'),
      defaultReminderMinutes: p.defaultReminderMinutes,
      digestTime: p.digestTime ?? '',
    });
  }
}
