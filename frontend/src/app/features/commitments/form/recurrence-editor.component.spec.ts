import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormBuilder } from '@angular/forms';
import { createRecurrenceGroup, fromRecurrenceRule, RecurrenceEditorComponent, toRecurrenceRule } from './recurrence-editor.component';

@Component({
  imports: [RecurrenceEditorComponent],
  template: `<app-recurrence-editor [group]="group" />`,
})
class HostComponent {
  readonly group = createRecurrenceGroup(new FormBuilder());
}

describe('RecurrenceEditorComponent', () => {
  let fixture: ComponentFixture<HostComponent>;
  let element: HTMLElement;
  let group: HostComponent['group'];

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [HostComponent] }).compileComponents();
    fixture = TestBed.createComponent(HostComponent);
    element = fixture.nativeElement;
    group = fixture.componentInstance.group;
    await fixture.whenStable();
  });

  const toggleDia = (rotulo: string) =>
    Array.from(element.querySelectorAll<HTMLButtonElement>('.dias button')).find((b) => b.textContent!.trim() === rotulo)!;

  // C09 T1 (CA1)
  it('semanal exige ao menos um dia da semana', async () => {
    group.controls.freq.setValue('WEEKLY');
    await fixture.whenStable();
    expect(group.invalid).toBe(true);
    expect(element.textContent).toContain('Escolha pelo menos um dia');

    toggleDia('Seg').click();
    await fixture.whenStable();

    expect(group.controls.byWeekDays.value).toEqual(['MON']);
    expect(group.valid).toBe(true);
  });

  it('diária não mostra nem exige dias da semana', async () => {
    group.controls.freq.setValue('DAILY');
    await fixture.whenStable();
    expect(element.querySelector('.dias')).toBeNull();
    expect(group.valid).toBe(true);
  });

  it('fim por data exige a data; fim por quantidade exige 1 a 1000', () => {
    group.controls.freq.setValue('DAILY');
    group.controls.endType.setValue('until');
    expect(group.invalid).toBe(true);
    group.controls.until.setValue('2026-12-31');
    expect(group.valid).toBe(true);

    group.controls.endType.setValue('count');
    group.controls.count.setValue(0);
    expect(group.invalid).toBe(true);
    group.controls.count.setValue(10);
    expect(group.valid).toBe(true);
  });

  it('converte de e para o formato da API', () => {
    const regra = { freq: 'WEEKLY' as const, interval: 2, byWeekDays: ['MON' as const, 'WED' as const], count: 8 };
    group.setValue(fromRecurrenceRule(regra));
    expect(toRecurrenceRule(group.getRawValue())).toEqual({ freq: 'WEEKLY', interval: 2, byWeekDays: ['MON', 'WED'], count: 8 });
  });
});
