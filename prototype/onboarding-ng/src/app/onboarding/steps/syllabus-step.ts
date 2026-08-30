import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { Shell } from '../shell';
import { OnboardingStore } from '../state';
import { SyllabusBrowser } from '../../syllabus/syllabus-browser';

@Component({
  selector: 'ob-syllabus-step',
  imports: [Shell, SyllabusBrowser],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <ob-shell
      title="Where are you right now?"
      [progressIndex]="store.progressIndex()"
      [segments]="store.progressSegments"
      (continue)="store.next()">
      <app-syllabus-browser />
    </ob-shell>
  `,
  styles: `app-syllabus-browser { display: block; }`,
})
export class SyllabusStep {
  protected readonly store = inject(OnboardingStore);
}
