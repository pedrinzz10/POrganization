import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { environment } from '../../../environments/environment';
import { PreferencesFormComponent } from './preferences-form.component';

const API = `${environment.apiUrl}/settings`;
const tick = () => new Promise((resolve) => setTimeout(resolve));

describe('PreferencesFormComponent', () => {
  let fixture: ComponentFixture<PreferencesFormComponent>;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PreferencesFormComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    httpMock = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(PreferencesFormComponent);
    httpMock.expectOne(API).flush({
      email: 'pedro@teste.com', timezone: 'America/Manaus', channels: ['PUSH'], defaultReminderMinutes: null, digestTime: null,
    });
    await tick();
    await fixture.whenStable();
  });

  afterEach(() => httpMock.verify());

  it('abre com as preferências da API', () => {
    expect(fixture.componentInstance.form.getRawValue()).toEqual({
      timezone: 'America/Manaus', push: true, email: false, defaultReminderMinutes: null, digestTime: '',
    });
  });

  it('salvar manda canais, lembrete padrão e resumo; resumo vazio vira null', async () => {
    fixture.componentInstance.form.patchValue({ email: true, defaultReminderMinutes: 30 });

    const salvando = fixture.componentInstance.save();
    const req = httpMock.expectOne({ method: 'PUT', url: API });
    expect(req.request.body).toEqual({
      timezone: 'America/Manaus', channels: ['PUSH', 'EMAIL'], defaultReminderMinutes: 30, digestTime: null,
    });
    req.flush({ email: 'pedro@teste.com', timezone: 'America/Manaus', channels: ['EMAIL', 'PUSH'], defaultReminderMinutes: 30, digestTime: null });
    await salvando;
    await fixture.whenStable();

    expect(fixture.nativeElement.textContent).toContain('Preferências salvas.');
  });

  it('sem nenhum canal não salva', async () => {
    fixture.componentInstance.form.patchValue({ push: false, email: false });
    await fixture.componentInstance.save();
    await fixture.whenStable();

    httpMock.expectNone({ method: 'PUT', url: API });
    expect(fixture.nativeElement.textContent).toContain('Escolha push, e-mail ou os dois.');
  });
});
