import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { MatRippleModule } from '@angular/material/core';
import { MatIconModule } from '@angular/material/icon';
import { Shell } from '../shell';
import { DAY_SHAPES, OnboardingStore } from '../state';

@Component({
  selector: 'ob-shape-step',
  imports: [Shell, MatRippleModule, MatIconModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <ob-shell
      title="Your schedule"
      [progressIndex]="store.progressIndex()"
      [segments]="store.progressSegments"
      (continue)="store.next()">

      <div class="list" role="radiogroup" aria-label="Schedule shape">
        @for (s of shapes; track s.id) {
          <button matRipple class="card" role="radio"
                  [attr.aria-checked]="store.shapeId() === s.id"
                  [class.on]="store.shapeId() === s.id"
                  (click)="store.applyShape(s)">
            <span class="head">
              <span class="name">{{ s.label }}</span>
              @if (store.shapeId() === s.id) { <mat-icon>check_circle</mat-icon> }
            </span>
            <span class="meta">{{ s.weekday }}h weekday · {{ s.weekend }}h weekend</span>
          </button>
        }
      </div>
    </ob-shell>
  `,
  styles: `
    .list { display: flex; flex-direction: column; gap: 8px; }

    .card {
      display: flex;
      flex-direction: column;
      gap: 4px;
      padding: 16px;
      text-align: left;
      border: none;
      border-radius: var(--mat-sys-corner-large);
      background: var(--mat-sys-surface-container-high);
      color: var(--mat-sys-on-surface);
      cursor: pointer;
    }

    .card.on {
      background: var(--mat-sys-secondary-container);
      color: var(--mat-sys-on-secondary-container);
    }

    .head { display: flex; align-items: center; justify-content: space-between; }
    .name { font: var(--mat-sys-title-medium); }
    .meta { font: var(--mat-sys-body-medium); color: var(--mat-sys-on-surface-variant); }
    .card.on .meta { color: var(--mat-sys-on-secondary-container); }
    mat-icon { color: var(--mat-sys-on-secondary-container); }
  `,
})
export class ShapeStep {
  protected readonly store = inject(OnboardingStore);
  protected readonly shapes = DAY_SHAPES;
}
