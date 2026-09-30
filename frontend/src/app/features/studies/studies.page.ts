import { Component } from '@angular/core';
import { MatTabsModule } from '@angular/material/tabs';
import { StudyHistoryPage } from './history/study-history.page';
import { SubjectsPage } from './subjects/subjects.page';
import { StudyTodayPage } from './today/study-today.page';

/** Seção Estudos: plano do dia com o timer, matérias e histórico. */
@Component({
  selector: 'app-studies-page',
  imports: [MatTabsModule, StudyTodayPage, SubjectsPage, StudyHistoryPage],
  template: `
    <h1 class="titulo">Estudos</h1>
    <mat-tab-group mat-stretch-tabs="false" animationDuration="0ms">
      <mat-tab label="Hoje">
        <div class="painel"><app-study-today-page /></div>
      </mat-tab>
      <mat-tab label="Matérias">
        <div class="painel"><app-subjects-page /></div>
      </mat-tab>
      <mat-tab label="Histórico">
        <ng-template matTabContent>
          <div class="painel"><app-study-history-page /></div>
        </ng-template>
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
