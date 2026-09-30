import { BreakpointObserver } from '@angular/cdk/layout';
import { Component, inject, viewChild } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatListModule } from '@angular/material/list';
import { MatSidenav, MatSidenavModule } from '@angular/material/sidenav';
import { MatToolbarModule } from '@angular/material/toolbar';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { map } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';

export interface NavItem {
  label: string;
  path: string;
  icon: string;
}

export const NAV_ITEMS: NavItem[] = [
  { label: 'Hoje', path: '/hoje', icon: 'wb_sunny' },
  { label: 'Compromissos', path: '/compromissos', icon: 'event' },
  { label: 'Estudos', path: '/estudos', icon: 'school' },
  { label: 'Finanças', path: '/financas', icon: 'account_balance_wallet' },
  { label: 'Configurações', path: '/configuracoes', icon: 'settings' },
];

/**
 * Layout das telas logadas: barra superior, menu lateral e o conteúdo da seção.
 * No computador o menu fica fixo; abaixo de 768px vira gaveta aberta pelo botão de menu.
 */
@Component({
  selector: 'app-shell',
  imports: [
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    MatSidenavModule,
    MatToolbarModule,
    MatListModule,
    MatIconModule,
    MatButtonModule,
  ],
  templateUrl: './shell.component.html',
  styleUrl: './shell.component.scss',
})
export class ShellComponent {
  protected readonly auth = inject(AuthService);
  protected readonly items = NAV_ITEMS;

  private readonly sidenav = viewChild.required(MatSidenav);

  protected readonly isMobile = toSignal(
    inject(BreakpointObserver)
      .observe('(max-width: 767.98px)')
      .pipe(map((state) => state.matches)),
    { initialValue: false },
  );

  protected onNavigate(): void {
    if (this.isMobile()) {
      void this.sidenav().close();
    }
  }
}
