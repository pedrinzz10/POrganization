import { saudacaoPara } from './today.page';

describe('saudacaoPara', () => {
  it('muda com o horário', () => {
    expect(saudacaoPara(5)).toBe('Bom dia');
    expect(saudacaoPara(11)).toBe('Bom dia');
    expect(saudacaoPara(12)).toBe('Boa tarde');
    expect(saudacaoPara(17)).toBe('Boa tarde');
    expect(saudacaoPara(18)).toBe('Boa noite');
    expect(saudacaoPara(2)).toBe('Boa noite');
  });
});
