import { BudgetLevelPipe } from './budget-level.pipe';

describe('BudgetLevelPipe', () => {
  const pipe = new BudgetLevelPipe();

  // F14 T1 (CA1)
  it('transforma o nível na classe da cor da barra', () => {
    expect(pipe.transform('OK')).toBe('budget--ok');
    expect(pipe.transform('ATENCAO')).toBe('budget--warn');
    expect(pipe.transform('ESTOURADO')).toBe('budget--over');
  });

  it('com "label" devolve o texto', () => {
    expect(pipe.transform('OK', 'label')).toBe('Dentro do orçamento');
    expect(pipe.transform('ATENCAO', 'label')).toBe('Atenção');
    expect(pipe.transform('ESTOURADO', 'label')).toBe('Estourado');
  });
});
