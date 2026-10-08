import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { environment } from '../../../../environments/environment';
import {
  addDays,
  monthGridRange,
  today,
  weekRange,
  yearMonthOf,
} from '../../commitments/data/date-range.util';
import { StudyCalendarDay } from '../data/study.model';
import { StudyAgendaPage } from './study-agenda.page';

const API = `${environment.apiUrl}/study/calendar`;
const SEMANA_API = `${environment.apiUrl}/study/week`;
const tick = () => new Promise((resolve) => setTimeout(resolve));

describe('StudyAgendaPage', () => {
  let fixture: ComponentFixture<StudyAgendaPage>;
  let element: HTMLElement;
  let httpMock: HttpTestingController;
  const hoje = today();

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [StudyAgendaPage],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    httpMock = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(StudyAgendaPage);
    element = fixture.nativeElement;
    await tick();
  });

  afterEach(() => httpMock.verify());

  function responder(
    from: string,
    to: string,
    preencher: (d: string) => StudyCalendarDay['items'] = () => [],
    planned = false,
  ) {
    const req = httpMock.expectOne(
      (r) => r.url === API && r.params.get('from') === from && r.params.get('to') === to,
    );
    const dias: StudyCalendarDay[] = [];
    for (let d = from; d <= to; d = addDays(d, 1)) {
      dias.push({ date: d, items: preencher(d), planned });
    }
    req.flush(dias);
  }

  const botao = (texto: string) =>
    Array.from(element.querySelectorAll<HTMLButtonElement>('button')).find(
      (b) => b.textContent!.trim() === texto,
    )!;

  // E12 T3 (CA3)
  it('abre na semana com aula, revisão atrasada e o estudado; Mês e clique no dia levam ao Dia', async () => {
    const semana = weekRange(hoje);
    responder(semana.from, semana.to, (d) =>
      d === hoje
        ? [
            {
              kind: 'DONE',
              subjectId: 'j',
              subjectName: 'Java',
              color: null,
              title: 'Streams',
              minutes: 45,
              sessionType: 'LESSON',
              overdue: false,
              pinned: false,
            },
            {
              kind: 'REVIEW',
              subjectId: 'j',
              subjectName: 'Java',
              color: null,
              title: 'Lambdas',
              minutes: 10,
              sessionType: 'REVIEW',
              overdue: true,
              pinned: false,
            },
            {
              kind: 'LESSON',
              subjectId: 'i',
              subjectName: 'Inglês',
              color: null,
              title: null,
              minutes: 50,
              sessionType: 'LESSON',
              overdue: false,
              pinned: false,
            },
          ]
        : [],
    );
    await fixture.whenStable();

    expect(element.querySelectorAll('.semana__dia')).toHaveLength(7);
    const textoHoje = element.querySelector('.semana__dia--hoje')!.textContent!;
    expect(textoHoje).toContain('✓ Java: Streams');
    expect(textoHoje).toContain('Revisão: Lambdas');
    expect(textoHoje).toContain('atrasada');
    expect(textoHoje).toContain('Aula de Inglês');
    expect(element.querySelector('.semana__dia--hoje .item--atrasada')).not.toBeNull();

    botao('Mês').click();
    await tick();
    const { year, month } = yearMonthOf(hoje);
    const grade = monthGridRange(year, month);
    responder(grade.from, grade.to);
    await fixture.whenStable();
    expect(element.querySelectorAll('.mes__dia')).toHaveLength(42);

    element.querySelector<HTMLButtonElement>('.mes__dia--hoje')!.click();
    await tick();
    responder(hoje, hoje);
    await fixture.whenStable();
    expect(botao('Dia').getAttribute('aria-selected')).toBe('true');
    expect(element.textContent).toContain('Nada para estudar neste dia.');
  });

  it('as setas andam uma semana por vez', async () => {
    const semana = weekRange(hoje);
    responder(semana.from, semana.to);
    await fixture.whenStable();

    element.querySelector<HTMLButtonElement>('[aria-label="Próxima semana"]')!.click();
    await tick();

    responder(addDays(semana.from, 7), addDays(semana.to, 7));
  });

  // E13 T5 (CA2)
  it('mover uma aula muda na hora, chama /moves e recarrega a semana', async () => {
    const semana = weekRange(hoje);
    const aula = {
      kind: 'LESSON' as const,
      subjectId: 'i',
      subjectName: 'Inglês',
      color: null,
      title: null,
      minutes: 50,
      sessionType: 'LESSON' as const,
      overdue: false,
      pinned: false,
    };
    const destino = semana.to > hoje ? semana.to : hoje;
    responder(semana.from, semana.to, (d) => (d === hoje ? [aula] : []));
    await fixture.whenStable();

    const movendo = fixture.componentInstance.moverAula(aula, hoje, destino);
    await fixture.whenStable();
    if (destino !== hoje) {
      // Na tela antes da resposta: saiu de hoje e entrou fixada no destino
      expect(element.querySelector('.semana__dia--hoje')!.textContent).not.toContain('Aula de Inglês');
    }

    const req = httpMock.expectOne(`${API}/moves`);
    expect(req.request.body).toEqual({ subjectId: 'i', from: hoje, to: destino });
    req.flush([]);
    await movendo;
    await tick();
    responder(semana.from, semana.to);
  });

  it('só solta de hoje em diante e na mesma semana', () => {
    const podeSoltar = (fixture.componentInstance as unknown as {
      podeSoltar: (drag: { data: { from: string } }, drop: { data: string }) => boolean;
    }).podeSoltar;
    const proximaSemana = addDays(weekRange(hoje).from, 7);
    const ontem = addDays(hoje, -1);

    expect(podeSoltar({ data: { from: hoje } }, { data: hoje })).toBe(true);
    expect(podeSoltar({ data: { from: hoje } }, { data: proximaSemana })).toBe(false);
    if (weekRange(ontem).from === weekRange(hoje).from) {
      expect(podeSoltar({ data: { from: hoje } }, { data: ontem })).toBe(false);
    }
    httpMock.match(() => true).forEach((r) => r.flush([]));
  });

  const aulaJava = {
    kind: 'LESSON' as const,
    subjectId: 'j',
    subjectName: 'Java',
    color: null,
    title: 'Laços',
    minutes: 50,
    sessionType: 'LESSON' as const,
    overdue: false,
    pinned: false,
    plannedLessonId: 'p2',
  };

  // E15 T6 (CA1)
  it('semana automática: mostra a previsão e "Gerar semana" sorteia e recarrega', async () => {
    const semana = weekRange(hoje);
    responder(semana.from, semana.to, (d) => (d === hoje ? [aulaJava] : []));
    await fixture.whenStable();

    expect(element.textContent).toContain('Previsão automática');
    expect(element.textContent).not.toContain('Plano da semana');
    // Matéria com aulas definidas: a aula mostra qual é
    expect(element.querySelector('.semana__dia--hoje')!.textContent).toContain('Java: Laços');

    void fixture.componentInstance.gerarSemana();
    await tick();
    const req = httpMock.expectOne((r) => r.url === `${SEMANA_API}/generate` && r.method === 'POST');
    expect(req.request.params.get('week')).toBe(semana.from);
    req.flush([]);
    await fixture.whenStable();
    await tick();
    responder(semana.from, semana.to, (d) => (d === hoje ? [aulaJava] : []), true);
    await fixture.whenStable();

    expect(element.textContent).toContain('Plano da semana');
    expect(botao('Voltar ao automático')).toBeTruthy();
  });

  // E15 T6 (CA2)
  it('tirar (×) chama a API com a matéria e o dia; "Voltar ao automático" limpa a semana', async () => {
    const semana = weekRange(hoje);
    responder(semana.from, semana.to, (d) => (d === hoje ? [aulaJava] : []), true);
    await fixture.whenStable();

    expect(element.querySelector('.card__tirar')).toBeTruthy();
    void fixture.componentInstance.tirarAula(aulaJava, hoje);
    await tick();
    const tirar = httpMock.expectOne((r) => r.url === `${SEMANA_API}/slots` && r.method === 'DELETE');
    expect(tirar.request.params.get('subjectId')).toBe('j');
    expect(tirar.request.params.get('day')).toBe(hoje);
    tirar.flush([]);
    await fixture.whenStable();
    await tick();
    responder(semana.from, semana.to, () => [], true);
    await fixture.whenStable();

    void fixture.componentInstance.limparPlano();
    await tick();
    const limpar = httpMock.expectOne((r) => r.url === SEMANA_API && r.method === 'DELETE');
    expect(limpar.request.params.get('week')).toBe(semana.from);
    limpar.flush(null);
    await fixture.whenStable();
    await tick();
    responder(semana.from, semana.to);
  });

  // E15 T6 (CA2)
  it('incluir aula manda a matéria e o dia', async () => {
    const semana = weekRange(hoje);
    responder(semana.from, semana.to);
    await fixture.whenStable();

    const incluindo = fixture.componentInstance.incluirAula('i', 'Inglês', hoje);
    const req = httpMock.expectOne((r) => r.url === `${SEMANA_API}/slots` && r.method === 'POST');
    expect(req.request.body).toEqual({ subjectId: 'i', day: hoje });
    req.flush([]);
    await incluindo;
    await tick();
    responder(semana.from, semana.to);
  });
});
