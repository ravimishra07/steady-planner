import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { MatRippleModule } from '@angular/material/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { FocusStore, clock } from '../focus/focus-store';

interface Destination {
  path: string;
  label: string;
  icon: string;
  description: string;
}

const DESTINATIONS: Destination[] = [
  { path: '/today', label: 'Today', icon: 'calendar_today', description: 'Your schedule and next work' },
  { path: '/syllabus', label: 'Syllabus', icon: 'book_2', description: 'Coverage, order, and scope' },
  { path: '/focus', label: 'Focus', icon: 'timer', description: 'Start or continue a sitting' },
  { path: '/progress', label: 'Progress', icon: 'monitoring', description: 'Pace, retention, and time' },
  { path: '/settings', label: 'Settings', icon: 'settings', description: 'Plan and preferences' },
];

@Component({
  selector: 'app-shell',
  imports: [MatIconModule, MatRippleModule, RouterLink, RouterLinkActive, RouterOutlet],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <a class="skip-link" href="#main-workspace">Skip to main content</a>
    <div class="desktop-shell">
      <aside class="side-nav" aria-label="Primary navigation">
        <a class="brand" routerLink="/today" aria-label="Steadyline home">
          <span class="brand-mark" aria-hidden="true"><mat-icon>timeline</mat-icon></span>
          <span class="brand-copy"><strong>Steadyline</strong><small>Study planner</small></span>
        </a>

        <nav class="destinations">
          @for (destination of destinations; track destination.path) {
            <a
              matRipple
              class="destination"
              [routerLink]="destination.path"
              routerLinkActive="active"
              [routerLinkActiveOptions]="{ exact: true }"
            >
              <span class="destination-icon"><mat-icon>{{ destination.icon }}</mat-icon></span>
              <span class="destination-copy">
                <strong>{{ destination.label }}</strong>
                <small>{{ destination.description }}</small>
              </span>
            </a>
          }
        </nav>

        <a matRipple class="plan-link" routerLink="/onboarding">
          <mat-icon>tune</mat-icon>
          <span><strong>Plan setup</strong><small>Review your starting assumptions</small></span>
        </a>
      </aside>

      <main id="main-workspace" class="workspace" tabindex="-1">
        <router-outlet />
      </main>

      @if (focus.status() === 'running' || focus.status() === 'paused') {
        <button matRipple class="running-session" (click)="openFocus()">
          <span class="session-state"><mat-icon>timer</mat-icon>{{ focus.status() === 'paused' ? 'Paused' : 'Studying' }}</span>
          <strong>{{ focus.target()?.title }}</strong>
          <span class="session-time">{{ remaining() }}</span>
          <mat-icon>arrow_forward</mat-icon>
        </button>
      }

      <nav class="compact-nav" aria-label="Primary navigation">
        @for (destination of destinations; track destination.path) {
          <a
            matRipple
            class="compact-destination"
            [routerLink]="destination.path"
            routerLinkActive="active"
            [routerLinkActiveOptions]="{ exact: true }"
          >
            <span class="compact-pill"><mat-icon>{{ destination.icon }}</mat-icon></span>
            <span>{{ destination.label }}</span>
          </a>
        }
      </nav>
    </div>
  `,
  styles: `
    :host {
      display: block;
      height: 100dvh;
      overflow: hidden;
      background: var(--mat-sys-surface);
      color: var(--mat-sys-on-surface);
    }

    .skip-link {
      position: fixed;
      z-index: 100;
      top: 8px;
      left: 8px;
      padding: 10px 14px;
      border-radius: var(--mat-sys-corner-small);
      background: var(--mat-sys-inverse-surface);
      color: var(--mat-sys-inverse-on-surface);
      font: var(--mat-sys-label-large);
      text-decoration: none;
      transform: translateY(-150%);
    }

    .skip-link:focus { transform: translateY(0); }

    .desktop-shell {
      height: 100%;
      display: grid;
      grid-template-columns: 272px minmax(0, 1fr);
      position: relative;
    }

    .side-nav {
      min-width: 0;
      display: flex;
      flex-direction: column;
      padding: 24px 16px 16px;
      border-right: 1px solid var(--mat-sys-outline-variant);
      background: var(--mat-sys-surface-container-low);
    }

    .brand {
      display: flex;
      align-items: center;
      gap: 12px;
      margin: 0 8px 32px;
      color: var(--mat-sys-on-surface);
      text-decoration: none;
    }

    .brand-mark {
      width: 44px;
      height: 44px;
      display: grid;
      place-items: center;
      flex: none;
      border-radius: var(--mat-sys-corner-large);
      background: var(--mat-sys-primary);
      color: var(--mat-sys-on-primary);
    }

    .brand-copy, .destination-copy, .plan-link span { display: flex; min-width: 0; flex-direction: column; }
    .brand-copy strong { font: var(--mat-sys-title-medium); }
    .brand-copy small, .destination-copy small, .plan-link small { color: var(--mat-sys-on-surface-variant); font: var(--mat-sys-body-small); }

    .destinations { display: flex; flex-direction: column; gap: 4px; }

    .destination {
      min-height: 60px;
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 8px 12px;
      border-radius: var(--mat-sys-corner-large);
      color: var(--mat-sys-on-surface-variant);
      text-decoration: none;
    }

    .destination:hover { background: var(--mat-sys-surface-container-high); }
    .destination.active { background: var(--mat-sys-secondary-container); color: var(--mat-sys-on-secondary-container); }
    .destination-icon { width: 36px; height: 36px; display: grid; place-items: center; flex: none; border-radius: var(--mat-sys-corner-full); }
    .destination.active .destination-icon { background: var(--mat-sys-primary); color: var(--mat-sys-on-primary); }
    .destination.active mat-icon { font-variation-settings: 'FILL' 1, 'wght' 400, 'GRAD' 0, 'opsz' 24; }
    .destination-copy strong { font: var(--mat-sys-label-large); }
    .destination.active .destination-copy small { color: var(--mat-sys-on-secondary-container); }

    .plan-link {
      min-height: 56px;
      display: flex;
      align-items: center;
      gap: 12px;
      margin-top: auto;
      padding: 8px 12px;
      border-radius: var(--mat-sys-corner-large);
      color: var(--mat-sys-on-surface);
      text-decoration: none;
    }

    .plan-link:hover { background: var(--mat-sys-surface-container-high); }
    .plan-link strong { font: var(--mat-sys-label-large); }

    .workspace {
      min-width: 0;
      min-height: 0;
      overflow: hidden;
      background: var(--mat-sys-surface);
    }

    .workspace > router-outlet { display: none; }
    .workspace > :not(router-outlet) { display: block; height: 100%; }

    .running-session {
      position: fixed;
      right: 24px;
      bottom: 24px;
      z-index: 20;
      max-width: min(520px, calc(100vw - 320px));
      min-height: 56px;
      display: grid;
      grid-template-columns: auto minmax(120px, 1fr) auto auto;
      align-items: center;
      gap: 16px;
      padding: 8px 12px 8px 16px;
      border: 1px solid var(--mat-sys-outline-variant);
      border-radius: var(--mat-sys-corner-extra-large);
      background: var(--mat-sys-inverse-surface);
      color: var(--mat-sys-inverse-on-surface);
      box-shadow: var(--mat-sys-level3);
      cursor: pointer;
    }

    .session-state { display: flex; align-items: center; gap: 8px; color: var(--mat-sys-inverse-primary); font: var(--mat-sys-label-medium); }
    .running-session strong { overflow: hidden; text-align: left; text-overflow: ellipsis; white-space: nowrap; font: var(--mat-sys-label-large); }
    .session-time { font: var(--mat-sys-title-medium); font-variant-numeric: tabular-nums; }
    .compact-nav { display: none; }

    @media (max-width: 1120px) {
      .desktop-shell { grid-template-columns: 88px minmax(0, 1fr); }
      .side-nav { align-items: center; padding-inline: 12px; }
      .brand { margin-inline: 0; }
      .brand-copy, .destination-copy, .plan-link span { display: none; }
      .destination, .plan-link { width: 56px; justify-content: center; padding-inline: 0; }
      .destination-icon { width: 40px; }
      .running-session { max-width: calc(100vw - 136px); }
    }

    @media (max-width: 720px) {
      :host { height: 100dvh; }
      .desktop-shell { grid-template-columns: 1fr; grid-template-rows: minmax(0, 1fr) 80px; }
      .side-nav { display: none; }
      .workspace { grid-row: 1; }
      .compact-nav {
        grid-row: 2;
        display: flex;
        align-items: center;
        background: var(--mat-sys-surface-container);
      }
      .compact-destination {
        flex: 1;
        height: 100%;
        display: flex;
        flex-direction: column;
        align-items: center;
        justify-content: center;
        gap: 4px;
        color: var(--mat-sys-on-surface-variant);
        text-decoration: none;
        font: var(--mat-sys-label-medium);
      }
      .compact-pill { width: 56px; height: 32px; display: grid; place-items: center; border-radius: var(--mat-sys-corner-full); }
      .compact-destination.active { color: var(--mat-sys-on-surface); }
      .compact-destination.active .compact-pill { background: var(--mat-sys-secondary-container); color: var(--mat-sys-on-secondary-container); }
      .running-session {
        right: 12px;
        bottom: 92px;
        left: 12px;
        max-width: none;
        grid-template-columns: auto minmax(0, 1fr) auto;
      }
      .session-state { display: none; }
    }

    @media (prefers-reduced-motion: reduce) {
      .skip-link { transition: none; }
    }
  `,
})
export class AppShell {
  protected readonly destinations = DESTINATIONS;
  protected readonly focus = inject(FocusStore);
  private readonly router = inject(Router);
  protected readonly remaining = computed(() => clock(this.focus.remainingSec()));

  protected openFocus(): void { void this.router.navigateByUrl('/focus'); }
}
