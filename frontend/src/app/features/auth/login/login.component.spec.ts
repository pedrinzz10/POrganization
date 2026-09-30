import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { AuthService } from '../../../core/auth/auth.service';
import { LoginComponent } from './login.component';

describe('LoginComponent', () => {
  let fixture: ComponentFixture<LoginComponent>;
  let element: HTMLElement;
  let auth: { signIn: ReturnType<typeof vi.fn> };
  let navigate: ReturnType<typeof vi.spyOn>;

  beforeEach(async () => {
    auth = { signIn: vi.fn() };
    await TestBed.configureTestingModule({
      imports: [LoginComponent],
      providers: [provideRouter([]), { provide: AuthService, useValue: auth }],
    }).compileComponents();

    navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    fixture = TestBed.createComponent(LoginComponent);
    element = fixture.nativeElement;
    await fixture.whenStable();
  });

  async function preencher(email: string, senha: string) {
    for (const [nome, valor] of [['email', email], ['password', senha]]) {
      const input = element.querySelector<HTMLInputElement>(`input[formControlName="${nome}"]`)!;
      input.value = valor;
      input.dispatchEvent(new Event('input'));
    }
    await fixture.whenStable();
  }

  const botaoEntrar = () => element.querySelector<HTMLButtonElement>('button[type="submit"]')!;

  async function enviar() {
    element.querySelector('form')!.dispatchEvent(new Event('submit'));
    await fixture.whenStable();
  }

  // B07 T1 (CA1)
  it('desabilita Entrar com e-mail inválido e habilita com dados válidos', async () => {
    await preencher('abc', '123456');
    expect(botaoEntrar().disabled).toBe(true);

    await preencher('pedro@teste.com', '12345');
    expect(botaoEntrar().disabled).toBe(true);

    await preencher('pedro@teste.com', '123456');
    expect(botaoEntrar().disabled).toBe(false);
  });

  // B07 T2 (CA2)
  it('com login certo, navega para /hoje', async () => {
    auth.signIn.mockResolvedValue(undefined);
    await preencher('pedro@teste.com', 'segredo123');

    await enviar();

    expect(auth.signIn).toHaveBeenCalledWith('pedro@teste.com', 'segredo123');
    expect(navigate).toHaveBeenCalledWith(['/hoje']);
  });

  // B07 T2 (CA2)
  it('com erro do Supabase, mostra a mensagem na tela', async () => {
    auth.signIn.mockRejectedValue(new Error('Invalid login'));
    await preencher('pedro@teste.com', 'segredo123');

    await enviar();

    expect(element.textContent).toContain('Invalid login');
    expect(navigate).not.toHaveBeenCalled();
  });
});
