// Desenvolvimento local (ng serve). Só valores públicos: nada aqui é segredo.
export const environment = {
  production: false,
  apiUrl: 'http://localhost:8080/api',
  // Supabase > Project Settings > API: URL do projeto e a publishable key (sb_publishable_...).
  // Nunca a secret key (sb_secret_...), que o check "segredos" bloqueia.
  supabaseUrl: 'https://seu-projeto.supabase.co',
  supabaseKey: 'sb_publishable_substitua-pela-sua-chave',
};
