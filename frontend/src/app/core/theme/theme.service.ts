import { DOCUMENT } from '@angular/common';
import { Injectable, computed, inject, signal } from '@angular/core';

export type ThemePreference = 'light' | 'dark' | 'system';
export type Theme = 'light' | 'dark';

const STORAGE_KEY = 'porganization.tema';

/**
 * Tema claro/escuro do Intelly Design System: grava data-theme no <html> (os tokens de
 * tokens.css trocam por ele) e color-scheme. A escolha fica neste aparelho (localStorage);
 * "Sistema" segue o sistema operacional e muda junto com ele.
 */
@Injectable({ providedIn: 'root' })
export class ThemeService {
  private readonly document = inject(DOCUMENT);
  private readonly media = this.document.defaultView?.matchMedia?.('(prefers-color-scheme: dark)');
  private readonly systemDark = signal(this.media?.matches ?? false);

  readonly preference = signal<ThemePreference>(this.read());
  readonly theme = computed<Theme>(() =>
    this.preference() === 'system'
      ? this.systemDark()
        ? 'dark'
        : 'light'
      : (this.preference() as Theme),
  );

  constructor() {
    this.apply();
    this.media?.addEventListener?.('change', (e) => {
      this.systemDark.set(e.matches);
      this.apply();
    });
  }

  set(preference: ThemePreference): void {
    this.preference.set(preference);
    this.apply();
    try {
      this.document.defaultView?.localStorage.setItem(STORAGE_KEY, preference);
    } catch {
      // Sem localStorage (modo privado): vale só até fechar a aba
    }
  }

  /**
   * Grava o tema no <html> na hora (não num effect): quem reage ao sinal, como os gráficos,
   * já lê os tokens do tema novo.
   */
  private apply(): void {
    const root = this.document.documentElement;
    root.dataset['theme'] = this.theme();
    root.style.colorScheme = this.theme();
  }

  private read(): ThemePreference {
    try {
      const saved = this.document.defaultView?.localStorage.getItem(STORAGE_KEY);
      return saved === 'light' || saved === 'dark' || saved === 'system' ? saved : 'light';
    } catch {
      return 'light';
    }
  }
}
