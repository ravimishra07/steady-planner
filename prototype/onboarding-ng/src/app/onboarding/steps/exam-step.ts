import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { MatRippleModule } from '@angular/material/core';
import { Shell } from '../shell';
import { EXAMS, OnboardingStore } from '../state';

@Component({
  selector: 'ob-exam-step',
  imports: [Shell, MatIconModule, MatRippleModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <ob-shell
      title="Which exam?"
      [progressIndex]="store.progressIndex()"
      [segments]="store.progressSegments"
      [continueEnabled]="store.examId() !== ''"
      (continue)="store.next()">

      <div class="list" role="radiogroup" aria-label="Exam">
        @for (e of exams; track e.id) {
          <button matRipple class="row" role="radio"
                  [disabled]="!e.available"
                  [attr.aria-checked]="store.examId() === e.id"
                  [class.on]="store.examId() === e.id"
                  (click)="store.examId.set(e.id)">
            <span class="name">{{ e.label }}</span>
            @if (e.available && store.examId() === e.id) {
              <mat-icon class="filled">check_circle</mat-icon>
            }
          </button>
        }
      </div>
    </ob-shell>
  `,
  styles: `
    .list { display: flex; flex-direction: column; gap: 8px; }

    /* M3 two-line-free list item: 56dp min height, 16dp inner padding. */
    .row {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 16px;
      min-height: 56px;
      padding: 0 16px;
      border: none;
      border-radius: var(--mat-sys-corner-large);
      background: var(--mat-sys-surface-container-high);
      color: var(--mat-sys-on-surface);
      font: var(--mat-sys-title-medium);
      cursor: pointer;
    }

    .row.on {
      background: var(--mat-sys-secondary-container);
      color: var(--mat-sys-on-secondary-container);
    }

    .row:disabled {
      cursor: default;
      background: var(--mat-sys-surface-container);
      color: var(--mat-sys-on-surface-variant);
    }


    mat-icon { color: var(--mat-sys-on-secondary-container); }
  `,
})
export class ExamStep {
  protected readonly store = inject(OnboardingStore);
  protected readonly exams = EXAMS;
}
