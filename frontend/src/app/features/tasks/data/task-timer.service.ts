import { DestroyRef, inject, Injectable, signal } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { firstValueFrom } from 'rxjs';
import { problemMessage } from '../../../core/http/problem';
import { TasksService } from './tasks.service';

/** Um cronômetro de tarefa: rodando (endsAt) ou pausado (remainingMs). */
export interface TaskTimer {
  taskId: string;
  /** Dia (fuso do usuário) em que a tarefa será marcada ao terminar. */
  day: string;
  title: string;
  totalMs: number;
  /** Quanto falta enquanto pausado. */
  remainingMs: number;
  /** Instante (ms) em que termina, se rodando; null = pausado. */
  endsAt: number | null;
}

const CHAVE = 'porganization.cronometros';

/**
 * Cronômetros das tarefas diárias (T07). Ficam no serviço raiz e no localStorage, então seguem
 * rodando ao trocar de tela ou recarregar a página; ao zerar, a tarefa é marcada como feita.
 * Um tick por segundo atualiza `agora`, de onde a tela calcula o tempo restante.
 */
@Injectable({ providedIn: 'root' })
export class TaskTimerService {
  private readonly tasks = inject(TasksService);
  private readonly snackBar = inject(MatSnackBar);

  readonly timers = signal<Record<string, TaskTimer>>(ler());
  readonly agora = signal(Date.now());
  /** Ids das tarefas que o cronômetro marcou como feitas (a tela Hoje atualiza a lista). */
  readonly concluidas = signal<ReadonlySet<string>>(new Set());

  private intervalo: ReturnType<typeof setInterval> | null = null;

  constructor() {
    inject(DestroyRef).onDestroy(() => this.parar());
    this.sincronizar();
  }

  restante(taskId: string): number | null {
    const t = this.timers()[taskId];
    if (!t) {
      return null;
    }
    return Math.max(0, t.endsAt === null ? t.remainingMs : t.endsAt - this.agora());
  }

  iniciar(taskId: string, day: string, title: string, minutos: number): void {
    const totalMs = minutos * 60_000;
    this.gravar({
      ...this.timers(),
      [taskId]: { taskId, day, title, totalMs, remainingMs: totalMs, endsAt: Date.now() + totalMs },
    });
  }

  pausar(taskId: string): void {
    const t = this.timers()[taskId];
    if (t?.endsAt) {
      this.gravar({
        ...this.timers(),
        [taskId]: { ...t, remainingMs: Math.max(0, t.endsAt - Date.now()), endsAt: null },
      });
    }
  }

  retomar(taskId: string): void {
    const t = this.timers()[taskId];
    if (t && t.endsAt === null) {
      this.gravar({ ...this.timers(), [taskId]: { ...t, endsAt: Date.now() + t.remainingMs } });
    }
  }

  cancelar(taskId: string): void {
    const { [taskId]: _, ...resto } = this.timers();
    this.gravar(resto);
  }

  private gravar(timers: Record<string, TaskTimer>): void {
    this.timers.set(timers);
    try {
      localStorage.setItem(CHAVE, JSON.stringify(timers));
    } catch {
      // Sem localStorage o cronômetro só não sobrevive ao recarregar
    }
    this.sincronizar();
  }

  /** Liga o tick só enquanto há cronômetro rodando. */
  private sincronizar(): void {
    this.agora.set(Date.now());
    const rodando = Object.values(this.timers()).some((t) => t.endsAt !== null);
    if (rodando && !this.intervalo) {
      this.intervalo = setInterval(() => this.tick(), 1000);
    } else if (!rodando) {
      this.parar();
    }
    this.tick();
  }

  private parar(): void {
    if (this.intervalo) {
      clearInterval(this.intervalo);
      this.intervalo = null;
    }
  }

  private tick(): void {
    const agora = Date.now();
    this.agora.set(agora);
    for (const t of Object.values(this.timers())) {
      if (t.endsAt !== null && t.endsAt <= agora) {
        void this.terminar(t);
      }
    }
  }

  private async terminar(t: TaskTimer): Promise<void> {
    if (!this.timers()[t.taskId]) {
      return; // já tratado por outro tick
    }
    this.cancelar(t.taskId);
    navigator.vibrate?.([200, 100, 200]);
    try {
      await firstValueFrom(this.tasks.complete(t.taskId, t.day));
      this.concluidas.update((s) => new Set(s).add(t.taskId));
      this.snackBar.open(`⏱️ Tempo de "${t.title}" concluído. Tarefa feita!`, 'OK', {
        duration: 6000,
      });
    } catch (error) {
      this.snackBar.open(
        problemMessage(error, `O tempo de "${t.title}" acabou, mas não deu para marcar a tarefa.`),
        'OK',
        {
          duration: 6000,
        },
      );
    }
  }
}

function ler(): Record<string, TaskTimer> {
  try {
    const salvo = JSON.parse(localStorage.getItem(CHAVE) ?? '{}');
    return salvo && typeof salvo === 'object' ? salvo : {};
  } catch {
    return {};
  }
}

/** 1500000 → "25:00"; acima de uma hora, "1:05:00". */
export function formatarTempo(ms: number): string {
  const total = Math.ceil(ms / 1000);
  const h = Math.floor(total / 3600);
  const m = Math.floor((total % 3600) / 60);
  const s = total % 60;
  const mmss = `${String(m).padStart(h ? 2 : 1, '0')}:${String(s).padStart(2, '0')}`;
  return h ? `${h}:${mmss}` : mmss.padStart(5, '0');
}
