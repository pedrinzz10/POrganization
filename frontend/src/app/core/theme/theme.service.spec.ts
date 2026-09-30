import { TestBed } from '@angular/core/testing';
import { ThemeService } from './theme.service';

describe('ThemeService', () => {
  beforeEach(() => localStorage.clear());
  afterEach(() => {
    delete document.documentElement.dataset['theme'];
    localStorage.clear();
  });

  it('começa no claro e grava data-theme no <html>', () => {
    const service = TestBed.inject(ThemeService);
    expect(service.theme()).toBe('light');
    expect(document.documentElement.dataset['theme']).toBe('light');
  });

  it('escolher escuro troca na hora e fica guardado neste aparelho', () => {
    TestBed.inject(ThemeService).set('dark');

    expect(document.documentElement.dataset['theme']).toBe('dark');
    expect(localStorage.getItem('porganization.tema')).toBe('dark');
  });

  it('lê a escolha guardada ao abrir', () => {
    localStorage.setItem('porganization.tema', 'dark');
    expect(TestBed.inject(ThemeService).theme()).toBe('dark');
  });
});
