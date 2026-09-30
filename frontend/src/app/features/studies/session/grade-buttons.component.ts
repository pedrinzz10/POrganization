import { Component, input, output } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { ReviewGrade } from '../data/study.model';

/** Os 3 botões que encerram uma revisão: a nota vai para o FSRS reagendar. */
@Component({
  selector: 'app-grade-buttons',
  imports: [MatButtonModule],
  template: `
    <p class="pergunta">Como foi a revisão?</p>
    <div class="notas">
      <button mat-stroked-button type="button" [disabled]="disabled()" (click)="graded.emit('DIFICIL')">Difícil</button>
      <button mat-stroked-button type="button" [disabled]="disabled()" (click)="graded.emit('OK')">Ok</button>
      <button mat-flat-button type="button" [disabled]="disabled()" (click)="graded.emit('FACIL')">Fácil</button>
    </div>
  `,
  styles: `
    .pergunta {
      margin: 0 0 8px;
      font: var(--mat-sys-title-small);
    }
    .notas {
      display: flex;
      gap: 8px;
      flex-wrap: wrap;
    }
  `,
})
export class GradeButtonsComponent {
  readonly disabled = input(false);
  readonly graded = output<ReviewGrade>();
}
