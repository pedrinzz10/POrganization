import { Component, computed, inject, input, output, signal } from '@angular/core';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBar } from '@angular/material/snack-bar';
import { firstValueFrom } from 'rxjs';
import { problemMessage } from '../../../core/http/problem';
import { Occurrence } from '../data/commitment.model';
import { CommitmentsService } from '../data/commitments.service';
import { compareOccurrences, occurrenceKey } from './occurrence-order';

/**
 * Lista cronológica de um dia, com checkbox de concluído. O pai passa as ocorrências por
 * input(); a ordem e o estado "feito" são derivados com computed(). Marcar é otimista: risca
 * na hora e desfaz se a API falhar.
 */
@Component({
  selector: 'app-day-view',
  imports: [MatCheckboxModule, MatIconModule],
  templateUrl: './day-view.component.html',
  styleUrl: './day-view.component.scss',
})
export class DayViewComponent {
  private readonly commitments = inject(CommitmentsService);
  private readonly snackBar = inject(MatSnackBar);

  readonly occurrences = input.required<Occurrence[]>();
  /** Clique no título: o pai abre o formulário de edição. */
  readonly opened = output<Occurrence>();

  /** "feito" marcado nesta tela, por cima do que veio da API, até a próxima carga. */
  private readonly doneOverrides = signal<ReadonlyMap<string, boolean>>(new Map());

  protected readonly items = computed(() => {
    const overrides = this.doneOverrides();
    return [...this.occurrences()]
      .sort(compareOccurrences)
      .map((o) => ({ ...o, done: overrides.get(occurrenceKey(o)) ?? o.done }));
  });

  protected readonly key = occurrenceKey;

  async toggle(occurrence: Occurrence, done: boolean): Promise<void> {
    const key = occurrenceKey(occurrence);
    this.setOverride(key, done);
    try {
      if (occurrence.recurring) {
        await firstValueFrom(
          this.commitments.patchOccurrence(occurrence.commitmentId, occurrence.occurrenceDate, { done }),
        );
      } else {
        await firstValueFrom(this.commitments.setDone(occurrence.commitmentId, done));
      }
    } catch (error) {
      this.setOverride(key, !done);
      this.snackBar.open(problemMessage(error, 'Não foi possível atualizar.'), 'OK', { duration: 5000 });
    }
  }

  private setOverride(key: string, done: boolean): void {
    this.doneOverrides.update((current) => new Map(current).set(key, done));
  }
}
