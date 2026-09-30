import { Directive, ElementRef, forwardRef, inject } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';

/**
 * Campo de dinheiro como máquina registradora: cada dígito entra pela direita dos centavos.
 * Digitar 1, 2, 3, 4, 5, 6 mostra "R$ 1.234,56" e entrega "1234.56" ao formulário (string, como a
 * API). Campo vazio entrega null.
 *
 * É um ControlValueAccessor: com ele, `<input matInput appMoneyInput formControlName="amount">`
 * funciona como qualquer outro campo (valor, validação, disabled), sem o form saber da máscara.
 */
@Directive({
  selector: 'input[appMoneyInput]',
  host: {
    inputmode: 'numeric',
    autocomplete: 'off',
    '(input)': 'onInput()',
    '(blur)': 'onTouched()',
  },
  providers: [{ provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => MoneyInputDirective), multi: true }],
})
export class MoneyInputDirective implements ControlValueAccessor {
  private readonly input = inject<ElementRef<HTMLInputElement>>(ElementRef).nativeElement;

  private onChange: (value: string | null) => void = () => {};
  protected onTouched: () => void = () => {};

  writeValue(value: string | number | null | undefined): void {
    this.input.value = value === null || value === undefined || value === '' ? '' : formatBrl(toCents(String(value)));
  }

  registerOnChange(fn: (value: string | null) => void): void {
    this.onChange = fn;
  }

  registerOnTouched(fn: () => void): void {
    this.onTouched = fn;
  }

  setDisabledState(disabled: boolean): void {
    this.input.disabled = disabled;
  }

  protected onInput(): void {
    // Até 14 dígitos (o backend aceita 12 inteiros + 2 decimais)
    const digits = this.input.value.replace(/\D/g, '').replace(/^0+/, '').slice(0, 14);
    if (!digits) {
      this.input.value = '';
      this.onChange(null);
      return;
    }
    const cents = BigInt(digits);
    this.input.value = formatBrl(cents);
    this.input.setSelectionRange(this.input.value.length, this.input.value.length);
    this.onChange(centsToDecimal(cents));
  }
}

/** "1234.56" ou "-10.5" → centavos. */
export function toCents(value: string): bigint {
  const negative = value.trim().startsWith('-');
  const [inteiro, fracao = ''] = value.replace('-', '').trim().split('.');
  const cents = BigInt(inteiro || '0') * 100n + BigInt((fracao + '00').slice(0, 2));
  return negative ? -cents : cents;
}

/** Centavos → "1234.56". */
export function centsToDecimal(cents: bigint): string {
  const negative = cents < 0n;
  const abs = negative ? -cents : cents;
  return `${negative ? '-' : ''}${abs / 100n}.${(abs % 100n).toString().padStart(2, '0')}`;
}

/** Centavos → "R$ 1.234,56", com espaço comum (Intl usaria espaço não separável). */
export function formatBrl(cents: bigint): string {
  const negative = cents < 0n;
  const abs = negative ? -cents : cents;
  const inteiro = (abs / 100n).toString().replace(/\B(?=(\d{3})+(?!\d))/g, '.');
  return `${negative ? '-' : ''}R$ ${inteiro},${(abs % 100n).toString().padStart(2, '0')}`;
}
