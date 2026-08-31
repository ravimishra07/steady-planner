import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { MatRippleModule } from '@angular/material/core';
import { TodayScreen } from './today-screen';
import { SyllabusTab } from './syllabus-tab';
import { ProgressTab } from './progress-tab';
import { FocusScreen } from '../focus/focus-screen';
import { FocusStore, clock } from '../focus/focus-store';
import { SettingsScreen } from './settings-screen';

interface Destination { id: string; label: string; icon: string; }

/**
 * Focus sits in the middle: it is the thing the app is opened to do. The
 * glyphs are all plain Material Symbols of the same weight — no decorated
 * ones, since a sparkle next to a book and a stopwatch reads as three sets.
 */
const DESTINATIONS: Destination[] = [
  { id: 'home', label: 'Today', icon: 'calendar_today' },
  { id: 'syllabus', label: 'Syllabus', icon: 'book_2' },
  { id: 'focus', label: 'Focus', icon: 'timer' },
  { id: 'progress', label: 'Progress', icon: 'monitoring' },
  { id: 'settings', label: 'Settings', icon: 'settings' },
];

/** The post-onboarding shell: one screen plus the M3 navigation bar. */
@Component({
  selector: 'app-shell',
  imports: [MatIconModule, MatRippleModule, TodayScreen, SyllabusTab, ProgressTab, SettingsScreen, FocusScreen],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="screen">
      @if (current() === 'home') {
        <app-today />
      } @else if (current() === 'syllabus') {
        <app-syllabus-tab />
      } @else if (current() === 'focus') {
        <app-focus-screen />
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

    @if (focus.status() === 'running' && current() !== 'focus') {
      <button matRipple class="running-bar" (click)="current.set('focus')">
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

    /* Five destinations on a 390dp phone: the pill has to give up width. */
    .pill { width: 56px; }

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
  protected readonly focus = inject(FocusStore);

  protected remaining(): string {
    return clock(this.focus.remainingSec());
  }

  protected label(): string {
    return DESTINATIONS.find((d) => d.id === this.current())!.label;
  }
}
