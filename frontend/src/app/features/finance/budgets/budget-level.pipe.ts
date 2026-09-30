import { Pipe, PipeTransform } from '@angular/core';
import { BudgetLevel } from '../data/finance.model';

const CLASSES: Record<BudgetLevel, string> = { OK: 'budget--ok', ATENCAO: 'budget--warn', ESTOURADO: 'budget--over' };
const TEXTOS: Record<BudgetLevel, string> = { OK: 'Dentro do orçamento', ATENCAO: 'Atenção', ESTOURADO: 'Estourado' };

/**
 * Nível de alerta do orçamento → classe CSS (padrão) ou texto. Deixa o template limpo:
 * `[class]="b.level | budgetLevel"` e `{{ b.level | budgetLevel: 'label' }}`.
 */
@Pipe({ name: 'budgetLevel' })
export class BudgetLevelPipe implements PipeTransform {
  transform(level: BudgetLevel, as: 'class' | 'label' = 'class'): string {
    return as === 'label' ? TEXTOS[level] : CLASSES[level];
  }
}
