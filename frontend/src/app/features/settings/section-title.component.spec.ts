import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { DataResetService } from './data-reset.service';
import { SectionTitleComponent } from './section-title.component';

@Component({
  imports: [SectionTitleComponent],
  template: `<app-section-title secao="tasks">Tarefas</app-section-title>`,
})
class Host {}

describe('SectionTitleComponent', () => {
  let fixture: ComponentFixture<Host>;
  let element: HTMLElement;
  let apagar: ReturnType<typeof vi.fn>;

  beforeEach(async () => {
    apagar = vi.fn();
    await TestBed.configureTestingModule({
      imports: [Host],
      providers: [
        provideRouter([]),
        { provide: DataResetService, useValue: { apagar, apagando: () => null } },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(Host);
    element = fixture.nativeElement;
    await fixture.whenStable();
  });

  const botao = () => element.querySelector<HTMLButtonElement>('button')!;

  // B15 T1 (CA1)
  it('mostra o título e o botão; apagar abre a tela de novo', async () => {
    expect(element.querySelector('h1')!.textContent).toContain('Tarefas');
    expect(botao().textContent).toContain('Apagar dados');
    const router = TestBed.inject(Router);
    const navegar = vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);
    apagar.mockResolvedValue(true);

    botao().click();
    await fixture.whenStable();

    expect(apagar).toHaveBeenCalledWith('tasks');
    expect(navegar).toHaveBeenCalledTimes(2);
  });

  // B15 T1 (CA1)
  it('cancelado, continua na tela sem recarregar', async () => {
    const navegar = vi.spyOn(TestBed.inject(Router), 'navigateByUrl');
    apagar.mockResolvedValue(false);

    botao().click();
    await fixture.whenStable();

    expect(apagar).toHaveBeenCalledWith('tasks');
    expect(navegar).not.toHaveBeenCalled();
  });
});
