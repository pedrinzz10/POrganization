import { HttpClient } from '@angular/common/http';
import { inject, Injectable, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../environments/environment';
import { NotifyChannel } from '../../features/commitments/data/commitment.model';

/** Preferências do usuário (GET/PUT /api/settings). */
export interface Preferences {
  email: string | null;
  timezone: string;
  /** Canais do lembrete padrão e do resumo diário. */
  channels: NotifyChannel[];
  /** Lembrete que todo compromisso novo ganha; null = nenhum. */
  defaultReminderMinutes: number | null;
  /** Horário do resumo diário ("07:00"); null = sem resumo. */
  digestTime: string | null;
}

export type PreferencesRequest = Omit<Preferences, 'email'>;

/**
 * Preferências do usuário expostas como signal: o formulário de compromisso usa o lembrete padrão
 * como valor inicial e a tela de Configurações edita. Carrega uma vez e guarda.
 */
@Injectable({ providedIn: 'root' })
export class PreferencesService {
  private readonly http = inject(HttpClient);
  private readonly api = `${environment.apiUrl}/settings`;

  private readonly current = signal<Preferences | undefined>(undefined);
  /** undefined até a primeira carga. */
  readonly preferences = this.current.asReadonly();

  async load(): Promise<Preferences> {
    const loaded = this.current();
    if (loaded) {
      return loaded;
    }
    const preferences = await firstValueFrom(this.http.get<Preferences>(this.api));
    this.current.set(preferences);
    return preferences;
  }

  async save(request: PreferencesRequest): Promise<Preferences> {
    const saved = await firstValueFrom(this.http.put<Preferences>(this.api, request));
    this.current.set(saved);
    return saved;
  }
}

/** Opções de antecedência dos lembretes, em minutos. */
export const REMINDER_OPTIONS: { minutes: number; label: string }[] = [
  { minutes: 0, label: 'Na hora' },
  { minutes: 5, label: '5 minutos antes' },
  { minutes: 10, label: '10 minutos antes' },
  { minutes: 15, label: '15 minutos antes' },
  { minutes: 30, label: '30 minutos antes' },
  { minutes: 60, label: '1 hora antes' },
  { minutes: 120, label: '2 horas antes' },
  { minutes: 1440, label: '1 dia antes' },
  { minutes: 2880, label: '2 dias antes' },
  { minutes: 10080, label: '1 semana antes' },
];
