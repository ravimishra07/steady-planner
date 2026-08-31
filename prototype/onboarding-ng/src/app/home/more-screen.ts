import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { MatRippleModule } from '@angular/material/core';
import { BLOCKABLE_APPS, OnboardingStore } from '../onboarding/state';
import { StudyStore } from '../study/study-store';
import { clearDemo, loadDemo } from '../study/demo-data';
import { clockLabel } from '../onboarding/commitments';
import { PACK } from '../onboarding/exam-pack';

/** Settings: the plan's own knobs, and the demo-data switch this build needs. */
@Component({
  selector: 'app-more-screen',
  imports: [MatIconModule, MatRippleModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="group">
      <h2 class="label">Plan</h2>
      <div class="rows">
        <div class="row">
          <mat-icon>school</mat-icon>
          <span class="name">Exam<span class="sub">{{ pack.displayName }} · {{ store.days() }} days left</span></span>
        </div>
        <div class="row">
          <mat-icon>schedule</mat-icon>
          <span class="name">
            Hours
            <span class="sub">{{ store.weekdayHours() }}h weekdays · {{ store.weekendHours() }}h weekends</span>
          </span>
        </div>
        <div class="row">
          <mat-icon>event_busy</mat-icon>
          <span class="name">
            Fixed hours
            <span class="sub">{{ fixedSummary() }}</span>
          </span>
        </div>
        <div class="row">
          <mat-icon>bedtime</mat-icon>
          <span class="name">
            Awake
            <span class="sub">{{ awake() }}</span>
          </span>
        </div>
      </div>
    </section>

    <section class="group">
      <h2 class="label">Focus</h2>
      <div class="rows">
        <button matRipple class="row action" (click)="store.blockApps.set(!store.blockApps())">
          <mat-icon [class.filled]="store.blockApps()">{{ store.blockApps() ? 'lock' : 'lock_open' }}</mat-icon>
          <span class="name">
            Block apps during a session
            <span class="sub">{{ store.blockApps() ? 'on — released when the timer stops' : 'off' }}</span>
          </span>
          <span class="switch" [class.on]="store.blockApps()"><span class="knob"></span></span>
        </button>
      </div>

      <div class="apps">
        @for (a of apps; track a.id) {
          <button matRipple class="app" [class.on]="store.blockedApps().has(a.id)"
                  [disabled]="!store.blockApps()"
                  (click)="store.toggleBlockedApp(a.id)">
            <mat-icon>{{ a.icon }}</mat-icon>
            {{ a.label }}
          </button>
        }
      </div>
    </section>

    <section class="group">
      <h2 class="label">Demo data</h2>
      <p class="lede">
        Three weeks of fabricated history — sittings, revision rounds and
        recall. Nothing here is real study data.
      </p>
      <div class="rows">
        <button matRipple class="row action" (click)="load()">
          <mat-icon>science</mat-icon>
          <span class="name">
            Load demo history
            <span class="sub">{{ loaded() ? 'loaded — reload to regenerate' : 'fills the app as if used for 3 weeks' }}</span>
          </span>
        </button>
        <button matRipple class="row action" (click)="clear()">
          <mat-icon>delete_sweep</mat-icon>
          <span class="name">
            Clear everything
            <span class="sub">back to a fresh account</span>
          </span>
        </button>
      </div>
    </section>

    <section class="group">
      <h2 class="label">Where the numbers come from</h2>
      <ul class="meta">
        @for (line of pack.meta.methodology; track line) { <li>{{ line }}</li> }
        <li>
          Readiness is an estimate: chapters covered, weighted by how much of that is
          still held, plus retention and accuracy — 4:3:3.
        </li>
        <li>
          Revision falls due on an expanding schedule (3, 10, 30, 60 days), pulled in or
          pushed out by how the last sitting went.
        </li>
      </ul>
    </section>
  `,
  styles: `
    :host {
      display: flex;
      flex-direction: column;
      gap: 24px;
      height: 100%;
      overflow-y: auto;
      padding: 16px 16px 24px;
    }

    .group { display: flex; flex-direction: column; gap: 12px; }
    .label { margin: 0; font: var(--mat-sys-title-small); color: var(--mat-sys-on-surface-variant); }
    .lede { margin: 0; font: var(--mat-sys-body-medium); color: var(--mat-sys-on-surface-variant); }

    .rows { display: flex; flex-direction: column; }

    .row {
      display: flex;
      align-items: center;
      gap: 16px;
      min-height: 64px;
      padding: 8px 4px;
      border: none;
      background: transparent;
      color: var(--mat-sys-on-surface);
      text-align: left;
      font: var(--mat-sys-body-large);
    }

    .row.action { cursor: pointer; }
    .row mat-icon { color: var(--mat-sys-on-surface-variant); }
    .row.action mat-icon { color: var(--mat-sys-primary); }
    .name { flex: 1; display: flex; flex-direction: column; gap: 2px; }
    .sub { font: var(--mat-sys-label-medium); color: var(--mat-sys-on-surface-variant); }

    /* An M3 switch, small enough to sit in a settings row. */
    .switch {
      position: relative;
      flex: none;
      width: 52px;
      height: 32px;
      border: 2px solid var(--mat-sys-outline);
      border-radius: var(--mat-sys-corner-full);
      background: var(--mat-sys-surface-container-highest);
      transition: background 120ms, border-color 120ms;
    }

    .switch.on { border-color: transparent; background: var(--mat-sys-primary); }

    .knob {
      position: absolute;
      top: 50%;
      left: 6px;
      width: 16px;
      height: 16px;
      border-radius: 50%;
      background: var(--mat-sys-outline);
      transform: translateY(-50%);
      transition: left 120ms, width 120ms, height 120ms, background 120ms;
    }

    .switch.on .knob {
      left: 26px;
      width: 24px;
      height: 24px;
      background: var(--mat-sys-on-primary);
    }

    .apps { display: flex; flex-wrap: wrap; gap: 8px; }

    .app {
      display: flex;
      align-items: center;
      gap: 6px;
      height: 32px;
      padding: 0 12px 0 8px;
      border: 1px solid var(--mat-sys-outline-variant);
      border-radius: var(--mat-sys-corner-full);
      background: transparent;
      color: var(--mat-sys-on-surface-variant);
      font: var(--mat-sys-label-large);
      cursor: pointer;
    }

    .app.on {
      border-color: transparent;
      background: var(--mat-sys-secondary-container);
      color: var(--mat-sys-on-secondary-container);
    }

    .app:disabled { opacity: .4; cursor: default; }
    .app mat-icon { font-size: 18px; width: 18px; height: 18px; }

    .meta {
      margin: 0;
      padding-left: 20px;
      display: flex;
      flex-direction: column;
      gap: 8px;
      font: var(--mat-sys-body-small);
      color: var(--mat-sys-on-surface-variant);
    }
  `,
})
export class MoreScreen {
  protected readonly store = inject(OnboardingStore);
  protected readonly study = inject(StudyStore);
  protected readonly pack = PACK;
  protected readonly apps = BLOCKABLE_APPS;

  protected readonly loaded = computed(() => this.study.sessions().some((s) => s.id.startsWith('demo-')));

  protected fixedSummary(): string {
    const list = this.store.commitments();
    if (list.length === 0) return 'nothing fixed';
    return list.map((c) => `${c.label} ${clockLabel(c.startMinute)}`).join(' · ');
  }

  protected awake(): string {
    return `${clockLabel(this.store.wakeMinute())} to ${clockLabel(this.store.sleepMinute())}`;
  }

  protected load(): void { loadDemo(this.store, this.study); }
  protected clear(): void { clearDemo(this.store, this.study); }
}
