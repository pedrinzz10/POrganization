import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Occurrence } from '../data/commitment.model';
import { MonthViewComponent } from './month-view.component';

function ocorrencia(titulo: string, data: string, hora: string): Occurrence {
  return {
    commitmentId: 'id-' + titulo,
    occurrenceDate: data,
    title: titulo,
    startTime: hora,
    endTime: null,
    allDay: false,
    done: false,
    recurring: false,
    description: null,
    location: null,
  };
}

describe('MonthViewComponent', () => {
  let fixture: ComponentFixture<MonthViewComponent>;
  let element: HTMLElement;

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [MonthViewComponent] }).compileComponents();
    fixture = TestBed.createComponent(MonthViewComponent);
    element = fixture.nativeElement;
    fixture.componentRef.setInput('year', 2026);
    fixture.componentRef.setInput('month', 10);
  });

  const celula = (data: string) => element.querySelector<HTMLElement>(`[data-date="${data}"]`)!;

  it('desenha 42 células começando em 28/09', async () => {
    fixture.componentRef.setInput('occurrences', []);
    await fixture.whenStable();

    const celulas = element.querySelectorAll('[data-date]');
    expect(celulas).toHaveLength(42);
    expect(celulas[0].getAttribute('data-date')).toBe('2026-09-28');
    expect(celula('2026-09-28').classList).toContain('fora-do-mes');
  });

  // C08 T2 (CA2)
  it('dia com mais de 3 compromissos mostra 3 títulos e +N', async () => {
    fixture.componentRef.setInput('occurrences', [
      ocorrencia('E', '2026-10-15', '18:00'),
      ocorrencia('A', '2026-10-15', '08:00'),
      ocorrencia('B', '2026-10-15', '09:00'),
      ocorrencia('C', '2026-10-15', '10:00'),
      ocorrencia('D', '2026-10-15', '11:00'),
    ]);
    await fixture.whenStable();

    const titulos = Array.from(celula('2026-10-15').querySelectorAll('.cell__item')).map((i) => i.textContent!.trim());
    expect(titulos).toEqual(['A', 'B', 'C']);
    expect(celula('2026-10-15').textContent).toContain('+2');
  });

  it('clicar num dia avisa qual dia abrir', async () => {
    fixture.componentRef.setInput('occurrences', []);
    await fixture.whenStable();
    const escolhidos: string[] = [];
    fixture.componentInstance.daySelected.subscribe((d) => escolhidos.push(d));

    celula('2026-10-20').click();

    expect(escolhidos).toEqual(['2026-10-20']);
  });
});
