import { Component, inject, signal } from '@angular/core';
import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../../core/auth/auth.service';

function senhasIguais(group: AbstractControl): ValidationErrors | null {
  const { password, confirmation } = group.value as { password: string; confirmation: string };
  return confirmation && password !== confirmation ? { senhasDiferentes: true } : null;
}

@Component({
  selector: 'app-signup',
  imports: [ReactiveFormsModule, RouterLink, MatCardModule, MatFormFieldModule, MatInputModule, MatButtonModule],
  styleUrl: '../auth.scss',
  template: `
    <main class="auth">
      <mat-card appearance="outlined" class="auth__card">
        <mat-card-header>
          <mat-card-title>Criar conta</mat-card-title>
        </mat-card-header>

        <mat-card-content>
          @if (confirmationSent()) {
            <p class="auth__info" role="status">
              Enviamos um link de confirmação para o seu e-mail. Depois de confirmar, é só entrar.
            </p>
          } @else {
            <form [formGroup]="form" (ngSubmit)="submit()" class="auth__form">
              <mat-form-field>
                <mat-label>E-mail</mat-label>
                <input matInput type="email" formControlName="email" autocomplete="email" required />
                @if (form.controls.email.invalid) {
                  <mat-error>Digite um e-mail válido.</mat-error>
                }
              </mat-form-field>

              <mat-form-field>
                <mat-label>Senha</mat-label>
                <input matInput type="password" formControlName="password" autocomplete="new-password" required />
                @if (form.controls.password.invalid) {
                  <mat-error>Use pelo menos 6 caracteres.</mat-error>
                }
              </mat-form-field>

              <mat-form-field>
                <mat-label>Confirme a senha</mat-label>
                <input matInput type="password" formControlName="confirmation" autocomplete="new-password" required />
              </mat-form-field>

              @if (form.hasError('senhasDiferentes')) {
                <p class="auth__error" role="alert">As senhas não conferem.</p>
              }
              @if (error(); as message) {
                <p class="auth__error" role="alert">{{ message }}</p>
              }

              <button mat-flat-button type="submit" [disabled]="form.invalid || loading()">
                {{ loading() ? 'Criando...' : 'Criar conta' }}
              </button>
            </form>
          }
        </mat-card-content>

        <mat-card-actions>
          <a mat-button routerLink="/login">Já tenho conta</a>
        </mat-card-actions>
      </mat-card>
    </main>
  `,
})
export class SignupComponent {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  readonly form = inject(FormBuilder).nonNullable.group(
    {
      email: ['', [Validators.required, Validators.email]],
      password: ['', [Validators.required, Validators.minLength(6)]],
      confirmation: ['', Validators.required],
    },
    { validators: senhasIguais },
  );

  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly confirmationSent = signal(false);

  async submit(): Promise<void> {
    if (this.form.invalid || this.loading()) {
      return;
    }
    this.loading.set(true);
    this.error.set(null);
    const { email, password } = this.form.getRawValue();
    try {
      const { needsEmailConfirmation } = await this.auth.signUp(email, password);
      if (needsEmailConfirmation) {
        this.confirmationSent.set(true);
      } else {
        await this.router.navigate(['/hoje']);
      }
    } catch (e) {
      this.error.set(e instanceof Error ? e.message : 'Não foi possível criar a conta. Tente de novo.');
    } finally {
      this.loading.set(false);
    }
  }
}
