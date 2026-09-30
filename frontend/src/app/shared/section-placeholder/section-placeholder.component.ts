import { Component, input } from '@angular/core';

// Cabeçalho das seções que ainda não têm conteúdo; cada etapa do plano substitui o texto
@Component({
  selector: 'app-section-placeholder',
  template: `
    <h1 class="titulo">{{ title() }}</h1>
    <p class="aviso">{{ description() }}</p>
  `,
  styles: `
    .titulo {
      font: var(--mat-sys-headline-medium);
      margin: 8px 0 16px;
    }
    .aviso {
      color: var(--mat-sys-on-surface-variant);
    }
  `,
})
export class SectionPlaceholderComponent {
  readonly title = input.required<string>();
  readonly description = input.required<string>();
}
