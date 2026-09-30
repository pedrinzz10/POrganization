// Produção (build da Vercel). Só valores públicos; nada aqui é segredo.
// Troque os placeholders pelos endereços reais depois de criar os serviços (ver README, seção Deploy).
export const environment = {
  production: true,
  // URL do serviço porganization-api no Render
  apiUrl: 'https://porganization-api.onrender.com/api',
  // Supabase > Project Settings > API: URL do projeto e a publishable key (sb_publishable_...)
  supabaseUrl: 'https://seu-projeto.supabase.co',
  supabaseKey: 'sb_publishable_substitua-pela-sua-chave',
};
