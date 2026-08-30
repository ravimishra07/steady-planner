import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { OnboardingStore } from './onboarding/state';
import { AppearanceStep } from './onboarding/steps/appearance-step';
import { ExamStep } from './onboarding/steps/exam-step';
import { CoachingStep } from './onboarding/steps/coaching-step';
import { DateStep } from './onboarding/steps/date-step';
import { ShapeStep } from './onboarding/steps/shape-step';
import { HoursStep } from './onboarding/steps/hours-step';
import { SyllabusStep } from './onboarding/steps/syllabus-step';
import { PlanStep } from './onboarding/steps/plan-step';
import { AppShell } from './home/app-shell';

@Component({
  selector: 'app-root',
  imports: [AppearanceStep, ExamStep, CoachingStep, DateStep, ShapeStep, HoursStep, SyllabusStep, PlanStep, AppShell],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="phone" [class]="'accent-' + store.accent() + ' bg-' + store.appearance()">
      <div class="screen">
        @if (store.started()) {
          <app-shell />
        } @else {
        @switch (store.step()) {
          @case ('appearance') { <ob-appearance-step /> }
          @case ('exam') { <ob-exam-step /> }
          @case ('coaching') { <ob-coaching-step /> }
          @case ('date') { <ob-date-step /> }
          @case ('shape') { <ob-shape-step /> }
          @case ('hours') { <ob-hours-step /> }
          @case ('syllabus') { <ob-syllabus-step /> }
          @case ('plan') { <ob-plan-step /> }
        }
        }
      </div>
    </div>
  `,
  styles: `
    :host { display: block; }

    /* Handset viewport: 390 x 844 at 1x, the box the Compose screens ship into. */
    .phone {
      width: 390px;
      height: 844px;
      overflow: hidden;
      background: var(--mat-sys-surface);
      border: 1px solid rgb(0 0 0 / .25);
    }

    .screen { height: 100%; }
    .screen > * { height: 100%; }
  `,
})
export class App {
  protected readonly store = inject(OnboardingStore);
}
