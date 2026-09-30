import { addDays, daysOf, monthGrid, monthGridRange, nextWeek, previousWeek, shiftMonth, toIsoDate, weekRange, yearRange } from './date-range.util';

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

  // C08 T1 (CA1)
  it('a grade de outubro/2026 começa em 28/09 e tem 42 células', () => {
    const grade = monthGrid(2026, 10);
    expect(grade).toHaveLength(42);
    expect(grade[0]).toBe('2026-09-28');
    expect(grade[41]).toBe('2026-11-08');
    expect(monthGridRange(2026, 10)).toEqual({ from: '2026-09-28', to: '2026-11-08' });
  });

  it('mês que começa na segunda não repete a semana anterior', () => {
    expect(monthGrid(2027, 3)[0]).toBe('2027-03-01');
  });

  it('ano inteiro e troca de mês atravessando o ano', () => {
    expect(yearRange(2026)).toEqual({ from: '2026-01-01', to: '2026-12-31' });
    expect(shiftMonth({ year: 2026, month: 12 }, 1)).toEqual({ year: 2027, month: 1 });
    expect(shiftMonth({ year: 2026, month: 1 }, -1)).toEqual({ year: 2025, month: 12 });
  });
});
