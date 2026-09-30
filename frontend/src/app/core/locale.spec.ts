import { CurrencyPipe, DatePipe, DecimalPipe } from '@angular/common';
import { TestBed } from '@angular/core/testing';
import { provideLocale } from './locale';

// O Intl usa espaço não separável (U+00A0) entre "R$" e o valor; normaliza para comparar
const normalize = (value: string | null) => value?.replace(/ /g, ' ');

// B06 T2 (CA2)
describe('provideLocale', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideLocale(), CurrencyPipe, DecimalPipe, DatePipe],
    });
  });

  it('formata moeda em reais no padrão brasileiro', () => {
    const currency = TestBed.inject(CurrencyPipe);
    expect(normalize(currency.transform(1234.5, 'BRL'))).toBe('R$ 1.234,50');
  });

  it('usa BRL como moeda padrão', () => {
    const currency = TestBed.inject(CurrencyPipe);
    expect(normalize(currency.transform(1234.5))).toBe('R$ 1.234,50');
  });

  it('formata números e datas em pt-BR', () => {
    expect(TestBed.inject(DecimalPipe).transform(1234.5, '1.2-2')).toBe('1.234,50');
    expect(TestBed.inject(DatePipe).transform('2026-10-02T12:00:00', 'EEEE, d MMMM')).toBe('sexta-feira, 2 outubro');
  });
});
