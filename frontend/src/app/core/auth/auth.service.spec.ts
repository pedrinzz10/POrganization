import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import type { AuthChangeEvent, Session } from '@supabase/supabase-js';
import { AuthService } from './auth.service';
import { SUPABASE_CLIENT } from './supabase.client';

const sessao = { access_token: 'token-de-teste', user: { id: 'user-1', email: 'pedro@teste.com' } } as Session;

function fakeSupabase(sessaoSalva: Session | null) {
  let listener: ((event: AuthChangeEvent, session: Session | null) => void) | undefined;
  const auth = {
    getSession: vi.fn().mockResolvedValue({ data: { session: sessaoSalva }, error: null }),
    onAuthStateChange: vi.fn((callback) => {
      listener = callback;
      return { data: { subscription: { unsubscribe: vi.fn() } } };
    }),
    signInWithPassword: vi.fn(),
    signUp: vi.fn(),
    signOut: vi.fn().mockResolvedValue({ error: null }),
  };
  return { client: { auth }, auth, emit: (e: AuthChangeEvent, s: Session | null) => listener?.(e, s) };
}

function setup(sessaoSalva: Session | null) {
  const supabase = fakeSupabase(sessaoSalva);
  TestBed.configureTestingModule({ providers: [{ provide: SUPABASE_CLIENT, useValue: supabase.client }] });
  const router = TestBed.inject(Router);
  const navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);
  return { service: TestBed.inject(AuthService), supabase, navigate };
}

describe('AuthService', () => {
  // B07 T3 (CA3)
  it('recupera a sessão salva ao iniciar (recarregar a página mantém o login)', async () => {
    const { service } = setup(sessao);
    await service.ready;
    expect(service.session()).not.toBeNull();
    expect(service.user()?.email).toBe('pedro@teste.com');
    expect(service.accessToken()).toBe('token-de-teste');
  });

  // B07 T3 (CA3)
  it('signOut limpa a sessão e volta para /login', async () => {
    const { service, supabase, navigate } = setup(sessao);
    await service.ready;

    await service.signOut();

    expect(supabase.auth.signOut).toHaveBeenCalled();
    expect(service.session()).toBeNull();
    expect(navigate).toHaveBeenCalledWith(['/login']);
  });

  it('sem sessão salva, começa deslogado', async () => {
    const { service } = setup(null);
    await service.ready;
    expect(service.isAuthenticated()).toBe(false);
  });

  it('acompanha mudanças do Supabase (renovação de token, logout em outra aba)', async () => {
    const { service, supabase } = setup(null);
    await service.ready;

    supabase.emit('SIGNED_IN', sessao);
    expect(service.isAuthenticated()).toBe(true);

    supabase.emit('SIGNED_OUT', null);
    expect(service.isAuthenticated()).toBe(false);
  });

  it('signIn guarda a sessão', async () => {
    const { service, supabase } = setup(null);
    supabase.auth.signInWithPassword.mockResolvedValue({ data: { session: sessao, user: sessao.user }, error: null });

    await service.signIn('pedro@teste.com', 'segredo123');

    expect(supabase.auth.signInWithPassword).toHaveBeenCalledWith({ email: 'pedro@teste.com', password: 'segredo123' });
    expect(service.session()).toBe(sessao);
  });

  it('signIn traduz o erro de credenciais do Supabase', async () => {
    const { service, supabase } = setup(null);
    supabase.auth.signInWithPassword.mockResolvedValue({
      data: { session: null, user: null },
      error: { code: 'invalid_credentials', message: 'Invalid login credentials' },
    });

    await expect(service.signIn('pedro@teste.com', 'errada1')).rejects.toThrow('E-mail ou senha incorretos.');
  });

  it('signUp informa quando o e-mail precisa ser confirmado', async () => {
    const { service, supabase } = setup(null);
    supabase.auth.signUp.mockResolvedValue({ data: { session: null, user: { id: 'novo' } }, error: null });

    const resultado = await service.signUp('novo@teste.com', 'segredo123');

    expect(resultado).toEqual({ needsEmailConfirmation: true });
    expect(service.session()).toBeNull();
  });
});
