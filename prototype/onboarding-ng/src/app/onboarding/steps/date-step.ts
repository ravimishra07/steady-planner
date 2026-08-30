import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { DatePipe } from '@angular/common';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { Shell } from '../shell';
import { OnboardingStore, addDays, startOfToday } from '../state';

/** One question, one control: the calendar. The line above reads the pick back. */
@Component({
  selector: 'ob-date-step',
  imports: [Shell, MatDatepickerModule, DatePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <ob-shell
      title="When's the exam?"
      [progressIndex]="store.progressIndex()"
      [segments]="store.progressSegments"
      (continue)="store.next()">

      <div class="readback">
        <div class="when">
          <span class="full">{{ store.targetDate() | date: 'EEEE, d MMM y' }}</span>
          <span class="meaning">Last day of prep</span>
        </div>
        <span class="chip">{{ store.days() }} days</span>
      </div>

      <mat-calendar
        [startAt]="store.targetDate()"
        [selected]="store.targetDate()"
        [minDate]="min"
        [maxDate]="max"
        (selectedChange)="pick($event)" />
    </ob-shell>
  `,
  styles: `
    .readback {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 16px;
      flex: none;
    }

    .when { display: flex; flex-direction: column; gap: 2px; }
    .full { font: var(--mat-sys-title-large); color: var(--mat-sys-on-surface); }
    .meaning { font: var(--mat-sys-body-medium); color: var(--mat-sys-on-surface-variant); }

    /* M3 assist-chip metrics: 32dp tall, 16dp side padding, full radius. */
    .chip {
      flex: none;
      height: 32px;
      display: grid;
      place-items: center;
      padding: 0 16px;
      border-radius: var(--mat-sys-corner-full);
      background: var(--mat-sys-secondary-container);
      color: var(--mat-sys-on-secondary-container);
      font: var(--mat-sys-label-large);
    }

    mat-calendar { flex: none; }

    /* The month tag inside the grid is redundant next to the header — keep it
       for screen readers, drop it visually. */
    ::ng-deep .mat-calendar-body-label { visibility: hidden; }
  `,
})
export class DateStep {
  protected readonly store = inject(OnboardingStore);

  /** A target must be at least a week out and no more than two years out. */
  protected readonly min = addDays(startOfToday(), 7);
  protected readonly max = addDays(startOfToday(), 730);

  protected pick(date: Date | null): void {
    if (date) this.store.targetDate.set(date);
  }
}
