import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { formatBrl, MoneyInputDirective } from './money-input.directive';

@Component({
  imports: [ReactiveFormsModule, MoneyInputDirective],
  template: `<input appMoneyInput [formControl]="control" />`,
})
class HostComponent {
  readonly control = new FormControl<string | null>(null);
}

describe('MoneyInputDirective', () => {
  function montar() {
    const fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
    const input = fixture.nativeElement.querySelector('input') as HTMLInputElement;
    return { fixture, input, control: fixture.componentInstance.control };
  }

  function digitar(input: HTMLInputElement, valor: string) {
    input.value = valor;
    input.dispatchEvent(new Event('input'));
  }

  // F12 T1 (CA1)
  it('digitar 123456 mostra "R$ 1.234,56" e entrega "1234.56"', () => {
    const { input, control } = montar();

    digitar(input, '123456');

    expect(input.value).toBe('R$ 1.234,56');
    expect(control.value).toBe('1234.56');
  });

  it('cada dígito entra pela direita, como numa máquina registradora', () => {
    const { input, control } = montar();

    digitar(input, '5');
    expect(input.value).toBe('R$ 0,05');
    digitar(input, input.value + '0');
    expect(input.value).toBe('R$ 0,50');
    digitar(input, input.value + '0');
    expect(input.value).toBe('R$ 5,00');
    expect(control.value).toBe('5.00');
  });

  it('apagar tira o último dígito e apagar tudo entrega null', () => {
    const { input, control } = montar();
    digitar(input, '1234');

    digitar(input, 'R$ 12,3');
    expect(input.value).toBe('R$ 1,23');
    expect(control.value).toBe('1.23');

    digitar(input, '');
    expect(input.value).toBe('');
    expect(control.value).toBeNull();
  });

  it('valor vindo do formulário aparece formatado', () => {
    const { fixture, input, control } = montar();

    control.setValue('1500.5');
    fixture.detectChanges();

    expect(input.value).toBe('R$ 1.500,50');
  });

  it('formatBrl agrupa milhares e trata negativos', () => {
    expect(formatBrl(123456789n)).toBe('R$ 1.234.567,89');
    expect(formatBrl(-5000n)).toBe('-R$ 50,00');
    expect(formatBrl(0n)).toBe('R$ 0,00');
  });
});
