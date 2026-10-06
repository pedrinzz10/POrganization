import { Component, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';

export interface ConfirmData {
  title: string;
  message: string;
  confirmLabel?: string;
  /** Ação sem volta: o botão só libera depois de digitar este texto. */
  typeToConfirm?: string;
}

/** Pergunta de sim ou não. Fecha com true só no botão de confirmar. */
@Component({
  selector: 'app-confirm-dialog',
  imports: [MatDialogModule, MatButtonModule, MatFormFieldModule, MatInputModule],
  template: `
    <h2 mat-dialog-title>{{ data.title }}</h2>
    <mat-dialog-content>
      {{ data.message }}
      @if (data.typeToConfirm; as palavra) {
        <mat-form-field class="digitar">
          <mat-label>Digite {{ palavra }} para confirmar</mat-label>
          <input matInput autocomplete="off" (input)="digitado.set($any($event.target).value)" />
        </mat-form-field>
      }
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button type="button" [mat-dialog-close]="false">Cancelar</button>
      <button
        mat-flat-button
        type="button"
        [disabled]="!!data.typeToConfirm && digitado().trim() !== data.typeToConfirm"
        [mat-dialog-close]="true"
      >
        {{ data.confirmLabel ?? 'Confirmar' }}
      </button>
    </mat-dialog-actions>
  `,
  styles: `
    .digitar {
      display: block;
      margin-top: 16px;
    }
  `,
})
export class ConfirmDialog {
  protected readonly data = inject<ConfirmData>(MAT_DIALOG_DATA);
  protected readonly digitado = signal('');
}
