import { Page } from '@playwright/test';

// Mesma URL do environment.development.ts: o supabase-js guarda a sessão em
// localStorage["sb-<primeiro pedaço do host>-auth-token"]
const SUPABASE_URL = 'https://seu-projeto.supabase.co';
const STORAGE_KEY = `sb-${new URL(SUPABASE_URL).hostname.split('.')[0]}-auth-token`;

export const USUARIO_TESTE = { id: '00000000-0000-4000-8000-000000000001', email: 'pedro@teste.com' };

/**
 * Faz o app abrir "logado" sem Supabase: planta uma sessão válida por 1 hora no localStorage
 * e bloqueia qualquer chamada de rede ao Supabase, para o teste nunca depender dele.
 */
export async function entrarComSessaoFalsa(page: Page): Promise<void> {
  await page.route(`${SUPABASE_URL}/**`, (route) => route.abort());
  const agora = Math.floor(Date.now() / 1000);
  const sessao = {
    access_token: 'token-e2e',
    token_type: 'bearer',
    expires_in: 3600,
    expires_at: agora + 3600,
    refresh_token: 'refresh-e2e',
    user: { ...USUARIO_TESTE, aud: 'authenticated', role: 'authenticated', app_metadata: {}, user_metadata: {} },
  };
  await page.addInitScript(
    ([chave, valor]) => window.localStorage.setItem(chave, valor),
    [STORAGE_KEY, JSON.stringify(sessao)],
  );
}
