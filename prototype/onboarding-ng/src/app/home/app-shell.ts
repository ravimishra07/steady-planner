import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { MatRippleModule } from '@angular/material/core';
import { TodayScreen } from './today-screen';
import { SyllabusTab } from './syllabus-tab';
import { ProgressTab } from './progress-tab';
import { MoreScreen } from './more-screen';

interface Destination { id: string; label: string; icon: string; }

const DESTINATIONS: Destination[] = [
  { id: 'home', label: 'Today', icon: 'today' },
  { id: 'syllabus', label: 'Syllabus', icon: 'menu_book' },
  { id: 'progress', label: 'Progress', icon: 'insights' },
  { id: 'more', label: 'More', icon: 'more_horiz' },
];

/** The post-onboarding shell: one screen plus the M3 navigation bar. */
@Component({
  selector: 'app-shell',
  imports: [MatIconModule, MatRippleModule, TodayScreen, SyllabusTab, ProgressTab, MoreScreen],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="screen">
      @if (current() === 'home') {
        <app-today />
      } @else if (current() === 'syllabus') {
        <app-syllabus-tab />
      } @else if (current() === 'progress') {
        <app-progress-tab />
      } @else if (current() === 'more') {
        <app-more-screen />
      } @else {
        <div class="placeholder">
          <mat-icon>construction</mat-icon>
          <span>{{ label() }} comes next</span>
        </div>
      }
    </div>

    <nav class="navbar">
      @for (d of destinations; track d.id) {
        <button matRipple class="dest" [class.on]="current() === d.id" (click)="current.set(d.id)">
          <span class="pill"><mat-icon>{{ d.icon }}</mat-icon></span>
          <span class="dest-label">{{ d.label }}</span>
        </button>
      }
    </nav>
  `,
  styles: `
    :host {
      display: flex;
      flex-direction: column;
      height: 100%;
      background: var(--mat-sys-surface);
      color: var(--mat-sys-on-surface);
    }

    .screen { flex: 1; min-height: 0; }

    .placeholder {
      height: 100%;
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      gap: 8px;
      color: var(--mat-sys-on-surface-variant);
      font: var(--mat-sys-body-large);
    }

    /* M3 navigation bar: 80dp tall, active indicator pill behind the icon. */
    .navbar {
      flex: none;
      height: 80px;
      display: flex;
      align-items: center;
      background: var(--mat-sys-surface-container);
    }

    .dest {
      flex: 1;
      height: 100%;
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      gap: 4px;
      border: none;
      background: transparent;
      color: var(--mat-sys-on-surface-variant);
      cursor: pointer;
    }

    .pill {
      width: 64px;
      height: 32px;
      display: grid;
      place-items: center;
      border-radius: var(--mat-sys-corner-full);
    }

    .dest.on .pill {
      background: var(--mat-sys-secondary-container);
      color: var(--mat-sys-on-secondary-container);
    }

    .dest.on .dest-label { color: var(--mat-sys-on-surface); }

    .dest-label { font: var(--mat-sys-label-medium); }
  `,
})
export class AppShell {
  protected readonly destinations = DESTINATIONS;
  protected readonly current = signal('home');

  protected label(): string {
    return DESTINATIONS.find((d) => d.id === this.current())!.label;
  }
}
