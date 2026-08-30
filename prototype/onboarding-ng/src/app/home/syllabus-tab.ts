import { ChangeDetectionStrategy, Component } from '@angular/core';
import { SyllabusBrowser } from '../syllabus/syllabus-browser';

@Component({
  selector: 'app-syllabus-tab',
  imports: [SyllabusBrowser],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="body"><app-syllabus-browser /></div>
  `,
  styles: `
    :host { display: flex; flex-direction: column; height: 100%; }


    .body {
      flex: 1;
      min-height: 0;
      overflow-y: auto;
      overflow-x: hidden;
      padding: 16px 16px 24px;
      display: flex;
      flex-direction: column;
      gap: 24px;
    }
  `,
})
export class SyllabusTab {}
