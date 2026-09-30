import { DatePipe, NgTemplateOutlet } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { firstValueFrom } from 'rxjs';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTabsModule } from '@angular/material/tabs';
import { problemMessage } from '../../core/http/problem';
import { IsoDate, Occurrence } from './data/commitment.model';
import { CommitmentsService } from './data/commitments.service';
import {
  addDays,
  DateRange,
  monthGridRange,
  nextWeek,
  parseIsoDate,
  previousWeek,
  shiftMonth,
  today,
  weekRange,
  YearMonth,
  yearMonthOf,
} from './data/date-range.util';
import { CommitmentFormData, CommitmentFormDialog, CommitmentFormResult } from './form/commitment-form.dialog';
import { QuickAddComponent } from './quick-add/quick-add.component';
import { DayViewComponent } from './views/day-view.component';
import { MonthViewComponent } from './views/month-view.component';
import { WeekViewComponent } from './views/week-view.component';
import { YearViewComponent } from './views/year-view.component';

type Aba = 'dia' | 'semana' | 'mes' | 'ano';
const ABAS: Aba[] = ['dia', 'semana', 'mes', 'ano'];

@Component({
  selector: 'app-commitments-page',
  imports: [
    DatePipe,
    NgTemplateOutlet,
    MatTabsModule,
    MatButtonModule,
    MatIconModule,
    MatProgressBarModule,
    QuickAddComponent,
    DayViewComponent,
    WeekViewComponent,
    MonthViewComponent,
    YearViewComponent,
  ],
  templateUrl: './commitments.page.html',
  styleUrl: './commitments.page.scss',
})
export class CommitmentsPage {
  private readonly commitments = inject(CommitmentsService);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);

  protected readonly hoje = today();
  protected readonly aba = signal<Aba>('dia');
  protected readonly indiceAba = computed(() => ABAS.indexOf(this.aba()));

  protected readonly dia = signal<IsoDate>(this.hoje);
  protected readonly diaData = computed(() => parseIsoDate(this.dia()));
  protected readonly semana = signal<DateRange>(weekRange(this.hoje));
  protected readonly mes = signal<YearMonth>(yearMonthOf(this.hoje));
  protected readonly mesData = computed(() => parseIsoDate(`${this.mes().year}-${String(this.mes().month).padStart(2, '0')}-01`));
  protected readonly ano = signal<number>(yearMonthOf(this.hoje).year);

  /** Intervalo que a aba atual precisa; mudar aba, dia, semana ou mês recarrega sozinho. */
  private readonly intervalo = computed<DateRange | undefined>(() => {
    switch (this.aba()) {
      case 'dia':
        return { from: this.dia(), to: this.dia() };
      case 'semana':
        return this.semana();
      case 'mes':
        return monthGridRange(this.mes().year, this.mes().month);
      default:
        return undefined; // a visão do ano faz a própria consulta
    }
  });

  protected readonly ocorrencias = rxResource({
    params: () => this.intervalo(),
    stream: ({ params }) => this.commitments.findInRange(params.from, params.to),
  });

  protected readonly erro = computed(() =>
    this.ocorrencias.error() ? problemMessage(this.ocorrencias.error(), 'Não foi possível carregar os compromissos.') : null,
  );

  protected selecionarAba(index: number): void {
    this.aba.set(ABAS[index]);
  }

  protected mudarDia(delta: number): void {
    this.dia.update((d) => addDays(d, delta));
  }

  protected semanaAnterior(): void {
    this.semana.update(previousWeek);
  }

  protected proximaSemana(): void {
    this.semana.update(nextWeek);
  }

  protected mudarMes(delta: number): void {
    this.mes.update((m) => shiftMonth(m, delta));
  }

  protected mudarAno(delta: number): void {
    this.ano.update((a) => a + delta);
  }

  /** Volta todas as visões para hoje. */
  protected irParaHoje(): void {
    this.dia.set(this.hoje);
    this.semana.set(weekRange(this.hoje));
    this.mes.set(yearMonthOf(this.hoje));
    this.ano.set(yearMonthOf(this.hoje).year);
  }

  /** Clique num dia da grade do mês: abre a visão do dia. */
  protected abrirDia(date: IsoDate): void {
    this.dia.set(date);
    this.aba.set('dia');
  }

  /** Clique num mês da visão do ano: abre a grade daquele mês. */
  protected abrirMes(month: number): void {
    this.mes.set({ year: this.ano(), month });
    this.aba.set('mes');
  }

  protected recarregar(): void {
    this.ocorrencias.reload();
  }

  /** Formulário completo para um compromisso novo, no dia que está aberto. */
  protected novo(): void {
    this.abrirFormulario({ date: this.aba() === 'dia' ? this.dia() : this.hoje });
  }

  /** Clique numa ocorrência: busca o compromisso inteiro e abre a edição. */
  protected async editar(ocorrencia: Occurrence): Promise<void> {
    try {
      const commitment = await firstValueFrom(this.commitments.get(ocorrencia.commitmentId));
      this.abrirFormulario({ commitment, occurrenceDate: ocorrencia.recurring ? ocorrencia.occurrenceDate : undefined });
    } catch (error) {
      this.snackBar.open(problemMessage(error, 'Não foi possível abrir o compromisso.'), 'OK', { duration: 5000 });
    }
  }

  private abrirFormulario(data: CommitmentFormData): void {
    this.dialog
      .open<CommitmentFormDialog, CommitmentFormData, CommitmentFormResult>(CommitmentFormDialog, { data, autoFocus: 'first-tabbable' })
      .afterClosed()
      .subscribe((result) => {
        if (result) {
          this.snackBar.open(result === 'deleted' ? 'Compromisso excluído' : 'Compromisso salvo', 'OK', { duration: 3000 });
          this.recarregar();
        }
      });
  }
}
