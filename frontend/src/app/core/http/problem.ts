import { HttpErrorResponse } from '@angular/common/http';

/** Formato de erro da API (ProblemDetail, RFC 9457), definido na B05. */
export interface ProblemDetail {
  title?: string;
  status?: number;
  detail?: string;
  errors?: { field: string; message: string }[];
}

/** Mensagem legível para mostrar ao usuário a partir de qualquer erro de uma chamada à API. */
export function problemMessage(error: unknown, fallback = 'Não foi possível concluir. Tente de novo.'): string {
  if (error instanceof HttpErrorResponse) {
    if (error.status === 0) {
      return 'Sem conexão com o servidor. Verifique sua internet.';
    }
    const problem = error.error as ProblemDetail | null;
    const firstField = problem?.errors?.[0];
    if (firstField) {
      return `${firstField.field}: ${firstField.message}`;
    }
    if (problem?.detail) {
      return problem.detail;
    }
  }
  return fallback;
}
