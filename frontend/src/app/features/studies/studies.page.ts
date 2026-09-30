import { Component } from '@angular/core';
import { SectionPlaceholderComponent } from '../../shared/section-placeholder/section-placeholder.component';

@Component({
  selector: 'app-studies-page',
  imports: [SectionPlaceholderComponent],
  template: `<app-section-placeholder title="Estudos" description="Matérias, timer e revisões espaçadas chegam na etapa 3." />`,
})
export class StudiesPage {}
