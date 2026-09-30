import { Component } from '@angular/core';
import { SectionPlaceholderComponent } from '../../shared/section-placeholder/section-placeholder.component';

@Component({
  selector: 'app-settings-page',
  imports: [SectionPlaceholderComponent],
  template: `<app-section-placeholder title="Configurações" description="Preferências de notificação e integrações chegam na etapa 5." />`,
})
export class SettingsPage {}
