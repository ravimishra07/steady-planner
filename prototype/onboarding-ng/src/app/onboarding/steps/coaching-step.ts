import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { MatRippleModule } from '@angular/material/core';
import { Shell } from '../shell';
import { COACHINGS, OnboardingStore } from '../state';

@Component({
  selector: 'ob-coaching-step',
  imports: [Shell, MatIconModule, MatRippleModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <ob-shell
      title="Which coaching?"
      [progressIndex]="store.progressIndex()"
      [segments]="store.progressSegments"
      [continueEnabled]="store.coachingId() !== ''"
      (continue)="store.next()">

      <div class="list" role="radiogroup" aria-label="Coaching">
        @for (c of coachings; track c.id) {
          <button matRipple class="row" role="radio"
                  [attr.aria-checked]="store.coachingId() === c.id"
                  [class.on]="store.coachingId() === c.id"
                  (click)="store.coachingId.set(c.id)">
            <mat-icon class="lead">{{ c.icon }}</mat-icon>
            <span class="text">
              <span class="name">{{ c.label }}</span>
              <span class="mode">{{ c.mode }}</span>
            </span>
            @if (store.coachingId() === c.id) {
              <mat-icon class="mark">check_circle</mat-icon>
            }
          </button>
        }
      </div>
    </ob-shell>
  `,
  styles: `
    .list { display: flex; flex-direction: column; gap: 8px; }

    /* M3 two-line list item: 72dp min height, 16dp padding, 16dp icon gap. */
    .row {
      display: flex;
      align-items: center;
      gap: 16px;
      min-height: 72px;
      padding: 8px 16px;
      border: none;
      border-radius: var(--mat-sys-corner-large);
      background: var(--mat-sys-surface-container-high);
      color: var(--mat-sys-on-surface);
      text-align: left;
      cursor: pointer;
    }

    .row.on {
      background: var(--mat-sys-secondary-container);
      color: var(--mat-sys-on-secondary-container);
    }

    .text { display: flex; flex-direction: column; flex: 1; }
    .name { font: var(--mat-sys-body-large); }
    .mode { font: var(--mat-sys-body-medium); color: var(--mat-sys-on-surface-variant); }
    .row.on .mode { color: var(--mat-sys-on-secondary-container); }

    .lead { color: var(--mat-sys-on-surface-variant); }
    .row.on .lead, .mark { color: var(--mat-sys-on-secondary-container); }
  `,
})
export class CoachingStep {
  protected readonly store = inject(OnboardingStore);
  protected readonly coachings = COACHINGS;
}
