import { Component, inject } from '@angular/core';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { ThemePreference, ThemeService } from '../../core/theme/theme.service';

/** Claro, escuro ou igual ao sistema; vale para este aparelho. */
@Component({
  selector: 'app-appearance',
  imports: [MatButtonToggleModule],
  template: `
    <mat-button-toggle-group
      aria-label="Tema"
      hideSingleSelectionIndicator
      [value]="theme.preference()"
      (change)="escolher($event.value)"
    >
      <mat-button-toggle value="light">Claro</mat-button-toggle>
      <mat-button-toggle value="dark">Escuro</mat-button-toggle>
      <mat-button-toggle value="system">Sistema</mat-button-toggle>
    </mat-button-toggle-group>
    <p class="dica">Vale para este aparelho.</p>
  `,
  styles: `
    .dica {
      margin: var(--space-2) 0 0;
      font: var(--mat-sys-body-small);
      color: var(--fg-secondary);
    }
  `,
})
export class AppearanceComponent {
  protected readonly theme = inject(ThemeService);

  protected escolher(preference: ThemePreference): void {
    this.theme.set(preference);
  }
}
