-- Cronômetro opcional das tarefas diárias (T07): minutos a cumprir; ao fim, a tarefa é marcada como feita.
alter table daily_tasks add column timer_minutes integer check (timer_minutes between 1 and 240);
