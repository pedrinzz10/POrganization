import { Component } from '@angular/core';
import { SectionPlaceholderComponent } from '../../shared/section-placeholder/section-placeholder.component';

@Component({
  selector: 'app-finance-page',
  imports: [SectionPlaceholderComponent],
  template: `<app-section-placeholder title="Finanças" description="Contas, cartões, orçamentos e metas chegam na etapa 4." />`,
})
export class FinancePage {}
