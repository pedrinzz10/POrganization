import { addDays, daysOf, nextWeek, previousWeek, toIsoDate, weekRange } from './date-range.util';

describe('date-range.util', () => {
  // C07 T3 (CA3)
  it('a semana começa na segunda e "próxima" traz a semana seguinte', () => {
    const semana = weekRange('2026-10-01'); // quinta-feira
    expect(semana).toEqual({ from: '2026-09-28', to: '2026-10-04' });
    expect(nextWeek(semana)).toEqual({ from: '2026-10-05', to: '2026-10-11' });
  });

  it('domingo pertence à semana que começou na segunda anterior', () => {
    expect(weekRange('2026-10-04')).toEqual({ from: '2026-09-28', to: '2026-10-04' });
    expect(weekRange('2026-09-28')).toEqual({ from: '2026-09-28', to: '2026-10-04' });
  });

  it('semana anterior', () => {
    expect(previousWeek({ from: '2026-10-05', to: '2026-10-11' })).toEqual({ from: '2026-09-28', to: '2026-10-04' });
  });

  it('soma dias atravessando mês, ano e horário de verão', () => {
    expect(addDays('2026-09-30', 1)).toBe('2026-10-01');
    expect(addDays('2026-12-31', 1)).toBe('2027-01-01');
    expect(addDays('2026-03-01', -1)).toBe('2026-02-28');
    // mudança de horário (ex.: América/Santiago em setembro) não pula nem repete dias
    expect(addDays('2026-09-05', 2)).toBe('2026-09-07');
  });

  it('lista os dias de um intervalo', () => {
    expect(daysOf({ from: '2026-09-28', to: '2026-10-04' })).toEqual([
      '2026-09-28', '2026-09-29', '2026-09-30', '2026-10-01', '2026-10-02', '2026-10-03', '2026-10-04',
    ]);
  });

  it('toIsoDate usa a data local, não UTC', () => {
    expect(toIsoDate(new Date(2026, 9, 1, 23, 30))).toBe('2026-10-01');
  });
});
