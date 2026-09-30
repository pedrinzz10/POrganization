import { Component, computed, inject, linkedSignal, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { firstValueFrom } from 'rxjs';
import { problemMessage } from '../../../core/http/problem';
import { BrlPipe } from '../../../shared/money-input/brl.pipe';
import { MoneyInputDirective } from '../../../shared/money-input/money-input.directive';
import { Goal } from '../data/finance.model';
import { FinanceService } from '../data/finance.service';
import { todayIso } from '../data/month.util';
import { GoalFormData, GoalFormDialog } from './goal-form.dialog';

/**
 * Metas de economia: progresso, quanto falta e quanto guardar por mês até o prazo. Registrar um
 * aporte busca a meta de novo e troca só ela na lista, então a barra anda na hora.
 */
@Component({
  selector: 'app-goals-page',
  imports: [BrlPipe, MatButtonModule, MatFormFieldModule, MatIconModule, MatInputModule, MatProgressBarModule, MoneyInputDirective, ReactiveFormsModule],
  template: `
    <div class="topo">
      <button mat-flat-button type="button" (click)="editar()">
        <mat-icon aria-hidden="true">add</mat-icon>
        Nova meta
      </button>
    </div>

    @if (metas.isLoading() && !metas.hasValue()) {
      <mat-progress-bar mode="indeterminate" />
    }
    @if (erro(); as mensagem) {
      <p class="erro" role="alert">{{ mensagem }}</p>
    }

    <div class="grade">
      @for (meta of lista(); track meta.id) {
        <article class="meta" [class.meta--batida]="meta.achieved" [attr.aria-label]="meta.name">
          <header class="meta__topo">
            <h2 class="meta__nome">{{ meta.name }}</h2>
            <button mat-icon-button type="button" [attr.aria-label]="'Editar ' + meta.name" (click)="editar(meta)">
              <mat-icon aria-hidden="true">edit</mat-icon>
            </button>
          </header>
          <mat-progress-bar mode="determinate" [value]="largura(meta)" [attr.aria-label]="'Progresso de ' + meta.name" />
          <p class="meta__progresso">{{ meta.progress.replace('.', ',') }}% · {{ meta.saved | brl }} de {{ meta.targetAmount | brl }}</p>
          @if (meta.achieved) {
            <p class="meta__detalhe">Meta batida!</p>
          } @else {
            <p class="meta__detalhe">Faltam {{ meta.remaining | brl }}</p>
            @if (meta.monthlyNeeded) {
              <p class="meta__detalhe">Guardar {{ meta.monthlyNeeded | brl }} por mês até {{ data(meta.targetDate!) }}</p>
            }
          }

          @if (aportando() === meta.id) {
            <form class="aporte" [formGroup]="aporte" (ngSubmit)="aportar(meta)">
              <mat-form-field subscriptSizing="dynamic">
                <mat-label>Valor do aporte</mat-label>
                <input matInput appMoneyInput formControlName="amount" placeholder="R$ 0,00" />
              </mat-form-field>
              <mat-form-field subscriptSizing="dynamic">
                <mat-label>Data</mat-label>
                <input matInput type="date" formControlName="date" />
              </mat-form-field>
              <button mat-flat-button type="submit" [disabled]="salvando()">Guardar</button>
              <button mat-button type="button" (click)="aportando.set(null)">Cancelar</button>
            </form>
          } @else {
            <button mat-stroked-button type="button" (click)="abrirAporte(meta)">Aportar</button>
          }
        </article>
      } @empty {
        @if (metas.hasValue()) {
          <p class="vazio">Nenhuma meta ainda. Que tal uma reserva de emergência?</p>
        }
      }
    </div>
    @if (erroAporte(); as mensagem) {
      <p class="erro" role="alert">{{ mensagem }}</p>
    }
  `,
  styles: `
    .topo {
      display: flex;
      justify-content: flex-end;
      margin-bottom: 12px;
    }
    .erro {
      color: var(--mat-sys-error);
    }
    .grade {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
      gap: 12px;
    }
    .meta {
      display: flex;
      flex-direction: column;
      gap: 6px;
      padding: 16px;
      border-radius: 16px;
      background: var(--mat-sys-surface-container-low);
    }
    .meta--batida {
      outline: 2px solid #43a047;
    }
    .meta__topo {
      display: flex;
      justify-content: space-between;
      align-items: center;
    }
    .meta__nome {
      font: var(--mat-sys-title-medium);
      margin: 0;
    }
    .meta__progresso {
      margin: 0;
      font-weight: 600;
    }
    .meta__detalhe {
      margin: 0;
      font: var(--mat-sys-body-small);
      color: var(--mat-sys-on-surface-variant);
    }
    .aporte {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 8px;
    }
    .aporte mat-form-field {
      flex: 1 1 120px;
    }
    .vazio {
      padding: 24px 0;
      text-align: center;
      color: var(--mat-sys-on-surface-variant);
    }
  `,
})
export class GoalsPage {
  private readonly finance = inject(FinanceService);
  private readonly dialog = inject(MatDialog);

  protected readonly metas = rxResource({ stream: () => this.finance.listGoals() });
  /** Cópia local: um aporte troca só a meta dele, sem recarregar a lista inteira. */
  protected readonly lista = linkedSignal(() => (this.metas.hasValue() ? this.metas.value().filter((g) => !g.archived) : []));

  /** Id da meta com o formulário de aporte aberto. */
  protected readonly aportando = signal<string | null>(null);
  protected readonly salvando = signal(false);
  protected readonly erroAporte = signal<string | null>(null);

  protected readonly aporte = inject(FormBuilder).group({
    amount: [null as string | null, Validators.required],
    date: [todayIso(), Validators.required],
  });

  protected readonly erro = computed(() =>
    this.metas.error() ? problemMessage(this.metas.error(), 'Não foi possível carregar as metas.') : null,
  );

  protected largura(meta: Goal): number {
    return Math.min(100, Number(meta.progress));
  }

  /** "2027-04-30" → "30/04/2027". */
  protected data(iso: string): string {
    return `${iso.slice(8, 10)}/${iso.slice(5, 7)}/${iso.slice(0, 4)}`;
  }

  protected abrirAporte(meta: Goal): void {
    this.aporte.reset({ amount: null, date: todayIso() });
    this.erroAporte.set(null);
    this.aportando.set(meta.id);
  }

  async aportar(meta: Goal): Promise<void> {
    const v = this.aporte.getRawValue();
    if (!v.amount || !v.date || this.salvando()) {
      this.erroAporte.set(v.amount ? null : 'Informe o valor do aporte.');
      return;
    }
    this.salvando.set(true);
    this.erroAporte.set(null);
    try {
      await firstValueFrom(this.finance.contribute(meta.id, { amount: v.amount, date: v.date, note: null }));
      const atualizada = await firstValueFrom(this.finance.getGoal(meta.id));
      this.lista.update((metas) => metas.map((g) => (g.id === atualizada.id ? atualizada : g)));
      this.aportando.set(null);
    } catch (error) {
      this.erroAporte.set(problemMessage(error, 'Não foi possível registrar o aporte.'));
    } finally {
      this.salvando.set(false);
    }
  }

  protected editar(meta?: Goal): void {
    this.dialog
      .open<GoalFormDialog, GoalFormData, boolean>(GoalFormDialog, { data: { goal: meta } })
      .afterClosed()
      .subscribe((mudou) => {
        if (mudou) {
          this.metas.reload();
        }
      });
  }
}
