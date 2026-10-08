import { DOCUMENT } from '@angular/common';
import { inject, Injectable } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { SwUpdate, VersionReadyEvent } from '@angular/service-worker';
import { filter } from 'rxjs';

/** Recarregar a página (trocável nos testes: recarregar de verdade derrubaria o ambiente). */
export const pagina = { recarregar: () => location.reload() };

/** De quanto em quanto tempo procura versão nova com o app aberto. */
export const INTERVALO_MS = 15 * 60 * 1000;

/**
 * Versão nova do app (B16). O service worker guarda o app no aparelho e, sem isto, a versão nova só
 * entrava na segunda abertura. Agora: procura ao abrir, ao voltar para a aba e a cada 15 minutos;
 * quando a versão nova termina de baixar, recarrega sozinho se a aba está em segundo plano, ou
 * avisa com "Atualizar" se você está usando.
 */
@Injectable({ providedIn: 'root' })
export class AppUpdateService {
  private readonly sw = inject(SwUpdate);
  private readonly snackBar = inject(MatSnackBar);
  private readonly document = inject(DOCUMENT);

  iniciar(): void {
    if (!this.sw.isEnabled) {
      return;
    }
    this.sw.versionUpdates
      .pipe(filter((e): e is VersionReadyEvent => e.type === 'VERSION_READY'))
      .subscribe(() => this.versaoPronta());
    this.sw.unrecoverable.subscribe(() => pagina.recarregar());

    const procurar = () => void this.sw.checkForUpdate().catch(() => undefined);
    procurar();
    this.document.addEventListener('visibilitychange', () => {
      if (this.document.visibilityState === 'visible') {
        procurar();
      }
    });
    setInterval(procurar, INTERVALO_MS);
  }

  private versaoPronta(): void {
    if (this.document.visibilityState === 'hidden') {
      pagina.recarregar();
      return;
    }
    this.snackBar
      .open('Nova versão do app disponível.', 'Atualizar')
      .onAction()
      .subscribe(() => pagina.recarregar());
  }
}
