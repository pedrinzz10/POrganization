import { Component } from '@angular/core';
import { QuickAddComponent } from './quick-add/quick-add.component';

@Component({
  selector: 'app-commitments-page',
  imports: [QuickAddComponent],
  template: `
    <h1 class="titulo">Compromissos</h1>
    <app-quick-add />
    <p class="aviso">As visões de dia, semana, mês e ano chegam nas próximas specs.</p>
  `,
  styles: `
    .titulo {
      font: var(--mat-sys-headline-medium);
      margin: 8px 0 16px;
    }
    .aviso {
      color: var(--mat-sys-on-surface-variant);
    }
  `,
})
export class CommitmentsPage {}
