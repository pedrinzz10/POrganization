import { computed, DestroyRef, inject, Injectable, signal } from '@angular/core';
import { Router } from '@angular/router';
import type { AuthError, Session } from '@supabase/supabase-js';
import { SUPABASE_CLIENT } from './supabase.client';

// Mensagens em português para os códigos de erro mais comuns do Supabase Auth
const MENSAGENS: Record<string, string> = {
  invalid_credentials: 'E-mail ou senha incorretos.',
  email_not_confirmed: 'Confirme seu e-mail pelo link que enviamos antes de entrar.',
  user_already_exists: 'Já existe uma conta com este e-mail.',
  email_exists: 'Já existe uma conta com este e-mail.',
  weak_password: 'Senha fraca. Use pelo menos 6 caracteres, misturando letras e números.',
  over_request_rate_limit: 'Muitas tentativas. Aguarde um pouco e tente de novo.',
  over_email_send_rate_limit: 'Muitos e-mails enviados. Aguarde um pouco e tente de novo.',
};

function traduzir(error: AuthError): Error {
  return new Error((error.code && MENSAGENS[error.code]) || error.message);
}

/**
 * Estado do login do app inteiro. É um singleton (providedIn: 'root'): qualquer componente
 * que lê session() ou isAuthenticated() atualiza sozinho quando o login muda.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly supabase = inject(SUPABASE_CLIENT);
  private readonly router = inject(Router);

  private readonly _session = signal<Session | null>(null);

  readonly session = this._session.asReadonly();
  readonly user = computed(() => this._session()?.user ?? null);
  readonly isAuthenticated = computed(() => this._session() !== null);
  readonly accessToken = computed(() => this._session()?.access_token ?? null);

  /** Resolve quando a sessão salva (localStorage) já foi lida; o guard espera por isto. */
  readonly ready: Promise<void>;

  constructor() {
    const { data } = this.supabase.auth.onAuthStateChange((_event, session) => this._session.set(session));
    inject(DestroyRef).onDestroy(() => data.subscription.unsubscribe());

    this.ready = this.supabase.auth.getSession().then(({ data: { session } }) => {
      this._session.set(session);
    });
  }

  /**
   * Token válido para chamar a API. O getSession() do Supabase renova o token se ele já
   * venceu (ex.: notebook hibernado), em vez de mandar um token expirado e tomar 401.
   */
  async freshAccessToken(): Promise<string | null> {
    const {
      data: { session },
    } = await this.supabase.auth.getSession();
    if (session?.access_token !== this._session()?.access_token) {
      this._session.set(session);
    }
    return session?.access_token ?? null;
  }

  async signIn(email: string, password: string): Promise<void> {
    const { data, error } = await this.supabase.auth.signInWithPassword({ email, password });
    if (error) {
      throw traduzir(error);
    }
    this._session.set(data.session);
  }

  /** Cria a conta. Se o projeto exige confirmação por e-mail, ainda não há sessão. */
  async signUp(email: string, password: string): Promise<{ needsEmailConfirmation: boolean }> {
    const { data, error } = await this.supabase.auth.signUp({ email, password });
    if (error) {
      throw traduzir(error);
    }
    this._session.set(data.session);
    return { needsEmailConfirmation: data.session === null };
  }

  async signOut(): Promise<void> {
    await this.supabase.auth.signOut();
    this._session.set(null);
    await this.router.navigate(['/login']);
  }
}
