import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { SwPush } from '@angular/service-worker';
import { BehaviorSubject } from 'rxjs';
import { environment } from '../../../environments/environment';
import { PushService } from './push.service';

const API = `${environment.apiUrl}/push`;

// Deixa as promessas da cadeia andarem até a próxima chamada HTTP
const tick = () => new Promise((resolve) => setTimeout(resolve));

const ASSINATURA = {
  endpoint: 'https://fcm.googleapis.com/fcm/send/abc',
  toJSON: () => ({ endpoint: 'https://fcm.googleapis.com/fcm/send/abc', keys: { p256dh: 'chave-p256dh', auth: 'segredo-auth' } }),
} as unknown as PushSubscription;

describe('PushService', () => {
  let service: PushService;
  let httpMock: HttpTestingController;
  let assinatura$: BehaviorSubject<PushSubscription | null>;
  let swPush: { isEnabled: boolean; subscription: BehaviorSubject<PushSubscription | null>; requestSubscription: ReturnType<typeof vi.fn>; unsubscribe: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    assinatura$ = new BehaviorSubject<PushSubscription | null>(null);
    swPush = {
      isEnabled: true,
      subscription: assinatura$,
      requestSubscription: vi.fn(async () => {
        assinatura$.next(ASSINATURA);
        return ASSINATURA;
      }),
      unsubscribe: vi.fn(async () => assinatura$.next(null)),
    };
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), { provide: SwPush, useValue: swPush }],
    });
    service = TestBed.inject(PushService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  // I03 T1 (CA1)
  it('enable() assina com a chave VAPID do servidor e faz POST com endpoint e keys', async () => {
    const ativando = service.enable();
    httpMock.expectOne(`${API}/vapid-public-key`).flush({ publicKey: 'BChaveVapidPublica' });
    await tick();

    expect(swPush.requestSubscription).toHaveBeenCalledWith({ serverPublicKey: 'BChaveVapidPublica' });
    const post = httpMock.expectOne(`${API}/subscriptions`);
    expect(post.request.method).toBe('POST');
    expect(post.request.body).toEqual({
      endpoint: 'https://fcm.googleapis.com/fcm/send/abc',
      keys: { p256dh: 'chave-p256dh', auth: 'segredo-auth' },
    });
    post.flush(null, { status: 204, statusText: 'No Content' });
    await ativando;

    expect(service.subscribed()).toBe(true);
  });

  // I03 T1 (CA1)
  it('disable() faz DELETE da assinatura na API e cancela no navegador', async () => {
    assinatura$.next(ASSINATURA);

    const desativando = service.disable();
    await tick();
    const del = httpMock.expectOne(`${API}/subscriptions`);
    expect(del.request.method).toBe('DELETE');
    expect(del.request.body).toEqual({ endpoint: 'https://fcm.googleapis.com/fcm/send/abc' });
    del.flush(null, { status: 204, statusText: 'No Content' });
    await desativando;

    expect(swPush.unsubscribe).toHaveBeenCalled();
    expect(service.subscribed()).toBe(false);
  });

  it('servidor sem chave VAPID: enable() explica e não pede permissão', async () => {
    const ativando = service.enable();
    httpMock.expectOne(`${API}/vapid-public-key`).flush({ publicKey: null });

    await expect(ativando).rejects.toThrow('não foram configuradas');
    expect(swPush.requestSubscription).not.toHaveBeenCalled();
  });

  it('disable() sem assinatura não chama a API', async () => {
    await service.disable();
    httpMock.expectNone(`${API}/subscriptions`);
  });
});
