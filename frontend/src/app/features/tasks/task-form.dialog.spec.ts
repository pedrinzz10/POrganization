import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { environment } from '../../../environments/environment';
import { TaskFormDialog } from './task-form.dialog';

const API = `${environment.apiUrl}/tasks`;

describe('TaskFormDialog', () => {
  let fixture: ComponentFixture<TaskFormDialog>;
  let element: HTMLElement;
  let httpMock: HttpTestingController;
  let dialogRef: { close: ReturnType<typeof vi.fn> };

  beforeEach(async () => {
    dialogRef = { close: vi.fn() };
    await TestBed.configureTestingModule({
      imports: [TaskFormDialog],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: MAT_DIALOG_DATA, useValue: {} },
        { provide: MatDialogRef, useValue: dialogRef },
      ],
    }).compileComponents();
    httpMock = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(TaskFormDialog);
    element = fixture.nativeElement;
    await fixture.whenStable();
  });

  afterEach(() => httpMock.verify());

  const toggle = (nome: string) =>
    element.querySelector<HTMLElement>(`mat-button-toggle button[aria-label="${nome}"]`)!;

  // T04 T1 (CA1)
  it('nova tarefa vem com os 7 dias; só seg/qua/sex vai no POST na ordem da semana', async () => {
    expect(fixture.componentInstance.form.controls.weekDays.value).toEqual([
      'MON',
      'TUE',
      'WED',
      'THU',
      'FRI',
      'SAT',
      'SUN',
    ]);
    expect(element.textContent).toContain('Todos os dias');

    for (const dia of ['Terça', 'Quinta', 'Sábado', 'Domingo']) {
      toggle(dia).click();
    }
    await fixture.whenStable();
    fixture.componentInstance.form.patchValue({ title: 'Academia', emoji: '🏋️' });

    const salvando = fixture.componentInstance.save();
    const req = httpMock.expectOne({ method: 'POST', url: API });
    expect(req.request.body).toEqual({
      title: 'Academia',
      emoji: '🏋️',
      weekDays: ['MON', 'WED', 'FRI'],
      reminderTime: null,
    });
    req.flush({});
    await salvando;
    expect(dialogRef.close).toHaveBeenCalledWith(true);
  });

  it('sem nenhum dia não chama a API e avisa', async () => {
    fixture.componentInstance.form.patchValue({ title: 'Nada', weekDays: [] });
    await fixture.componentInstance.save();
    await fixture.whenStable();

    httpMock.expectNone({ method: 'POST', url: API });
    expect(element.textContent).toContain('Escolha pelo menos um dia.');
  });

  // T05: lembrete opcional
  it('envia o horário do lembrete quando preenchido', async () => {
    fixture.componentInstance.form.patchValue({ title: 'Beber água', reminderTime: '15:00' });

    const salvando = fixture.componentInstance.save();
    const req = httpMock.expectOne({ method: 'POST', url: API });
    expect(req.request.body.reminderTime).toBe('15:00');
    req.flush({});
    await salvando;
  });
});
