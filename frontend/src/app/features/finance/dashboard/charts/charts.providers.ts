import { EnvironmentProviders, makeEnvironmentProviders } from '@angular/core';
import { ArcElement, BarController, BarElement, CategoryScale, DoughnutController, Legend, LinearScale, Tooltip } from 'chart.js';
import { provideCharts } from 'ng2-charts';

/** Registra só as peças do Chart.js que o resumo usa (rosca e barras), não a biblioteca inteira. */
export function provideFinanceCharts(): EnvironmentProviders {
  return makeEnvironmentProviders([
    provideCharts({
      registerables: [DoughnutController, ArcElement, BarController, BarElement, CategoryScale, LinearScale, Legend, Tooltip],
    }),
  ]);
}
