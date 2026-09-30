import { registerLocaleData } from '@angular/common';
import localePt from '@angular/common/locales/pt';
import { DEFAULT_CURRENCY_CODE, EnvironmentProviders, LOCALE_ID, makeEnvironmentProviders } from '@angular/core';

/**
 * Deixa o app inteiro em pt-BR: os pipes de data, número e moeda passam a formatar
 * como no Brasil ("R$ 1.234,50", "sexta-feira, 2 outubro") sem repetir o locale em cada uso.
 */
export function provideLocale(): EnvironmentProviders {
  registerLocaleData(localePt, 'pt-BR');
  return makeEnvironmentProviders([
    { provide: LOCALE_ID, useValue: 'pt-BR' },
    { provide: DEFAULT_CURRENCY_CODE, useValue: 'BRL' },
  ]);
}
