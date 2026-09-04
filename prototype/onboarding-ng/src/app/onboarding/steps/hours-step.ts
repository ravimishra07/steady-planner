import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatSliderModule } from '@angular/material/slider';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatChipsModule } from '@angular/material/chips';
import { MatInputModule } from '@angular/material/input';
import { Shell } from '../shell';
import { DAY_SHAPES, OnboardingStore, STUDY_SPOTS } from '../state';

@Component({
  selector: 'ob-hours-step',
  imports: [Shell, FormsModule, MatSliderModule, MatFormFieldModule, MatInputModule, MatChipsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <ob-shell
      title="Hours per day"
      ctaLabel="Continue"
      [progressIndex]="store.progressIndex()"
      [segments]="store.progressSegments"
      [continueEnabled]="store.capacityIssues().length === 0"
      (continue)="store.next()">

      <div class="field">
        <span class="label">Start with a schedule</span>
        <div class="presets">
          @for (shape of shapes; track shape.id) {
            <button type="button" [class.on]="store.shapeId() === shape.id" (click)="store.applyShape(shape)">
              <strong>{{ shape.label }}</strong><span>{{ shape.weekday }}h weekdays · {{ shape.weekend }}h weekends</span>
            </button>
          }
        </div>
      </div>

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

      @if (store.capacityIssues().length > 0) {
        <div class="issues" role="alert">
          <strong>The hours do not fit yet</strong>
          @for (issue of store.capacityIssues(); track issue) { <span>{{ issue }}</span> }
          <span>Lower the hours, or go back and change fixed commitments.</span>
        </div>
      }

      <div class="spots">
        <span class="label">Study spot <small>Optional, saved for future reminders</small></span>
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
    .presets { display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px; }
    .presets button { display: flex; min-width: 0; flex-direction: column; gap: 2px; padding: 12px; border: 1px solid var(--mat-sys-outline-variant); border-radius: var(--mat-sys-corner-large); background: transparent; color: var(--mat-sys-on-surface); text-align: left; cursor: pointer; }
    .presets button.on { border-color: transparent; background: var(--mat-sys-secondary-container); color: var(--mat-sys-on-secondary-container); }
    .presets strong { font: var(--mat-sys-label-large); }
    .presets span { font: var(--mat-sys-body-small); }

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
    .spots small { display: block; font: var(--mat-sys-body-small); font-weight: 400; }

    .issues { display: flex; flex-direction: column; gap: 4px; padding: 12px 16px; border-radius: var(--mat-sys-corner-large); background: var(--mat-sys-error-container); color: var(--mat-sys-on-error-container); font: var(--mat-sys-body-medium); }
    .issues strong { font: var(--mat-sys-title-small); }

    .label { font: var(--mat-sys-title-small); color: var(--mat-sys-on-surface-variant); }

    mat-form-field { width: 100%; margin-top: 8px; }
  `,
})
export class HoursStep {
  protected readonly store = inject(OnboardingStore);
  protected readonly spots = STUDY_SPOTS;
  protected readonly shapes = DAY_SHAPES;

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
