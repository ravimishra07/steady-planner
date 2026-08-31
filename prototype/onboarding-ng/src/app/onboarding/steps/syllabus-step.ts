import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { MatRippleModule } from '@angular/material/core';
import { Shell } from '../shell';
import { OnboardingStore } from '../state';
import { PACK } from '../exam-pack';
import { SyllabusBrowser } from '../../syllabus/syllabus-browser';

@Component({
  selector: 'ob-syllabus-step',
  imports: [Shell, SyllabusBrowser, MatIconModule, MatRippleModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <ob-shell
      [title]="store.useProvidedSyllabus() ? 'Where are you right now?' : 'Your own syllabus'"
      [progressIndex]="store.progressIndex()"
      [segments]="store.progressSegments"
      (continue)="store.next()">

      <!-- The bundled contents are a starting point, not the only option. -->
      <div class="choices">
        <button matRipple class="choice" [class.on]="store.useProvidedSyllabus()"
                (click)="store.useProvidedSyllabus.set(true)">
          <mat-icon [class.filled]="store.useProvidedSyllabus()">menu_book</mat-icon>
          <span class="choice-text">
            <span class="choice-name">Use the {{ pack.displayName }} syllabus</span>
            <span class="choice-hint">{{ chapterCount }} chapters from the NCERT contents</span>
          </span>
        </button>

        <button matRipple class="choice" [class.on]="!store.useProvidedSyllabus()"
                (click)="store.useProvidedSyllabus.set(false)">
          <mat-icon [class.filled]="!store.useProvidedSyllabus()">edit_note</mat-icon>
          <span class="choice-text">
            <span class="choice-name">Start empty</span>
            <span class="choice-hint">Enter your coaching's own chapters instead</span>
          </span>
        </button>
      </div>

      @if (store.useProvidedSyllabus()) {
        <app-syllabus-browser />
      } @else {
        <div class="blank">
          <mat-icon>playlist_add</mat-icon>
          <p>
            You'll add chapters yourself. Syllabus → Organise has the list, and the plan fills in
            as soon as there is something in it.
          </p>
        </div>
      }
    </ob-shell>
  `,
  styles: `
    app-syllabus-browser { display: block; }
    .choices { display: flex; flex-direction: column; gap: 8px; }

    .choice {
      display: flex;
      align-items: center;
      gap: 16px;
      padding: 16px;
      border: 1px solid var(--mat-sys-outline-variant);
      border-radius: var(--mat-sys-corner-large);
      background: transparent;
      color: var(--mat-sys-on-surface);
      text-align: left;
      cursor: pointer;
    }

    .choice.on {
      border-color: transparent;
      background: var(--mat-sys-secondary-container);
      color: var(--mat-sys-on-secondary-container);
    }

    .choice-text { display: flex; flex-direction: column; gap: 2px; }
    .choice-name { font: var(--mat-sys-title-small); }
    .choice-hint { font: var(--mat-sys-body-small); opacity: .8; }

    .blank {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 8px;
      padding: 24px 16px;
      border: 1px dashed var(--mat-sys-outline-variant);
      border-radius: var(--mat-sys-corner-large);
      text-align: center;
      color: var(--mat-sys-on-surface-variant);
    }

    .blank p { margin: 0; font: var(--mat-sys-body-medium); }
  `,
})
export class SyllabusStep {
  protected readonly store = inject(OnboardingStore);
  protected readonly pack = PACK;
  protected readonly chapterCount = PACK.subjects.reduce(
    (n, s) => n + s.sections.reduce((m, sec) => m + sec.chapters.length, 0),
    0,
  );
}
