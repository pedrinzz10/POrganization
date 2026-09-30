// Produção (ng build). Só valores públicos; os endereços reais entram na B11 (deploy).
export const environment = {
  production: true,
  apiUrl: 'https://<api-no-render>.onrender.com/api',
  supabaseUrl: 'https://<ref-do-projeto>.supabase.co',
  supabaseKey: '<sb_publishable_...>',
};
