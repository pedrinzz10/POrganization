import { Component, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { PushService } from '../../core/push/push.service';

/** Configurações: notificações no navegador. As specs seguintes acrescentam preferências e o Google Calendar. */
@Component({
  selector: 'app-settings-page',
  imports: [MatButtonModule, MatIconModule],
  template: `
    <h1 class="titulo">Configurações</h1>

    <section class="bloco" aria-labelledby="config-notificacoes">
      <h2 id="config-notificacoes" class="bloco__titulo">Notificações no navegador</h2>
      @if (!push.supported) {
        <p class="dica">
          Este navegador não recebe notificações do app. Elas funcionam na versão publicada, em navegadores com suporte a
          Web Push (no iPhone, adicione o app à tela de início).
        </p>
      } @else if (push.subscribed()) {
        <p class="status"><mat-icon aria-hidden="true">notifications_active</mat-icon> Ativadas neste navegador.</p>
        <button mat-stroked-button type="button" [disabled]="ocupado()" (click)="desativar()">Desativar notificações</button>
      } @else {
        <p class="dica">Receba os lembretes dos compromissos aqui, mesmo com o app fechado.</p>
        <button mat-flat-button type="button" [disabled]="ocupado()" (click)="ativar()">
          <mat-icon aria-hidden="true">notifications</mat-icon>
          Ativar notificações
        </button>
      }
      @if (erro(); as mensagem) {
        <p class="erro" role="alert">{{ mensagem }}</p>
      }
    </section>
  `,
  styles: `
    .titulo {
      font: var(--mat-sys-headline-medium);
      margin: 8px 0 16px;
    }
    .bloco {
      padding: 16px;
      margin-bottom: 16px;
      border-radius: 16px;
      border: 1px solid var(--mat-sys-outline-variant);
    }
    .bloco__titulo {
      font: var(--mat-sys-title-medium);
      margin: 0 0 8px;
    }
    .dica {
      color: var(--mat-sys-on-surface-variant);
    }
    .status {
      display: flex;
      align-items: center;
      gap: 8px;
    }
    .erro {
      color: var(--mat-sys-error);
    }
  `,
})
export class SettingsPage {
  protected readonly push = inject(PushService);
  protected readonly ocupado = signal(false);
  protected readonly erro = signal<string | null>(null);

  protected async ativar(): Promise<void> {
    await this.executar(() => this.push.enable(), 'Não foi possível ativar. Verifique se o navegador permitiu as notificações.');
  }

  protected async desativar(): Promise<void> {
    await this.executar(() => this.push.disable(), 'Não foi possível desativar.');
  }

  private async executar(acao: () => Promise<void>, falha: string): Promise<void> {
    this.ocupado.set(true);
    this.erro.set(null);
    try {
      await acao();
    } catch (error) {
      this.erro.set(error instanceof Error && error.message.includes('configuradas') ? error.message : falha);
    } finally {
      this.ocupado.set(false);
    }
  }
}
