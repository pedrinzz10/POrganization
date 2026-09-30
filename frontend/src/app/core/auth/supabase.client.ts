import { InjectionToken } from '@angular/core';
import { createClient, SupabaseClient } from '@supabase/supabase-js';
import { environment } from '../../../environments/environment';

/**
 * Cliente do Supabase como token de injeção: o app inteiro usa uma única instância,
 * e os testes trocam por um fake sem tocar na rede.
 * A sessão fica no localStorage (persistSession), por isso recarregar a página mantém o login.
 */
export const SUPABASE_CLIENT = new InjectionToken<SupabaseClient>('SUPABASE_CLIENT', {
  providedIn: 'root',
  factory: () =>
    createClient(environment.supabaseUrl, environment.supabaseKey, {
      auth: { persistSession: true, autoRefreshToken: true, detectSessionInUrl: true },
    }),
});
