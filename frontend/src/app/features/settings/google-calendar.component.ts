import { HttpClient } from '@angular/common/http';
import { Component, computed, inject, input, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../environments/environment';
import { problemMessage } from '../../core/http/problem';

interface GoogleStatus {
  /** Integração configurada no servidor. */
  available: boolean;
  connected: boolean;
  email: string | null;
  calendarId: string | null;
  connectedAt: string | null;
}

/** Porta de saída para o Google (trocável nos testes: navegar de verdade derrubaria o ambiente). */
export const navigation = { go: (url: string) => window.location.assign(url) };

/**
 * Conectar o Google Calendar. "Conectar" busca a URL de consentimento na API e sai do app para o
 * Google; na volta, a API redireciona para /configuracoes?google=ok|erro e o resultado chega aqui
 * pelo input `result` (query param lido pela rota com withComponentInputBinding()).
 */
@Component({
  selector: 'app-google-calendar',
  imports: [MatButtonModule, MatIconModule],
  template: `
    @switch (result()) {
      @case ('ok') {
        <p class="ok" role="status">Google Calendar conectado.</p>
      }
      @case ('erro') {
        <p class="erro" role="alert">Não foi possível conectar o Google Calendar. Tente de novo.</p>
      }
    }

    @if (status.hasValue()) {
      @let s = status.value();
      @if (!s.available) {
        <p class="dica">A integração com o Google ainda não foi configurada no servidor.</p>
      } @else if (s.connected) {
        <p class="conectado">
          <mat-icon aria-hidden="true">event_available</mat-icon>
          Conectado{{ s.email ? ' como ' + s.email : '' }}. Os compromissos vão para a agenda {{ s.calendarId === 'primary' ? 'principal' : s.calendarId }}.
        </p>
        <button mat-stroked-button type="button" [disabled]="ocupado()" (click)="desconectar()">Desconectar</button>
      } @else {
        <p class="dica">Publique seus compromissos no Google Calendar e traga os eventos de lá para cá.</p>
        <button mat-flat-button type="button" [disabled]="ocupado()" (click)="conectar()">
          <mat-icon aria-hidden="true">event</mat-icon>
          Conectar Google Calendar
        </button>
      }
    }
    @if (erro(); as mensagem) {
      <p class="erro" role="alert">{{ mensagem }}</p>
    }
  `,
  styles: `
    .dica {
      color: var(--mat-sys-on-surface-variant);
    }
    .conectado {
      display: flex;
      align-items: center;
      gap: 8px;
    }
    .ok {
      color: #2e7d32;
    }
    .erro {
      color: var(--mat-sys-error);
    }
  `,
})
export class GoogleCalendarComponent {
  private readonly http = inject(HttpClient);
  private readonly api = `${environment.apiUrl}/integrations/google`;

  /** ?google=ok|erro da volta do Google. */
  readonly result = input<string>();

  protected readonly status = rxResource({ stream: () => this.http.get<GoogleStatus>(this.api) });
  protected readonly ocupado = signal(false);
  private readonly falha = signal<string | null>(null);
  protected readonly erro = computed(() =>
    this.falha() ?? (this.status.error() ? problemMessage(this.status.error(), 'Não foi possível ver a conexão com o Google.') : null),
  );

  protected async conectar(): Promise<void> {
    this.ocupado.set(true);
    this.falha.set(null);
    try {
      const { url } = await firstValueFrom(this.http.get<{ url: string }>(`${this.api}/auth-url`));
      navigation.go(url);
    } catch (error) {
      this.falha.set(problemMessage(error, 'Não foi possível abrir o Google.'));
      this.ocupado.set(false);
    }
  }

  protected async desconectar(): Promise<void> {
    this.ocupado.set(true);
    this.falha.set(null);
    try {
      await firstValueFrom(this.http.delete<void>(this.api));
      this.status.reload();
    } catch (error) {
      this.falha.set(problemMessage(error, 'Não foi possível desconectar.'));
    } finally {
      this.ocupado.set(false);
    }
  }
}
