import { Component } from '@angular/core';
import { MatTabsModule } from '@angular/material/tabs';
import { IsActiveMatchOptions, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { SectionTitleComponent } from '../settings/section-title.component';

/** Uma aba por tela de finanças; cada aba é uma rota filha (/financas/contas, ...). */
export const FINANCE_TABS = [
  { label: 'Resumo', path: './' },
  { label: 'Extrato', path: 'extrato' },
  { label: 'Contas', path: 'contas' },
  { label: 'Cartões', path: 'cartoes' },
  { label: 'Agendados', path: 'agendados' },
  { label: 'Orçamentos', path: 'orcamentos' },
  { label: 'Metas', path: 'metas' },
];

/** Seção Finanças: título, abas de navegação e a tela da aba escolhida. */
@Component({
  selector: 'app-finance-page',
  imports: [SectionTitleComponent, MatTabsModule, RouterLink, RouterLinkActive, RouterOutlet],
  template: `
    <app-section-title secao="finance">Finanças</app-section-title>
    <nav mat-tab-nav-bar [tabPanel]="painel" mat-stretch-tabs="false" aria-label="Telas de finanças">
      @for (aba of abas; track aba.path) {
        <a
          mat-tab-link
          [routerLink]="aba.path"
          routerLinkActive
          #ativo="routerLinkActive"
          [routerLinkActiveOptions]="aba.path === './' ? exato : parcial"
          [active]="ativo.isActive"
          >{{ aba.label }}</a
        >
      }
    </nav>
    <mat-tab-nav-panel #painel>
      <div class="painel"><router-outlet /></div>
    </mat-tab-nav-panel>
  `,
  styles: `
    .painel {
      padding: 16px 0;
    }
  `,
})
export class FinancePage {
  protected readonly abas = FINANCE_TABS;
  // O resumo é a rota vazia: só fica ativo nela, com qualquer query param (?month=...)
  protected readonly exato: IsActiveMatchOptions = { paths: 'exact', queryParams: 'ignored', matrixParams: 'ignored', fragment: 'ignored' };
  protected readonly parcial: IsActiveMatchOptions = { paths: 'subset', queryParams: 'ignored', matrixParams: 'ignored', fragment: 'ignored' };
}
