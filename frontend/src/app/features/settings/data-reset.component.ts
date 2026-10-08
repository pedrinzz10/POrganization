import { Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { DataResetService, SECOES } from './data-reset.service';

/** Bloco "Apagar dados" de Configurações: um botão por seção (B14). */
@Component({
  selector: 'app-data-reset',
  imports: [MatButtonModule, MatIconModule],
  template: `
    <p class="dica">Apaga tudo o que você cadastrou numa seção. Não dá para desfazer.</p>
    <ul class="secoes">
      @for (secao of secoes; track secao.id) {
        <li class="secao">
          <mat-icon aria-hidden="true">{{ secao.icone }}</mat-icon>
          <span class="secao__nome">{{ secao.nome }}</span>
          <button
            mat-stroked-button
            type="button"
            class="apagar"
            [disabled]="reset.apagando() !== null"
            (click)="reset.apagar(secao.id)"
          >
            Apagar {{ secao.nome.toLowerCase() }}
          </button>
        </li>
      }
    </ul>
  `,
  styles: `
    .dica {
      color: var(--mat-sys-on-surface-variant);
    }
    .secoes {
      list-style: none;
      margin: 0;
      padding: 0;
    }
    .secao {
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 8px 0;
      border-top: 1px solid var(--mat-sys-outline-variant);
    }
    .secao__nome {
      flex: 1;
    }
    .apagar:not(:disabled) {
      color: var(--mat-sys-error);
      border-color: var(--mat-sys-error);
    }
  `,
})
export class DataResetComponent {
  protected readonly reset = inject(DataResetService);
  protected readonly secoes = SECOES;
}
