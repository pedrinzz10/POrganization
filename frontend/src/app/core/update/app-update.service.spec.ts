import { TestBed } from '@angular/core/testing';
import { MatSnackBar } from '@angular/material/snack-bar';
import { SwUpdate, VersionEvent } from '@angular/service-worker';
import { Subject } from 'rxjs';
import { AppUpdateService, pagina } from './app-update.service';

describe('AppUpdateService', () => {
  let versoes: Subject<VersionEvent>;
  let sw: {
    isEnabled: boolean;
    versionUpdates: Subject<VersionEvent>;
    unrecoverable: Subject<unknown>;
    checkForUpdate: ReturnType<typeof vi.fn>;
  };
  let acao: Subject<void>;
  let snackBar: { open: ReturnType<typeof vi.fn> };
  let recarregar: ReturnType<typeof vi.spyOn>;
  let visibilidade: DocumentVisibilityState;

  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['setInterval', 'clearInterval'] });
    versoes = new Subject();
    acao = new Subject();
    sw = {
      isEnabled: true,
      versionUpdates: versoes,
      unrecoverable: new Subject(),
      checkForUpdate: vi.fn().mockResolvedValue(false),
    };
    snackBar = { open: vi.fn(() => ({ onAction: () => acao })) };
    recarregar = vi.spyOn(pagina, 'recarregar').mockImplementation(() => undefined);
    visibilidade = 'visible';
    vi.spyOn(document, 'visibilityState', 'get').mockImplementation(() => visibilidade);
    TestBed.configureTestingModule({
      providers: [
        { provide: SwUpdate, useValue: sw },
        { provide: MatSnackBar, useValue: snackBar },
      ],
    });
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.restoreAllMocks();
  });

  const pronta = () =>
    versoes.next({
      type: 'VERSION_READY',
      currentVersion: { hash: 'a' },
      latestVersion: { hash: 'b' },
    });

  // B16 T1 (CA1)
  it('procura versão nova ao abrir, ao voltar para a aba e de tempos em tempos', () => {
    TestBed.inject(AppUpdateService).iniciar();
    expect(sw.checkForUpdate).toHaveBeenCalledTimes(1);

    document.dispatchEvent(new Event('visibilitychange'));
    expect(sw.checkForUpdate).toHaveBeenCalledTimes(2);

    vi.advanceTimersByTime(15 * 60 * 1000);
    expect(sw.checkForUpdate).toHaveBeenCalledTimes(3);
  });

  // B16 T2 (CA2)
  it('versão pronta com o app em uso: avisa e só recarrega ao clicar em Atualizar', () => {
    TestBed.inject(AppUpdateService).iniciar();
    pronta();

    expect(snackBar.open).toHaveBeenCalledWith('Nova versão do app disponível.', 'Atualizar');
    expect(recarregar).not.toHaveBeenCalled();
    acao.next();
    expect(recarregar).toHaveBeenCalledTimes(1);
  });

  // B16 T2 (CA2)
  it('versão pronta com a aba em segundo plano: recarrega sozinho', () => {
    TestBed.inject(AppUpdateService).iniciar();
    visibilidade = 'hidden';
    pronta();

    expect(recarregar).toHaveBeenCalledTimes(1);
    expect(snackBar.open).not.toHaveBeenCalled();
  });

  it('sem service worker (desenvolvimento) não faz nada', () => {
    sw.isEnabled = false;
    TestBed.inject(AppUpdateService).iniciar();
    expect(sw.checkForUpdate).not.toHaveBeenCalled();
  });
});
