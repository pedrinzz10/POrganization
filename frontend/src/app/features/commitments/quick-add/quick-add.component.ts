import { Component, inject, output, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSnackBar } from '@angular/material/snack-bar';
import { firstValueFrom } from 'rxjs';
import { problemMessage } from '../../../core/http/problem';
import { Commitment, CommitmentRequest } from '../data/commitment.model';
import { CommitmentsService } from '../data/commitments.service';
import { today } from '../data/date-range.util';

const naoVazio = Validators.pattern(/\S/);

/**
 * Criação rápida em uma linha: título, dia (hoje por padrão) e hora. Enter salva e limpa só
 * o título, para lançar vários seguidos. Avisa o pai pelo output "created".
 */
@Component({
  selector: 'app-quick-add',
  imports: [ReactiveFormsModule, MatFormFieldModule, MatInputModule, MatButtonModule, MatIconModule],
  templateUrl: './quick-add.component.html',
  styleUrl: './quick-add.component.scss',
})
export class QuickAddComponent {
  private readonly commitments = inject(CommitmentsService);
  private readonly snackBar = inject(MatSnackBar);

  /** Emite o compromisso criado (como a API devolveu) para o pai atualizar a lista. */
  readonly created = output<Commitment>();

  readonly form = inject(FormBuilder).nonNullable.group({
    title: ['', [Validators.required, naoVazio, Validators.maxLength(200)]],
    date: [today(), Validators.required],
    startTime: [''],
  });

  readonly saving = signal(false);

  async submit(event?: Event): Promise<void> {
    event?.preventDefault();
    if (this.form.invalid || this.saving()) {
      this.form.markAllAsTouched();
      return;
    }
    const { title, date, startTime } = this.form.getRawValue();
    const request: CommitmentRequest = { title: title.trim(), date };
    if (startTime) {
      request.startTime = startTime;
    }

    this.saving.set(true);
    try {
      const commitment = await firstValueFrom(this.commitments.create(request));
      this.form.controls.title.reset('');
      this.created.emit(commitment);
      this.snackBar.open('Compromisso criado', 'OK', { duration: 3000 });
    } catch (error) {
      this.snackBar.open(problemMessage(error, 'Não foi possível criar o compromisso.'), 'OK', { duration: 5000 });
    } finally {
      this.saving.set(false);
    }
  }
}
