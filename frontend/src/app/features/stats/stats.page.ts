import { DatePipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { RouterLink } from '@angular/router';
import { addDays, parseIsoDate, today } from '../commitments/data/date-range.util';
import { FinanceService } from '../finance/data/finance.service';
import { formatMinutes } from '../studies/history/study-history.page';
import { StudiesService } from '../studies/data/studies.service';
import { DayCount } from '../tasks/data/task.model';
import { TasksService } from '../tasks/data/tasks.service';
import { BrlPipe } from '../../shared/money-input/brl.pipe';

const PERIODOS = [7, 30, 90] as const;
type Periodo = (typeof PERIODOS)[number];

/** Uma casa do mapa de calor: vazia (alinhamento ou nada devido) ou com o nível de 0 a 3. */
interface Casa {
  date: string | null;
  nivel: number | null;
  rotulo: string;
}

/**
 * Tela Estatísticas (S01): junta o que já existe espalhado (tarefas, estudos e finanças) num
 * período escolhido (7, 30 ou 90 dias). Cada bloco tem o seu rxResource, então um que falhe não
 * derruba os outros.
 */
@Component({
  selector: 'app-stats-page',
  imports: [BrlPipe, DatePipe, MatButtonModule, MatProgressBarModule, RouterLink],
  templateUrl: './stats.page.html',
  styleUrl: './stats.page.scss',
})
export class StatsPage {
  private readonly tasks = inject(TasksService);
  private readonly studies = inject(StudiesService);
  private readonly finance = inject(FinanceService);

  protected readonly periodos = PERIODOS;
  protected readonly periodo = signal<Periodo>(30);
  private readonly hoje = today();
  protected readonly intervalo = computed(() => ({
    from: addDays(this.hoje, 1 - this.periodo()),
    to: this.hoje,
  }));
  protected readonly mes = this.hoje.slice(0, 7);

  protected readonly historico = rxResource({
    params: this.intervalo,
    stream: ({ params }) => this.tasks.history(params.from, params.to),
  });
  private readonly tarefas = rxResource({ stream: () => this.tasks.list() });
  private readonly sequencias = rxResource({ stream: () => this.tasks.stats() });
  protected readonly estudo = rxResource({
    params: this.intervalo,
    stream: ({ params }) => this.studies.stats(params.from, params.to),
  });
  protected readonly dinheiro = rxResource({ stream: () => this.finance.summary(this.mes) });

  protected readonly formatar = formatMinutes;
  protected readonly parse = parseIsoDate;

  protected readonly carregando = computed(
    () => this.historico.isLoading() || this.estudo.isLoading() || this.tarefas.isLoading(),
  );

  /** % das tarefas devidas no período que foram feitas; null sem nenhuma devida. */
  protected readonly taxaTarefas = computed(() => {
    const dias = this.historico.hasValue() ? this.historico.value() : [];
    const devidas = dias.reduce((s, d) => s + d.due, 0);
    const feitas = dias.reduce((s, d) => s + d.done, 0);
    return devidas ? { pct: Math.round((feitas * 100) / devidas), feitas, devidas } : null;
  });

  /** Tarefas ativas com sequência e % de 30 dias, da maior sequência para a menor. */
  protected readonly porTarefa = computed(() => {
    const stats = new Map((this.sequencias.value() ?? []).map((s) => [s.taskId, s]));
    return (this.tarefas.value() ?? [])
      .filter((t) => !t.archived)
      .map((t) => ({
        task: t,
        streak: stats.get(t.id)?.streak ?? 0,
        taxa: stats.get(t.id)?.completionRate ?? null,
      }))
      .sort((a, b) => b.streak - a.streak || a.task.position - b.task.position);
  });

  protected readonly melhor = computed(() => this.porTarefa()[0] ?? null);

  /** Mapa de calor: colunas de semana (seg a dom), com casas vazias antes do primeiro dia. */
  protected readonly casas = computed<Casa[]>(() => {
    const dias = this.historico.hasValue() ? this.historico.value() : [];
    if (!dias.length) {
      return [];
    }
    const antes = (parseIsoDate(dias[0].date).getDay() + 6) % 7;
    const vazias: Casa[] = Array.from({ length: antes }, () => ({
      date: null,
      nivel: null,
      rotulo: '',
    }));
    return [...vazias, ...dias.map((d) => this.casa(d))];
  });

  /** Minutos por semana, com a altura relativa à maior semana do período. */
  protected readonly barras = computed(() => {
    const semanas = this.estudo.value()?.weeks ?? [];
    const max = Math.max(1, ...semanas.map((s) => s.minutes));
    return semanas.map((s) => ({ ...s, altura: (s.minutes * 100) / max }));
  });

  protected readonly materias = computed(() => {
    const lista = (this.estudo.value()?.subjects ?? []).filter((m) => m.minutes > 0);
    const max = Math.max(1, ...lista.map((m) => m.minutes));
    return [...lista]
      .sort((a, b) => b.minutes - a.minutes)
      .map((m) => ({ ...m, largura: (m.minutes * 100) / max }));
  });

  protected escolher(p: Periodo): void {
    this.periodo.set(p);
  }

  protected taxa(valor: string | null): string {
    return valor === null ? '—' : `${Math.round(Number(valor))}%`;
  }

  private casa(d: DayCount): Casa {
    const data = parseIsoDate(d.date).toLocaleDateString('pt-BR', {
      day: '2-digit',
      month: '2-digit',
    });
    if (!d.due) {
      return { date: d.date, nivel: null, rotulo: `${data}: nenhuma tarefa` };
    }
    const r = d.done / d.due;
    const nivel = r === 0 ? 0 : r < 0.5 ? 1 : r < 1 ? 2 : 3;
    return { date: d.date, nivel, rotulo: `${data}: ${d.done} de ${d.due} feitas` };
  }
}
