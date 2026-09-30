import { Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { AuthService } from '../../core/auth/auth.service';

// Provisória: a B09 coloca esta página dentro do layout, e as etapas 2 a 4 preenchem as seções
@Component({
  selector: 'app-today-page',
  imports: [MatButtonModule],
  template: `
    <main style="padding: 16px">
      <h1>Hoje</h1>
      <p>Logado como {{ auth.user()?.email }}</p>
      <button mat-stroked-button type="button" (click)="auth.signOut()">Sair</button>
    </main>
  `,
})
export class TodayPage {
  protected readonly auth = inject(AuthService);
}
