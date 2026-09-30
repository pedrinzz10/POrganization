import { Component } from '@angular/core';
import { SectionPlaceholderComponent } from '../../shared/section-placeholder/section-placeholder.component';

@Component({
  selector: 'app-today-page',
  imports: [SectionPlaceholderComponent],
  template: `<app-section-placeholder title="Hoje" description="Seu dia em um só lugar: compromissos, estudos e finanças chegam aqui nas próximas etapas." />`,
})
export class TodayPage {}
