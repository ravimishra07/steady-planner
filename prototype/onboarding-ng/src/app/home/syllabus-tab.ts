import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import { SyllabusBrowser } from '../syllabus/syllabus-browser';
import { OrganiseScreen } from '../syllabus/organise-screen';
import { MatIconModule } from '@angular/material/icon';
import { MatRippleModule } from '@angular/material/core';

@Component({
  selector: 'app-syllabus-tab',
  imports: [SyllabusBrowser, OrganiseScreen, MatIconModule, MatRippleModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (organising()) {
      <app-organise-screen (close)="organising.set(false)" />
    } @else {
      <header class="bar">
        <h1 class="bar-title">Syllabus</h1>
        <button matRipple class="icon-btn" (click)="organising.set(true)" aria-label="Organise">
          <mat-icon>tune</mat-icon>
        </button>
      </header>
      <div class="body"><app-syllabus-browser /></div>
    }
  `,
  styles: `
    :host { display: flex; flex-direction: column; height: 100%; }

    .bar { flex: none; display: flex; align-items: center; gap: 8px; height: 56px; padding: 0 4px 0 16px; }
    .bar-title { flex: 1; margin: 0; font: var(--mat-sys-title-large); }

    .icon-btn {
      display: grid;
      place-items: center;
      width: 48px;
      height: 48px;
      flex: none;
      border: none;
      border-radius: var(--mat-sys-corner-full);
      background: transparent;
      color: var(--mat-sys-on-surface-variant);
      cursor: pointer;
    }


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
export class SyllabusTab {
  protected readonly organising = signal(false);
}
