// Gera reference-ts-fsrs.json, o fixture do FsrsReferenceTest (spec E05).
// Uso (numa pasta qualquer):  npm install ts-fsrs@5.4.2 && node generate-reference.mjs > reference-ts-fsrs.json
import { createEmptyCard, fsrs, generatorParameters, Rating, State } from 'ts-fsrs';

const params = generatorParameters({
  request_retention: 0.9,
  maximum_interval: 365,
  enable_fuzz: false,
  enable_short_term: false, // agendador em dias: revisões diárias, sem passos de minutos
});
const scheduler = fsrs(params);
const day = (d) => d.toISOString().slice(0, 10);
const addDays = (d, n) => new Date(d.getTime() + n * 86400000);

// grades: nota de cada revisão; delays: dias depois do vencimento em que a revisão acontece
function run(name, grades, delays) {
  let card = createEmptyCard(new Date('2026-10-01T12:00:00Z'));
  let now = new Date('2026-10-01T12:00:00Z');
  const steps = [];
  grades.forEach((grade, i) => {
    const { card: next } = scheduler.next(card, now, Rating[grade]);
    steps.push({
      reviewDate: day(now),
      grade,
      state: State[next.state],
      stability: next.stability,
      difficulty: next.difficulty,
      scheduledDays: next.scheduled_days,
      due: day(next.due),
      reps: next.reps,
    });
    card = next;
    now = addDays(next.due, delays[i] ?? 0);
  });
  return { name, steps };
}

console.log(JSON.stringify({
  generator: 'ts-fsrs@5.4.2',
  parameters: { w: params.w, requestRetention: 0.9, maximumInterval: 365, enableShortTerm: false, enableFuzz: false },
  sequences: [
    run('spec E05: OK, OK, DIFICIL, FACIL nas datas devidas', ['Good', 'Good', 'Hard', 'Easy'], [0, 0, 0, 0]),
    run('revisoes atrasadas', ['Good', 'Hard', 'Good', 'Easy', 'Good'], [3, 10, 0, 20, 0]),
    run('so faceis', ['Easy', 'Easy', 'Easy', 'Easy', 'Easy', 'Easy'], [0, 0, 0, 0, 0, 0]),
  ],
}, null, 2));
