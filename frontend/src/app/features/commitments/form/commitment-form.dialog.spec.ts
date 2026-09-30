import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { environment } from '../../../../environments/environment';
import { Commitment } from '../data/commitment.model';
import { CommitmentFormData, CommitmentFormDialog } from './commitment-form.dialog';

const URL = `${environment.apiUrl}/commitments`;

const serie: Commitment = {
  id: 'serie-1',
  title: 'Academia',
  date: '2026-10-01',
  startTime: '07:00',
  endTime: '08:00',
  allDay: false,
  description: null,
  location: 'Smart Fit',
  done: false,
  recurrenceRule: { freq: 'WEEKLY', interval: 1, byWeekDays: ['MON', 'WED', 'FRI'] },
  createdAt: '2026-10-01T10:00:00Z',
  updatedAt: '2026-10-01T10:00:00Z',
};

describe('CommitmentFormDialog', () => {
  let fixture: ComponentFixture<CommitmentFormDialog>;
  let element: HTMLElement;
  let httpMock: HttpTestingController;
  let dialogRef: { close: ReturnType<typeof vi.fn> };

  async function abrir(data: CommitmentFormData) {
    dialogRef = { close: vi.fn() };
    await TestBed.configureTestingModule({
      imports: [CommitmentFormDialog],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: MAT_DIALOG_DATA, useValue: data },
        { provide: MatDialogRef, useValue: dialogRef },
      ],
    }).compileComponents();
    httpMock = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(CommitmentFormDialog);
    element = fixture.nativeElement;
    await fixture.whenStable();
  }

  afterEach(() => httpMock.verify());

  const botao = (texto: string) =>
    Array.from(element.querySelectorAll<HTMLButtonElement>('button')).find((b) => b.textContent!.trim() === texto)!;

  async function salvar() {
    botao('Salvar').click();
    await fixture.whenStable();
  }

  // C09 T2 (CA2)
  it('gera o recurrenceRule que a API espera', async () => {
    await abrir({ date: '2026-10-05' });
    const form = fixture.componentInstance.form;
    form.patchValue({ title: 'Aula de Java', startTime: '19:00', repeat: true });
    form.controls.recurrence.patchValue({
      freq: 'WEEKLY',
      interval: 1,
      byWeekDays: ['MON', 'WED'],
      endType: 'until',
      until: '2026-12-31',
    });
    await fixture.whenStable();

    await salvar();

    const req = httpMock.expectOne({ method: 'POST', url: URL });
    expect(req.request.body.recurrenceRule).toEqual({
      freq: 'WEEKLY',
      interval: 1,
      byWeekDays: ['MON', 'WED'],
      until: '2026-12-31',
    });
    expect(req.request.body).toMatchObject({ title: 'Aula de Java', date: '2026-10-05', startTime: '19:00' });
    req.flush({ ...serie, id: 'nova' });
    await fixture.whenStable();
    expect(dialogRef.close).toHaveBeenCalledWith('saved');
  });

  it('sem "repetir", não manda regra', async () => {
    await abrir({ date: '2026-10-05' });
    fixture.componentInstance.form.patchValue({ title: 'Dentista', startTime: '14:00' });
    await salvar();

    const req = httpMock.expectOne({ method: 'POST', url: URL });
    expect(req.request.body.recurrenceRule).toBeNull();
    req.flush(serie);
  });

  // C09 T3 (CA3)
  it('editar "só esta" ocorrência chama o PATCH da ocorrência', async () => {
    await abrir({ commitment: serie, occurrenceDate: '2026-10-05' });
    fixture.componentInstance.scope.setValue('this');
    fixture.componentInstance.form.patchValue({ title: 'Academia (perna)', startTime: '18:00' });
    await fixture.whenStable();

    await salvar();

    const req = httpMock.expectOne(`${URL}/serie-1/occurrences/2026-10-05`);
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual({ title: 'Academia (perna)', startTime: '18:00' });
    req.flush({});
  });

  // C09 T3 (CA3)
  it('editar "toda a série" chama o PUT', async () => {
    await abrir({ commitment: serie, occurrenceDate: '2026-10-05' });
    fixture.componentInstance.scope.setValue('series');
    fixture.componentInstance.form.patchValue({ title: 'Academia (novo horário)', startTime: '06:30' });
    await fixture.whenStable();

    await salvar();

    const req = httpMock.expectOne(`${URL}/serie-1`);
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toMatchObject({
      title: 'Academia (novo horário)',
      date: '2026-10-01',
      startTime: '06:30',
      recurrenceRule: { freq: 'WEEKLY', interval: 1, byWeekDays: ['MON', 'WED', 'FRI'] },
    });
    req.flush(serie);
  });

  it('abre preenchido com os dados do compromisso', async () => {
    await abrir({ commitment: serie, occurrenceDate: '2026-10-05' });
    const valor = fixture.componentInstance.form.getRawValue();
    expect(valor).toMatchObject({ title: 'Academia', date: '2026-10-01', startTime: '07:00', endTime: '08:00', location: 'Smart Fit', repeat: true });
    expect(fixture.componentInstance.form.controls.recurrence.getRawValue().byWeekDays).toEqual(['MON', 'WED', 'FRI']);
  });

  it('excluir pede confirmação antes do DELETE', async () => {
    await abrir({ commitment: { ...serie, recurrenceRule: null } });

    botao('Excluir').click();
    await fixture.whenStable();
    httpMock.expectNone(`${URL}/serie-1`);
    expect(element.textContent).toContain('Tem certeza');

    botao('Sim, excluir').click();
    await fixture.whenStable();
    httpMock.expectOne({ method: 'DELETE', url: `${URL}/serie-1` }).flush(null);
    await fixture.whenStable();
    expect(dialogRef.close).toHaveBeenCalledWith('deleted');
  });

  it('excluir só esta ocorrência cancela o dia, sem apagar a série', async () => {
    await abrir({ commitment: serie, occurrenceDate: '2026-10-05' });
    fixture.componentInstance.scope.setValue('this');
    await fixture.whenStable();

    botao('Excluir').click();
    await fixture.whenStable();
    botao('Sim, excluir').click();
    await fixture.whenStable();

    const req = httpMock.expectOne(`${URL}/serie-1/occurrences/2026-10-05`);
    expect(req.request.body).toEqual({ cancelled: true });
    req.flush({});
  });
});
