import { Component } from '@angular/core';
import { MatTabsModule } from '@angular/material/tabs';
import { SubjectsPage } from './subjects/subjects.page';

/** Seção Estudos. As abas "Hoje" (plano do dia) e "Histórico" chegam na E09 e na E10. */
@Component({
  selector: 'app-studies-page',
  imports: [MatTabsModule, SubjectsPage],
  template: `
    <h1 class="titulo">Estudos</h1>
    <mat-tab-group mat-stretch-tabs="false" animationDuration="0ms">
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
