import { computed, DestroyRef, inject, Signal, signal } from '@angular/core';

/**
 * Relógio da tela: um signal com o "agora", atualizado a cada segundo por um setInterval que é
 * limpo automaticamente quando o componente que o criou sai da tela (DestroyRef).
 * Precisa ser chamado num contexto de injeção (campo ou construtor de componente).
 */
export function tickingNow(intervalMs = 1000): Signal<number> {
  const now = signal(Date.now());
  const id = setInterval(() => now.set(Date.now()), intervalMs);
  inject(DestroyRef).onDestroy(() => clearInterval(id));
  return now.asReadonly();
}

/**
 * Segundos estudados, derivados do relógio: base que veio da API + tempo corrido desde que ela
 * chegou (só enquanto está rodando). Não conta ticks, então aba em segundo plano não atrasa.
 */
export function elapsedSeconds(
  now: Signal<number>,
  base: Signal<{ elapsedSeconds: number; running: boolean; receivedAt: number }>,
): Signal<number> {
  return computed(() => {
    const b = base();
    return b.running ? b.elapsedSeconds + Math.floor((now() - b.receivedAt) / 1000) : b.elapsedSeconds;
  });
}

/** "mm:ss" (ou "+mm:ss" depois de zerar, contando o tempo extra). */
export function formatCountdown(remainingSeconds: number): string {
  const overtime = remainingSeconds < 0;
  const total = Math.abs(remainingSeconds);
  const mm = String(Math.floor(total / 60)).padStart(2, '0');
  const ss = String(total % 60).padStart(2, '0');
  return `${overtime ? '+' : ''}${mm}:${ss}`;
}

/** Bipe curto ao terminar o tempo sugerido (silencioso se o navegador não tiver áudio). */
export function beep(): void {
  try {
    const AudioCtx = window.AudioContext ?? (window as unknown as { webkitAudioContext?: typeof AudioContext }).webkitAudioContext;
    if (!AudioCtx) {
      return;
    }
    const ctx = new AudioCtx();
    const osc = ctx.createOscillator();
    osc.frequency.value = 880;
    osc.connect(ctx.destination);
    osc.start();
    osc.stop(ctx.currentTime + 0.4);
  } catch {
    // sem áudio disponível: o aviso visual basta
  }
}
