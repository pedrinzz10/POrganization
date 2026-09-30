import { Pipe, PipeTransform } from '@angular/core';
import { formatBrl, toCents } from './money-input.directive';

/**
 * "1234.56" → "R$ 1.234,56". Trabalha em centavos (sem float) e com espaço comum, igual ao
 * que o MoneyInput mostra. `signed` põe "+" nos positivos (extrato).
 */
@Pipe({ name: 'brl' })
export class BrlPipe implements PipeTransform {
  transform(value: string | number | null | undefined, signed = false): string {
    if (value === null || value === undefined || value === '') {
      return '';
    }
    const cents = toCents(String(value));
    return (signed && cents > 0n ? '+' : '') + formatBrl(cents);
  }
}
