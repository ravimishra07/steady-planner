import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import { MatRippleModule } from '@angular/material/core';
import { OnboardingStore, startOfToday } from '../onboarding/state';
import { StudyStore, dateKey } from '../study/study-store';
import { RECALLS, Recall } from '../study/retention';
import { DayPlanner } from '../home/day-planner';
import { StudyBlock } from '../home/scheduler';
import { FocusStore, FocusTarget, clock } from './focus-store';

/** Overrides offered when the planned length is not the right length. */
const LENGTHS = [25, 50, 90];

/**
 * Focus: one decision per state, never two. Idle asks only "start this?",
 * running shows only the clock, done asks only how it went. The timer is the
 * app's input — finishing it writes the sitting, ticks the subtopic and
 * schedules the next revision, so nothing has to be logged by hand after.
 */
@Component({
  selector: 'app-focus-screen',
  imports: [MatIconModule, MatRippleModule, FormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @switch (focus.status()) {
      @case ('idle') {
        <section class="stage">
          @if (suggestion(); as s) {
            <span class="eyebrow">{{ eyebrow() }}</span>

            <h1 class="topic">{{ s.title }}</h1>
            <p class="context">{{ s.context }}</p>

            <div class="lengths">
              @for (m of lengths(); track m) {
                <button matRipple class="length" [class.on]="minutes() === m" (click)="minutes.set(m)">
                  {{ m }}m
                </button>
              }
            </div>

            <button matRipple class="go" (click)="start(s)">
              <mat-icon>play_arrow</mat-icon>
              Start {{ minutes() }} min
            </button>

            @if (warning(); as w) {
              <p class="warn"><mat-icon>info</mat-icon>{{ w }}</p>
            }

            <button matRipple class="link" (click)="skipSuggestion()">Something else</button>
          } @else {
            <mat-icon class="big-icon">check_circle</mat-icon>
            <h1 class="topic">Day's plan is done</h1>
            <p class="context">{{ focus.sittingsToday() }} sittings logged today.</p>
            <button matRipple class="go ghost" (click)="startFree()">
              <mat-icon>timer</mat-icon>
              Free session
            </button>
          }
        </section>

        <footer class="foot">
          <span class="foot-stat"><b>{{ focus.sittingsToday() }}</b> today</span>
          <span class="foot-stat"><b>{{ hours(study.minutesOn(todayKey())) }}</b> logged</span>
          <button matRipple class="foot-stat toggle" [class.on]="store.blockApps()"
                  (click)="store.blockApps.set(!store.blockApps())">
            <mat-icon>{{ store.blockApps() ? 'lock' : 'lock_open' }}</mat-icon>
            {{ store.blockApps() ? 'Blocking on' : 'Blocking off' }}
          </button>
        </footer>
      }

      @case ('done') {
        <section class="stage">
          <span class="eyebrow">{{ spent() }} done</span>
          <h1 class="topic">{{ focus.target()?.title }}</h1>

          @if (focus.spentMinutes() < 1) {
            <p class="context">Under a minute — nothing worth logging.</p>
            <button matRipple class="go ghost" (click)="focus.discard()">
              <mat-icon>close</mat-icon>
              Close
            </button>
          } @else {
          <span class="ask">How did it go?</span>
          <div class="recalls">
            @for (r of recalls; track r.id) {
              <button matRipple class="recall" [class.on]="recall() === r.id" (click)="recall.set(r.id)">
                <mat-icon>{{ r.icon }}</mat-icon>
                {{ r.label }}
              </button>
            }
          </div>

          @if (focus.target()?.task === 'Practice') {
            <div class="score">
              <label>
                <span>Attempted</span>
                <input type="number" min="0" [ngModel]="attempted()" (ngModelChange)="attempted.set(+$event)" />
              </label>
              <label>
                <span>Correct</span>
                <input type="number" min="0" [ngModel]="correct()" (ngModelChange)="correct.set(+$event)" />
              </label>
            </div>
          }

          <button matRipple class="go" (click)="save()">
            <mat-icon>check</mat-icon>
            Log {{ spent() }}
          </button>
          <button matRipple class="link" (click)="focus.discard()">Discard</button>
          }
        </section>
      }

      @default {
        <!-- Running or paused: the clock, and nothing competing with it. -->
        <section class="stage running">
          <span class="eyebrow">{{ focus.target()?.task }}</span>
          <h1 class="topic small">{{ focus.target()?.title }}</h1>

          <div class="dial">
            <svg viewBox="0 0 240 240" aria-hidden="true">
              <circle class="dial-track" cx="120" cy="120" r="108" />
              <circle class="dial-fill" cx="120" cy="120" r="108"
                      [attr.stroke-dasharray]="dialLength"
                      [attr.stroke-dashoffset]="dialLength * focus.progress()" />
            </svg>
            <span class="dial-text">
              <span class="time">{{ time() }}</span>
              <span class="of">of {{ Math.round(focus.durationSec() / 60) }} min</span>
            </span>
          </div>

          <div class="controls">
            <button matRipple class="control" (click)="focus.extend(5)">
              <mat-icon>more_time</mat-icon>
              <span>+5 min</span>
            </button>
            <button matRipple class="control primary" (click)="toggle()">
              <mat-icon>{{ focus.status() === 'paused' ? 'play_arrow' : 'pause' }}</mat-icon>
              <span>{{ focus.status() === 'paused' ? 'Resume' : 'Pause' }}</span>
            </button>
            <button matRipple class="control" (click)="focus.stop()">
              <mat-icon>stop</mat-icon>
              <span>End</span>
            </button>
          </div>

          <p class="blocking-note">
            <mat-icon>{{ blocking() ? 'lock' : 'lock_open' }}</mat-icon>
            {{ blocking() ? 'Distracting apps blocked until this ends' : 'App blocking is off' }}
          </p>
        </section>
      }
    }
  `,
  styles: `
    :host {
      display: flex;
      flex-direction: column;
      height: 100%;
      padding: 16px;
      background: var(--mat-sys-surface);
      color: var(--mat-sys-on-surface);
    }

    .stage {
      flex: 1;
      min-height: 0;
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      gap: 8px;
      text-align: center;
    }

    .eyebrow {
      font: var(--mat-sys-label-large);
      color: var(--mat-sys-primary);
      text-transform: lowercase;
    }

    .topic { margin: 4px 0 0; font: var(--mat-sys-headline-medium); }
    .topic.small { font: var(--mat-sys-title-medium); color: var(--mat-sys-on-surface-variant); }
    .context { margin: 0 0 8px; font: var(--mat-sys-body-medium); color: var(--mat-sys-on-surface-variant); }
    .big-icon { font-size: 48px; width: 48px; height: 48px; color: var(--mat-sys-primary); }

    .lengths { display: flex; gap: 8px; margin: 16px 0 8px; }

    .length {
      min-width: 64px;
      height: 40px;
      border: 1px solid var(--mat-sys-outline-variant);
      border-radius: var(--mat-sys-corner-full);
      background: transparent;
      color: var(--mat-sys-on-surface);
      font: var(--mat-sys-label-large);
      cursor: pointer;
    }

    .length.on {
      border-color: transparent;
      background: var(--mat-sys-secondary-container);
      color: var(--mat-sys-on-secondary-container);
    }

    /* One primary action per state. */
    .go {
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 8px;
      width: 100%;
      max-width: 280px;
      height: 56px;
      margin-top: 8px;
      border: none;
      border-radius: var(--mat-sys-corner-full);
      background: var(--mat-sys-primary);
      color: var(--mat-sys-on-primary);
      font: var(--mat-sys-title-medium);
      cursor: pointer;
    }

    .go.ghost {
      background: transparent;
      box-shadow: inset 0 0 0 1px var(--mat-sys-outline);
      color: var(--mat-sys-primary);
    }

    .link {
      margin-top: 4px;
      padding: 8px 12px;
      border: none;
      background: transparent;
      color: var(--mat-sys-primary);
      font: var(--mat-sys-label-large);
      cursor: pointer;
    }

    .warn {
      display: flex;
      align-items: center;
      gap: 6px;
      margin: 12px 0 0;
      font: var(--mat-sys-label-medium);
      color: var(--mat-sys-on-surface-variant);
    }

    .warn mat-icon { font-size: 16px; width: 16px; height: 16px; }

    /* Dial */
    .dial { position: relative; width: 240px; height: 240px; margin: 16px 0; }
    .dial svg { width: 100%; height: 100%; transform: rotate(-90deg); }
    .dial-track { fill: none; stroke: var(--mat-sys-surface-container-highest); stroke-width: 10; }

    .dial-fill {
      fill: none;
      stroke: var(--mat-sys-primary);
      stroke-width: 10;
      stroke-linecap: round;
      transition: stroke-dashoffset 900ms linear;
    }

    .dial-text {
      position: absolute;
      inset: 0;
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      gap: 4px;
    }

    .time { font: var(--mat-sys-display-medium); font-variant-numeric: tabular-nums; }
    .of { font: var(--mat-sys-label-medium); color: var(--mat-sys-on-surface-variant); }

    .controls { display: flex; align-items: center; gap: 12px; margin-top: 8px; }

    .control {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 4px;
      width: 76px;
      padding: 10px 0;
      border: none;
      border-radius: var(--mat-sys-corner-large);
      background: var(--mat-sys-surface-container-high);
      color: var(--mat-sys-on-surface-variant);
      font: var(--mat-sys-label-medium);
      cursor: pointer;
    }

    .control.primary { background: var(--mat-sys-secondary-container); color: var(--mat-sys-on-secondary-container); }

    .blocking-note {
      display: flex;
      align-items: center;
      gap: 6px;
      margin: 16px 0 0;
      font: var(--mat-sys-label-medium);
      color: var(--mat-sys-on-surface-variant);
    }

    .blocking-note mat-icon { font-size: 16px; width: 16px; height: 16px; }

    /* Done */
    .ask { margin-top: 16px; font: var(--mat-sys-title-small); color: var(--mat-sys-on-surface-variant); }
    .recalls { display: flex; gap: 8px; margin: 4px 0 8px; }

    .recall {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 2px;
      width: 88px;
      padding: 12px 4px;
      border: 1px solid var(--mat-sys-outline-variant);
      border-radius: var(--mat-sys-corner-large);
      background: transparent;
      color: var(--mat-sys-on-surface-variant);
      font: var(--mat-sys-label-medium);
      cursor: pointer;
    }

    .recall.on {
      border-color: transparent;
      background: var(--mat-sys-secondary-container);
      color: var(--mat-sys-on-secondary-container);
    }

    .score { display: flex; gap: 8px; margin: 8px 0; }
    .score label { display: flex; flex-direction: column; gap: 4px; font: var(--mat-sys-label-small); color: var(--mat-sys-on-surface-variant); }

    .score input {
      width: 100px;
      height: 40px;
      padding: 0 12px;
      border: 1px solid var(--mat-sys-outline);
      border-radius: var(--mat-sys-corner-small);
      background: transparent;
      color: var(--mat-sys-on-surface);
      font: var(--mat-sys-body-medium);
      text-align: center;
    }

    /* Idle footer */
    .foot {
      flex: none;
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 8px;
      padding-top: 16px;
      border-top: 1px solid var(--mat-sys-outline-variant);
    }

    .foot-stat {
      display: flex;
      align-items: center;
      gap: 6px;
      font: var(--mat-sys-label-medium);
      color: var(--mat-sys-on-surface-variant);
    }

    .foot-stat b { color: var(--mat-sys-on-surface); font-weight: 600; }
    .foot-stat.on { color: var(--mat-sys-primary); }

    .toggle {
      padding: 6px 10px;
      border: none;
      border-radius: var(--mat-sys-corner-full);
      background: transparent;
      cursor: pointer;
    }

    .toggle.on { background: var(--mat-sys-secondary-container); color: var(--mat-sys-on-secondary-container); }
    .foot-stat mat-icon { font-size: 16px; width: 16px; height: 16px; }
  `,
})
export class FocusScreen {
  protected readonly focus = inject(FocusStore);
  protected readonly study = inject(StudyStore);
  private readonly planner = inject(DayPlanner);
  protected readonly store = inject(OnboardingStore);

  protected readonly Math = Math;
  protected readonly recalls = RECALLS;
  protected readonly dialLength = 2 * Math.PI * 108;

  protected readonly recall = signal<Recall>('okay');
  protected readonly attempted = signal(0);
  protected readonly correct = signal(0);
  protected readonly minutes = signal(0);

  /** Blocks the user has waved past this session, so "something else" works. */
  private readonly passed = signal<ReadonlySet<string>>(new Set());

  protected todayKey(): string { return dateKey(startOfToday()); }

  /** Whatever the plan says to do now. The tab never asks the user to choose. */
  protected readonly suggestion = computed<StudyBlock | null>(() => {
    const now = new Date();
    const minuteOfDay = now.getHours() * 60 + now.getMinutes();
    const passed = this.passed();
    const owed = this.planner.remainingToday().filter((b) => !passed.has(key(b)));
    if (owed.length === 0) return null;

    const current = owed.find(
      (b) => minuteOfDay >= b.startMinute && minuteOfDay < b.startMinute + b.minutes,
    );
    return current ?? owed.find((b) => b.startMinute >= minuteOfDay) ?? owed[0];
  });

  /** Says why this block and not another. */
  protected eyebrow(): string {
    const s = this.suggestion();
    if (!s) return '';
    if (s.overdue !== undefined && s.overdue > 0) return `${s.overdue} days overdue`;
    const now = new Date().getHours() * 60 + new Date().getMinutes();
    if (now >= s.startMinute && now < s.startMinute + s.minutes) return 'on now';
    return 'up next';
  }

  /** Warn before a session that would run into something already fixed. */
  protected warning(): string | null {
    const now = new Date().getHours() * 60 + new Date().getMinutes();
    const fixed = this.planner.nextFixed(now);
    if (!fixed) return null;
    const gap = fixed.startMinute - now;
    if (gap <= 0 || gap >= this.minutes()) return null;
    return `${fixed.kind === 'fixed' ? fixed.title : 'Something'} starts in ${gap} min.`;
  }

  /** The planned length first, then the standard overrides. */
  protected readonly lengths = computed(() => {
    const planned = this.suggestion()?.minutes;
    const set = new Set<number>(planned ? [planned] : []);
    LENGTHS.forEach((m) => set.add(m));
    return [...set].sort((a, b) => a - b);
  });

  constructor() {
    // Default the length to whatever the plan asked for.
    queueMicrotask(() => this.minutes.set(this.suggestion()?.minutes ?? 50));
  }

  /** "1 minutes" is the kind of thing that makes an app feel unfinished. */
  protected spent(): string {
    const m = this.focus.spentMinutes();
    return m === 1 ? '1 minute' : `${m} minutes`;
  }

  protected time(): string {
    return clock(this.focus.remainingSec());
  }

  protected start(block: StudyBlock): void {
    this.focus.start(toTarget(block), this.minutes() || block.minutes);
  }

  protected startFree(): void {
    const target: FocusTarget = {
      chapterId: '',
      title: 'Free session',
      context: 'Unplanned',
      task: 'Learn',
      minutes: 50,
    };
    this.focus.start(target, 50);
  }

  protected skipSuggestion(): void {
    const s = this.suggestion();
    if (!s) return;
    this.passed.set(new Set(this.passed()).add(key(s)));
    queueMicrotask(() => this.minutes.set(this.suggestion()?.minutes ?? 50));
  }

  protected toggle(): void {
    this.focus.status() === 'paused' ? this.focus.resume() : this.focus.pause();
  }

  protected save(): void {
    this.focus.finish(this.recall(), this.attempted(), this.correct());
    this.recall.set('okay');
    this.attempted.set(0);
    this.correct.set(0);
    queueMicrotask(() => this.minutes.set(this.suggestion()?.minutes ?? 50));
  }

  /** Blocking follows the session: on while it runs, off the moment it stops. */
  protected blocking(): boolean {
    return this.focus.status() === 'running' && this.store.blockApps();
  }

  protected hours(minutes: number): string {
    if (minutes <= 0) return '0m';
    const h = Math.floor(minutes / 60);
    const m = minutes % 60;
    if (h === 0) return `${m}m`;
    return m === 0 ? `${h}h` : `${h}h ${m}m`;
  }
}

function toTarget(block: StudyBlock): FocusTarget {
  return {
    chapterId: block.chapterId,
    subtopicId: block.subtopicId,
    title: block.title,
    context: block.context,
    task: block.task,
    minutes: block.minutes,
  };
}

function key(block: StudyBlock): string {
  return `${block.chapterId}|${block.task}|${block.subtopicId ?? ''}`;
}
