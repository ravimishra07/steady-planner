import { ChangeDetectionStrategy, Component } from '@angular/core';
import { SyllabusBrowser } from '../syllabus/syllabus-browser';

@Component({
  selector: 'app-syllabus-tab',
  imports: [SyllabusBrowser],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <header class="bar"><span>Syllabus</span></header>
    <div class="body"><app-syllabus-browser /></div>
  `,
  styles: `
    :host { display: flex; flex-direction: column; height: 100%; }

    .bar {
      flex: none;
      height: 64px;
      display: flex;
      align-items: center;
      padding: 0 16px;
      font: var(--mat-sys-title-large);
      color: var(--mat-sys-on-surface);
    }

    .body {
      flex: 1;
      min-height: 0;
      overflow-y: auto;
      overflow-x: hidden;
      padding: 0 16px 24px;
      display: flex;
      flex-direction: column;
      gap: 24px;
    }
  `,
})
export class SyllabusTab {}
