import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { MatIconRegistry } from '@angular/material/icon';
import { RouterOutlet } from '@angular/router';
import { OnboardingStore } from './onboarding/state';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="app-theme" [class]="'accent-' + store.accent() + ' bg-' + store.appearance()">
      <router-outlet />
    </div>
  `,
  styles: `
    :host, .app-theme { display: block; min-height: 100%; }
  `,
})
export class App {
  protected readonly store = inject(OnboardingStore);

  constructor() {
    inject(MatIconRegistry).setDefaultFontSetClass('material-symbols-rounded');
  }
}
