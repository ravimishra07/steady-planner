import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { MatRippleModule } from '@angular/material/core';
import { TodayScreen } from './today-screen';
import { SyllabusTab } from './syllabus-tab';
import { ProgressTab } from './progress-tab';
import { FocusScreen } from '../focus/focus-screen';
import { FocusStore, clock } from '../focus/focus-store';
import { SettingsScreen } from './settings-screen';
import { ClassLogScreen } from './class-log-screen';
import { ReviewScreen } from './review-screen';
import { OrganiseScreen } from '../syllabus/organise-screen';

interface Destination { id: string; label: string; icon: string; }

/**
 * Four destinations. Focus is not among them: it is a mode you enter by
 * starting something on Today, and as a tab it had to invent its own copy of
 * the day — two screens each claiming to be the truth about today.
 */
const DESTINATIONS: Destination[] = [
  { id: 'home', label: 'Today', icon: 'calendar_today' },
  { id: 'syllabus', label: 'Syllabus', icon: 'book_2' },
  { id: 'progress', label: 'Progress', icon: 'monitoring' },
  { id: 'settings', label: 'Settings', icon: 'settings' },
];

/** The post-onboarding shell: one screen plus the M3 navigation bar. */
@Component({
  selector: 'app-shell',
  imports: [MatIconModule, MatRippleModule, TodayScreen, SyllabusTab, ProgressTab, SettingsScreen, FocusScreen, OrganiseScreen, ClassLogScreen, ReviewScreen],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="screen">
      @if (reviewing()) {
        <app-review-screen (close)="reviewing.set(false)" />
      } @else if (focusing()) {
        <app-focus-screen (close)="focusing.set(false)" />
      } @else if (loggingClass()) {
        <app-class-log-screen (close)="loggingClass.set(false)" />
      } @else if (organising()) {
        <app-organise-screen (close)="organising.set(false)" />
      } @else if (current() === 'home') {
        <app-today (editPlan)="organising.set(true)" (openFocus)="focusing.set(true)" (logClass)="loggingClass.set(true)" (weekReview)="reviewing.set(true)" (openPlanCheck)="current.set('progress')" />
      } @else if (current() === 'syllabus') {
        <app-syllabus-tab />
      } @else if (current() === 'progress') {
        <app-progress-tab />
      } @else if (current() === 'settings') {
        <app-settings-screen />
      } @else {
        <div class="placeholder">
          <mat-icon>construction</mat-icon>
          <span>{{ label() }} comes next</span>
        </div>
      }
    </div>

    @if (focus.status() === 'running' && !focusing()) {
      <button matRipple class="running-bar" (click)="focusing.set(true)">
        <mat-icon>timer</mat-icon>
        <span class="running-text">{{ focus.target()?.title }}</span>
        <span class="running-time">{{ remaining() }}</span>
      </button>
    }

    <nav class="navbar">
      @for (d of destinations; track d.id) {
        <button matRipple class="dest" [class.on]="current() === d.id" (click)="current.set(d.id)">
          <span class="pill"><mat-icon [class.filled]="current() === d.id">{{ d.icon }}</mat-icon></span>
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


    /* A running timer is never hidden behind a tab. */
    .running-bar {
      flex: none;
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 10px 16px;
      border: none;
      background: var(--mat-sys-secondary-container);
      color: var(--mat-sys-on-secondary-container);
      cursor: pointer;
    }

    .running-text {
      flex: 1;
      min-width: 0;
      overflow: hidden;
      white-space: nowrap;
      text-overflow: ellipsis;
      text-align: left;
      font: var(--mat-sys-label-large);
    }

    .running-time { font: var(--mat-sys-title-small); font-variant-numeric: tabular-nums; }
  `,
})
export class AppShell {
  protected readonly destinations = DESTINATIONS;
  protected readonly current = signal('home');
  protected readonly loggingClass = signal(false);
  protected readonly focusing = signal(false);
  protected readonly reviewing = signal(false);
  protected readonly organising = signal(false);
  protected readonly focus = inject(FocusStore);

  protected remaining(): string {
    return clock(this.focus.remainingSec());
  }

  protected label(): string {
    return DESTINATIONS.find((d) => d.id === this.current())!.label;
  }
}
