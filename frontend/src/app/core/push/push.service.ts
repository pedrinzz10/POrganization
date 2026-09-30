import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { SwPush } from '@angular/service-worker';
import { firstValueFrom, map, take } from 'rxjs';
import { environment } from '../../../environments/environment';

/**
 * Notificações push deste navegador. O service worker do Angular (SwPush) pede a permissão e cria
 * a assinatura com a chave VAPID do servidor; a assinatura vai para a API, que passa a mandar os
 * lembretes para cá mesmo com o app fechado. Clicar na notificação abre o app na tela Hoje
 * (o payload traz notification.data.onActionClick, tratado pelo próprio ngsw-worker).
 */
@Injectable({ providedIn: 'root' })
export class PushService {
  private readonly swPush = inject(SwPush);
  private readonly http = inject(HttpClient);
  private readonly api = `${environment.apiUrl}/push`;

  /** Falso no ng serve (sem service worker) e em navegadores sem suporte a push. */
  readonly supported = this.swPush.isEnabled;

  /** Este navegador já está inscrito? */
  readonly subscribed = toSignal(this.swPush.subscription.pipe(map((s) => s !== null)), { initialValue: false });

  /** Pede permissão, assina com a chave VAPID do servidor e salva a assinatura na API. */
  async enable(): Promise<void> {
    const { publicKey } = await firstValueFrom(this.http.get<{ publicKey: string | null }>(`${this.api}/vapid-public-key`));
    if (!publicKey) {
      throw new Error('As notificações ainda não foram configuradas no servidor.');
    }
    const subscription = await this.swPush.requestSubscription({ serverPublicKey: publicKey });
    const { endpoint, keys } = subscription.toJSON();
    await firstValueFrom(this.http.post<void>(`${this.api}/subscriptions`, { endpoint, keys }));
  }

  /** Remove a assinatura na API e no navegador. */
  async disable(): Promise<void> {
    const subscription = await firstValueFrom(this.swPush.subscription.pipe(take(1)));
    if (!subscription) {
      return;
    }
    await firstValueFrom(this.http.delete<void>(`${this.api}/subscriptions`, { body: { endpoint: subscription.endpoint } }));
    await this.swPush.unsubscribe();
  }
}
