import { centsToDecimal, toCents } from '../../../shared/money-input/money-input.directive';

/**
 * Divide a compra em parcelas como a API (InstallmentCalculator): em centavos, o resto da divisão
 * vai para a primeira. 100,00 em 3x → 33,34 / 33,33 / 33,33. Valor pequeno demais → [].
 */
export function splitInstallments(amount: string | null, count: number): string[] {
  if (!amount || !Number.isInteger(count) || count < 1 || count > 48) {
    return [];
  }
  const total = toCents(amount);
  const parcelas = BigInt(count);
  const base = total / parcelas;
  if (base <= 0n) {
    return [];
  }
  const primeira = base + (total - base * parcelas);
  return Array.from({ length: count }, (_, i) => centsToDecimal(i === 0 ? primeira : base));
}
