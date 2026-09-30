import { Component, input, output } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { Occurrence } from '../../commitments/data/commitment.model';
import { QuickAddComponent } from '../../commitments/quick-add/quick-add.component';
import { DayViewComponent } from '../../commitments/views/day-view.component';

/** Seção "Compromissos" da tela Hoje: criação rápida e a lista do dia com checkbox. */
@Component({
  selector: 'app-today-commitments',
  imports: [QuickAddComponent, DayViewComponent, RouterLink, MatButtonModule],
  template: `
    <section class="secao" aria-labelledby="hoje-compromissos">
      <div class="secao__topo">
        <h2 id="hoje-compromissos" class="secao__titulo">Compromissos de hoje</h2>
        <a mat-button routerLink="/compromissos">Ver agenda</a>
      </div>
      <app-quick-add (created)="changed.emit()" />
      <app-day-view [occurrences]="occurrences()" />
    </section>
  `,
  styles: `
    .secao__topo {
      display: flex;
      align-items: center;
      justify-content: space-between;
    }
    .secao__titulo {
      font: var(--mat-sys-title-large);
      margin: 8px 0;
    }
  `,
})
export class TodayCommitmentsComponent {
  readonly occurrences = input.required<Occurrence[]>();
  /** Algo foi criado: a página recarrega a tela Hoje. */
  readonly changed = output<void>();
}
