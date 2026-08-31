import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { MatRippleModule } from '@angular/material/core';
import { Shell } from '../shell';
import { OnboardingStore } from '../state';
import {
  COMMITMENT_PRESETS,
  Commitment,
  DAY_INITIALS,
  clockLabel,
  minutesFromTimeValue,
  timeValue,
} from '../commitments';

/**
 * Fixed hours. Coaching used to be a constant in the timeline; here it is
 * asked for, along with school and anything else that owns part of the day.
 * Study is then scheduled into the gaps these leave.
 */
@Component({
  selector: 'ob-commitments-step',
  imports: [Shell, MatIconModule, MatRippleModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <ob-shell
      title="What's already fixed?"
      [progressIndex]="store.progressIndex()"
      [segments]="store.progressSegments"
      (continue)="store.next()">

      <p class="lede">
        Class, school, lectures — the hours you can't move. Everything else becomes
        study time.
      </p>

      <div class="group">
        @for (c of store.commitments(); track c.id) {
          <div class="card">
            <div class="card-head">
              <mat-icon>{{ icon(c) }}</mat-icon>
              <span class="name">{{ c.label }}</span>
              <span class="span">{{ range(c) }}</span>
              <button matRipple class="icon-button" (click)="store.removeCommitment(c.id)"
                      [attr.aria-label]="'Remove ' + c.label">
                <mat-icon>close</mat-icon>
              </button>
            </div>

            <div class="times">
              <label>
                <span class="field-label">Starts</span>
                <input type="time" [value]="value(c.startMinute)"
                       (change)="setStart(c, $any($event.target).value)" />
              </label>
              <label>
                <span class="field-label">Ends</span>
                <input type="time" [value]="value(c.startMinute + c.minutes)"
                       (change)="setEnd(c, $any($event.target).value)" />
              </label>
            </div>

            <div class="days">
              @for (d of dayInitials; track $index) {
                <button matRipple class="day" [class.on]="c.days.includes($index)"
                        (click)="store.toggleCommitmentDay(c.id, $index)">{{ d }}</button>
              }
            </div>
          </div>
        } @empty {
          <div class="empty">
            <mat-icon>event_available</mat-icon>
            <span>Nothing fixed — the whole day is yours.</span>
          </div>
        }
      </div>

      <div class="group">
        <h2 class="label">Add</h2>
        <div class="presets">
          @for (p of presets; track p.kind) {
            <button matRipple class="preset" (click)="store.addCommitment(p)">
              <mat-icon>{{ p.icon }}</mat-icon>
              {{ p.label }}
            </button>
          }
        </div>
      </div>

      <div class="group">
        <h2 class="label">Awake between</h2>
        <div class="times">
          <label>
            <span class="field-label">Up at</span>
            <input type="time" [value]="value(store.wakeMinute())"
                   (change)="store.wakeMinute.set(parse($any($event.target).value))" />
          </label>
          <label>
            <span class="field-label">Lights out</span>
            <input type="time" [value]="value(store.sleepMinute())"
                   (change)="store.sleepMinute.set(parse($any($event.target).value))" />
          </label>
        </div>
      </div>

      <div class="totals">
        <span class="total">
          <span class="big">{{ weekday() }}</span>
          <span class="caption">free on a weekday</span>
        </span>
        <span class="total">
          <span class="big">{{ weekend() }}</span>
          <span class="caption">free on Sunday</span>
        </span>
      </div>
    </ob-shell>
  `,
  styles: `
    .lede { margin: 0; font: var(--mat-sys-body-medium); color: var(--mat-sys-on-surface-variant); }

    .group { display: flex; flex-direction: column; gap: 12px; }
    .label { margin: 0; font: var(--mat-sys-title-small); color: var(--mat-sys-on-surface-variant); }

    .card {
      display: flex;
      flex-direction: column;
      gap: 12px;
      padding: 12px 16px 16px;
      border-radius: var(--mat-sys-corner-large);
      background: var(--mat-sys-surface-container-high);
    }

    .card-head { display: flex; align-items: center; gap: 8px; }
    .card-head > mat-icon { color: var(--mat-sys-primary); }
    .name { flex: 1; font: var(--mat-sys-title-medium); }
    .span { font: var(--mat-sys-label-medium); color: var(--mat-sys-on-surface-variant); }

    .icon-button {
      display: grid;
      place-items: center;
      width: 32px;
      height: 32px;
      border: none;
      border-radius: var(--mat-sys-corner-full);
      background: transparent;
      color: var(--mat-sys-on-surface-variant);
      cursor: pointer;
    }

    .times { display: flex; gap: 8px; }
    .times label { flex: 1; display: flex; flex-direction: column; gap: 4px; }
    .field-label { font: var(--mat-sys-label-small); color: var(--mat-sys-on-surface-variant); }

    input[type='time'] {
      height: 40px;
      padding: 0 12px;
      border: 1px solid var(--mat-sys-outline);
      border-radius: var(--mat-sys-corner-small);
      background: transparent;
      color: var(--mat-sys-on-surface);
      font: var(--mat-sys-body-medium);
      color-scheme: inherit;
    }

    .days { display: flex; gap: 4px; }

    .day {
      flex: 1;
      height: 36px;
      border: 1px solid var(--mat-sys-outline-variant);
      border-radius: var(--mat-sys-corner-full);
      background: transparent;
      color: var(--mat-sys-on-surface-variant);
      font: var(--mat-sys-label-large);
      cursor: pointer;
    }

    .day.on {
      border-color: transparent;
      background: var(--mat-sys-secondary-container);
      color: var(--mat-sys-on-secondary-container);
    }

    .presets { display: flex; flex-wrap: wrap; gap: 8px; }

    .preset {
      display: flex;
      align-items: center;
      gap: 8px;
      height: 32px;
      padding: 0 12px 0 8px;
      border: 1px solid var(--mat-sys-outline-variant);
      border-radius: var(--mat-sys-corner-full);
      background: transparent;
      color: var(--mat-sys-on-surface);
      font: var(--mat-sys-label-large);
      cursor: pointer;
    }

    .preset mat-icon { font-size: 18px; width: 18px; height: 18px; }

    .empty {
      display: flex;
      align-items: center;
      gap: 8px;
      padding: 16px;
      border: 1px dashed var(--mat-sys-outline-variant);
      border-radius: var(--mat-sys-corner-large);
      color: var(--mat-sys-on-surface-variant);
      font: var(--mat-sys-body-medium);
    }

    .totals {
      display: flex;
      gap: 8px;
    }

    .total {
      flex: 1;
      display: flex;
      flex-direction: column;
      gap: 2px;
      padding: 12px 16px;
      border-radius: var(--mat-sys-corner-large);
      background: var(--mat-sys-surface-container-high);
    }

    .big { font: var(--mat-sys-headline-small); color: var(--mat-sys-primary); }
    .caption { font: var(--mat-sys-label-medium); color: var(--mat-sys-on-surface-variant); }
  `,
})
export class CommitmentsStep {
  protected readonly store = inject(OnboardingStore);
  protected readonly presets = COMMITMENT_PRESETS;
  protected readonly dayInitials = DAY_INITIALS;

  protected readonly weekday = computed(() => hours(this.store.freeMinutesOn(3)));
  protected readonly weekend = computed(() => hours(this.store.freeMinutesOn(0)));

  protected icon(c: Commitment): string {
    return COMMITMENT_PRESETS.find((p) => p.kind === c.kind)?.icon ?? 'schedule';
  }

  protected range(c: Commitment): string {
    return `${clockLabel(c.startMinute)}–${clockLabel(c.startMinute + c.minutes)}`;
  }

  protected value(minute: number): string { return timeValue(minute); }
  protected parse(value: string): number { return minutesFromTimeValue(value); }

  protected setStart(c: Commitment, value: string): void {
    const start = minutesFromTimeValue(value);
    const end = c.startMinute + c.minutes;
    this.store.updateCommitment(c.id, { startMinute: start, minutes: Math.max(30, end - start) });
  }

  protected setEnd(c: Commitment, value: string): void {
    const end = minutesFromTimeValue(value);
    this.store.updateCommitment(c.id, { minutes: Math.max(30, end - c.startMinute) });
  }
}

function hours(minutes: number): string {
  const h = Math.floor(minutes / 60);
  const m = minutes % 60;
  return m === 0 ? `${h}h` : `${h}h ${m}m`;
}
