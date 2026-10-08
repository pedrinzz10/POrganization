-- Sessão de aula de uma matéria com aulas definidas (E16): qual aula da lista está sendo estudada.
-- Ao terminar, planned_lessons.lesson_id passa a apontar para a aula criada.
alter table study_sessions
    add column planned_lesson_id uuid references planned_lessons (id) on delete set null;
