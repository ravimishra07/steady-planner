import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatSliderModule } from '@angular/material/slider';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatChipsModule } from '@angular/material/chips';
import { MatInputModule } from '@angular/material/input';
import { Shell } from '../shell';
import { OnboardingStore, STUDY_SPOTS } from '../state';

@Component({
  selector: 'ob-hours-step',
  imports: [Shell, FormsModule, MatSliderModule, MatFormFieldModule, MatInputModule, MatChipsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <ob-shell
      title="Hours per day"
      ctaLabel="Build my plan"
      [progressIndex]="store.progressIndex()"
      [segments]="store.progressSegments"
      (continue)="store.next()">

      <div class="field">
        <div class="row">
          <span class="label">Weekdays</span>
          <span class="value">{{ store.weekdayHours() }} hrs</span>
        </div>
        <mat-slider min="1" max="14" step="0.5" discrete>
          <input matSliderThumb
                 [ngModel]="store.weekdayHours()"
                 (ngModelChange)="store.weekdayHours.set($event)" />
        </mat-slider>
      </div>

      <div class="field">
        <div class="row">
          <span class="label">Weekends</span>
          <span class="value">{{ store.weekendHours() }} hrs</span>
        </div>
        <mat-slider min="1" max="16" step="0.5" discrete>
          <input matSliderThumb
                 [ngModel]="store.weekendHours()"
                 (ngModelChange)="store.weekendHours.set($event)" />
        </mat-slider>
      </div>

      <div class="total">
        <span class="unit">Each week</span>
        <span class="chip">{{ store.weeklyHours() }} hours</span>
      </div>

      <div class="spots">
        <span class="label">Study spot</span>
        <mat-chip-listbox [value]="chosen()" (change)="choose($event.value)" aria-label="Study spot">
          @for (spot of spots; track spot) {
            <mat-chip-option [value]="spot" [selected]="chosen() === spot">{{ spot }}</mat-chip-option>
          }
        </mat-chip-listbox>

        @if (chosen() === 'Other') {
          <mat-form-field appearance="outline">
            <mat-label>Where?</mat-label>
            <input matInput
                   [ngModel]="store.studyPlace()"
                   (ngModelChange)="store.studyPlace.set($event)" />
          </mat-form-field>
        }
      </div>
    </ob-shell>
  `,
  styles: `
    .field { display: flex; flex-direction: column; gap: 4px; }

    .row { display: flex; align-items: baseline; justify-content: space-between; }
    .label { font: var(--mat-sys-title-small); color: var(--mat-sys-on-surface-variant); }
    .value { font: var(--mat-sys-title-medium); color: var(--mat-sys-primary); }

    mat-slider { width: 100%; margin-inline: 0; }

    .total {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 16px;
      padding: 12px 16px;
      border-radius: var(--mat-sys-corner-large);
      background: var(--mat-sys-surface-container-high);
    }

    .unit { font: var(--mat-sys-title-medium); color: var(--mat-sys-on-surface); }

    /* Same assist-chip metrics as the date screen's day count. */
    .chip {
      height: 32px;
      display: grid;
      place-items: center;
      padding: 0 16px;
      border-radius: var(--mat-sys-corner-full);
      background: var(--mat-sys-secondary-container);
      color: var(--mat-sys-on-secondary-container);
      font: var(--mat-sys-label-large);
    }

    .spots { display: flex; flex-direction: column; gap: 8px; }

    .label { font: var(--mat-sys-title-small); color: var(--mat-sys-on-surface-variant); }

    mat-form-field { width: 100%; margin-top: 8px; }
  `,
})
export class HoursStep {
  protected readonly store = inject(OnboardingStore);
  protected readonly spots = STUDY_SPOTS;

  /** True once "Other" is picked, which is what reveals the free-text field. */
  protected readonly other = signal(false);

  protected chosen(): string {
    if (this.other()) return 'Other';
    const place = this.store.studyPlace();
    return STUDY_SPOTS.includes(place) ? place : '';
  }

  protected choose(spot: string): void {
    this.other.set(spot === 'Other');
    this.store.studyPlace.set(spot === 'Other' ? '' : spot);
  }
}
