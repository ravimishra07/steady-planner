import { ChangeDetectionStrategy, Component, effect, inject } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { Router } from '@angular/router';
import { OnboardingStore } from '../onboarding/state';
import { AppearanceStep } from '../onboarding/steps/appearance-step';
import { CoachingStep } from '../onboarding/steps/coaching-step';
import { CommitmentsStep } from '../onboarding/steps/commitments-step';
import { DateStep } from '../onboarding/steps/date-step';
import { ExamStep } from '../onboarding/steps/exam-step';
import { HoursStep } from '../onboarding/steps/hours-step';
import { PlanStep } from '../onboarding/steps/plan-step';
import { ShapeStep } from '../onboarding/steps/shape-step';
import { SyllabusStep } from '../onboarding/steps/syllabus-step';

@Component({
  selector: 'app-onboarding-page',
  imports: [
    AppearanceStep,
    DatePipe,
    DecimalPipe,
    CoachingStep,
    CommitmentsStep,
    DateStep,
    ExamStep,
    HoursStep,
    PlanStep,
    ShapeStep,
    SyllabusStep,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <main class="onboarding-workspace">
      <section class="onboarding-flow" aria-label="Set up your study plan">
        @switch (store.step()) {
          @case ('appearance') { <ob-appearance-step /> }
          @case ('exam') { <ob-exam-step /> }
          @case ('coaching') { <ob-coaching-step /> }
          @case ('commitments') { <ob-commitments-step /> }
          @case ('date') { <ob-date-step /> }
          @case ('shape') { <ob-shape-step /> }
          @case ('hours') { <ob-hours-step /> }
          @case ('syllabus') { <ob-syllabus-step /> }
          @case ('plan') { <ob-plan-step /> }
        }
      </section>
      <aside class="setup-context" aria-label="Plan preview">
        <span class="eyebrow">Plan preview</span>
        <h1>{{ store.examTemplate().displayName }}</h1>
        <dl>
          <div><dt>Target</dt><dd>{{ store.examDate() | date:'d MMM y' }}</dd></div>
          <div><dt>Study time</dt><dd>{{ store.dailyAverage() | number:'1.0-1' }}h/day</dd></div>
          <div><dt>Coverage</dt><dd>{{ store.coverage() }}%</dd></div>
          <div><dt>Subjects</dt><dd>{{ store.subjects().length }}</dd></div>
        </dl>
        <p>This preview updates as you make decisions. You can change everything later.</p>
      </aside>
    </main>
  `,
  styles: `
    :host { display: block; min-height: 100%; }

    .onboarding-workspace {
      min-height: 100dvh;
      display: grid;
      grid-template-columns: minmax(390px, 620px) minmax(280px, 420px);
      justify-content: center;
      gap: clamp(32px, 6vw, 96px);
      padding: clamp(24px, 5vw, 72px);
      background: var(--mat-sys-surface);
      color: var(--mat-sys-on-surface);
    }

    .onboarding-flow {
      min-width: 0;
      min-height: min(760px, calc(100dvh - 48px));
      overflow: hidden;
      border: 1px solid var(--mat-sys-outline-variant);
      border-radius: var(--mat-sys-corner-extra-large);
      background: var(--mat-sys-surface-container-lowest);
    }

    .onboarding-flow > * { height: 100%; min-height: inherit; }

    .setup-context {
      align-self: center;
      max-width: 380px;
      padding-block: 32px;
    }

    .eyebrow {
      color: var(--mat-sys-primary);
      font: var(--mat-sys-label-large);
      letter-spacing: .04em;
      text-transform: uppercase;
    }

    h1 { margin: 12px 0 32px; font: var(--mat-sys-display-small); }
    dl { margin: 0; border-block: 1px solid var(--mat-sys-outline-variant); }
    dl div { display: flex; justify-content: space-between; gap: 24px; padding: 16px 0; }
    dl div + div { border-top: 1px solid var(--mat-sys-outline-variant); }
    dt { color: var(--mat-sys-on-surface-variant); font: var(--mat-sys-body-medium); }
    dd { margin: 0; text-align: right; font: var(--mat-sys-title-medium); }
    p { max-width: 34ch; margin: 24px 0 0; color: var(--mat-sys-on-surface-variant); font: var(--mat-sys-body-medium); }

    @media (max-width: 900px) {
      .onboarding-workspace { grid-template-columns: minmax(0, 620px); padding: 0; }
      .onboarding-flow { min-height: 100dvh; border: 0; border-radius: 0; }
      .setup-context { display: none; }
    }
  `,
})
export class OnboardingPage {
  protected readonly store = inject(OnboardingStore);
  private readonly router = inject(Router);

  constructor() {
    this.store.started.set(false);
    effect(() => {
      if (this.store.started()) void this.router.navigateByUrl('/today');
    });
  }
}
