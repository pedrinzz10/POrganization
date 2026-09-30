-- Lembrete opcional das tarefas diárias (T05): avisa no horário se a tarefa, devida hoje, não foi feita.
alter table daily_tasks add column reminder_time time;
-- dia (no fuso do usuário) do último lembrete enviado: um por tarefa e dia
alter table daily_tasks add column last_reminder_date date;
