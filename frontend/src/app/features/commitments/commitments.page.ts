import { Component } from '@angular/core';
import { SectionPlaceholderComponent } from '../../shared/section-placeholder/section-placeholder.component';

@Component({
  selector: 'app-commitments-page',
  imports: [SectionPlaceholderComponent],
  template: `<app-section-placeholder title="Compromissos" description="Criação rápida, recorrência e as visões de dia, semana, mês e ano chegam na etapa 2." />`,
})
export class CommitmentsPage {}
