import { Component, inject, input } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { Router } from '@angular/router';
import { DataResetService, SecaoId } from './data-reset.service';

/**
 * Título de uma seção com o botão "Apagar dados" ao lado (B14). Depois de apagar, abre a mesma
 * tela de novo para todas as partes dela buscarem os dados outra vez.
 */
@Component({
  selector: 'app-section-title',
  imports: [MatButtonModule, MatIconModule, MatTooltipModule],
  template: `
    <h1 class="titulo"><ng-content /></h1>
    <button
      mat-button
      type="button"
      class="apagar"
      [disabled]="reset.apagando() !== null"
      matTooltip="Apaga tudo o que você cadastrou nesta seção"
      (click)="apagar()"
    >
      <mat-icon aria-hidden="true">delete_sweep</mat-icon>
      Apagar dados
    </button>
  `,
  styles: `
    :host {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 8px;
      margin: 8px 0 16px;
    }
    .titulo {
      font: var(--mat-sys-headline-medium);
      margin: 0;
    }
    .apagar:not(:disabled) {
      color: var(--mat-sys-error);
    }
  `,
})
export class SectionTitleComponent {
  readonly secao = input.required<SecaoId>();

  protected readonly reset = inject(DataResetService);
  private readonly router = inject(Router);

  protected async apagar(): Promise<void> {
    if (await this.reset.apagar(this.secao())) {
      const url = this.router.url;
      await this.router.navigateByUrl('/', { skipLocationChange: true });
      await this.router.navigateByUrl(url);
    }
  }
}
