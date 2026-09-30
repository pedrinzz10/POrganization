import { Component } from '@angular/core';
import { MatTabsModule } from '@angular/material/tabs';
import { SubjectsPage } from './subjects/subjects.page';
import { StudyTodayPage } from './today/study-today.page';

/** Seção Estudos: o plano do dia com o timer e as matérias. A aba "Histórico" chega na E10. */
@Component({
  selector: 'app-studies-page',
  imports: [MatTabsModule, StudyTodayPage, SubjectsPage],
  template: `
    <h1 class="titulo">Estudos</h1>
    <mat-tab-group mat-stretch-tabs="false" animationDuration="0ms">
      <mat-tab label="Hoje">
        <div class="painel"><app-study-today-page /></div>
      </mat-tab>
      <mat-tab label="Matérias">
        <div class="painel"><app-subjects-page /></div>
      </mat-tab>
    </mat-tab-group>
  `,
  styles: `
    .titulo {
      font: var(--mat-sys-headline-medium);
      margin: 8px 0 16px;
    }
    .painel {
      padding: 16px 0;
    }
  `,
})
export class StudiesPage {}
