import { Component } from '@angular/core';
import { MatTabsModule } from '@angular/material/tabs';
import { StudyAgendaPage } from './agenda/study-agenda.page';
import { StudyHistoryPage } from './history/study-history.page';
import { SubjectsPage } from './subjects/subjects.page';
import { StudyTodayPage } from './today/study-today.page';
import { SectionTitleComponent } from '../settings/section-title.component';

/** Seção Estudos: plano do dia com o timer, agenda (dia, semana, mês), matérias e histórico. */
@Component({
  selector: 'app-studies-page',
  imports: [SectionTitleComponent, MatTabsModule, StudyAgendaPage, StudyTodayPage, SubjectsPage, StudyHistoryPage],
  template: `
    <app-section-title secao="studies">Estudos</app-section-title>
    <mat-tab-group mat-stretch-tabs="false" animationDuration="0ms">
      <mat-tab label="Hoje">
        <div class="painel"><app-study-today-page /></div>
      </mat-tab>
      <mat-tab label="Agenda">
        <ng-template matTabContent>
          <div class="painel"><app-study-agenda-page /></div>
        </ng-template>
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
    .painel {
      padding: 16px 0;
    }
  `,
})
export class StudiesPage {}
