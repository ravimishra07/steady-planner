import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';
import { MatRippleModule } from '@angular/material/core';
import { OnboardingStore, addDays, startOfToday } from '../onboarding/state';
import { ALL_CHAPTERS, Chapter, PACK, Subject, chapterIsDone } from '../onboarding/exam-pack';
import { StudyStore } from '../study/study-store';
import { RetentionState } from '../study/retention';

/**
 * Weeks the consistency grid shows. Long enough to read as a habit, short
 * enough that a new account is not mostly dead squares.
 */
const HEATMAP_WEEKS = 18;
/** Days in the hours chart. */
const HOURS_DAYS = 14;

interface Depth { id: string; name: string; r3: number; r2: number; r1: number; learnt: number; total: number; }

/**
 * Progress: not one percentage, but the four questions an aspirant actually
 * has — am I consistent, am I fast enough, how deep have I gone, and what am
 * I getting wrong.
 */
@Component({
  selector: 'app-progress-tab',
  imports: [MatIconModule, MatRippleModule, DatePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <!-- Readiness: the one composite number, stated as the estimate it is. -->
    <section class="hero">
      <div class="ring-wrap">
        <svg class="ring" viewBox="0 0 120 120" aria-hidden="true">
          <circle class="ring-track" cx="60" cy="60" r="52" />
          <circle class="ring-fill" cx="60" cy="60" r="52"
                  [attr.stroke-dasharray]="circumference"
                  [attr.stroke-dashoffset]="circumference * (1 - readiness() / 100)" />
        </svg>
        <span class="ring-text">
          <span class="ring-value">{{ readiness() }}</span>
          <span class="ring-unit">ready</span>
        </span>
      </div>

      <dl class="hero-side">
        <div class="hero-stat">
          <dt>Days left</dt>
          <dd>{{ store.days() }}</dd>
        </div>
        <div class="hero-stat">
          <dt>Learnt</dt>
          <dd>{{ rounds().learned }}<span class="of">/{{ inPlay() }}</span></dd>
        </div>
        <div class="hero-stat">
          <dt>Still held</dt>
          <dd>{{ held() }}</dd>
        </div>
      </dl>
    </section>

    <!-- Pace. The single most useful sentence on the screen. -->
    <section class="verdict" [class.behind]="behind()" [class.unknown]="daysNeeded() === null">
      <div class="verdict-row">
        <mat-icon>{{ verdictIcon() }}</mat-icon>
        <span class="verdict-text">
          <span class="verdict-head">{{ verdictHead() }}</span>
          <span class="verdict-sub">{{ verdictSub() }}</span>
        </span>
      </div>

      <!-- Always reachable: parking is reversible only if you can get back in. -->
      @if (daysNeeded() !== null) {
        <button matRipple class="verdict-cta" (click)="rebalanceOpen.set(true)">
          <mat-icon>tune</mat-icon>
          {{ behind() ? 'Rebalance the plan' : 'Adjust the plan' }}
        </button>
      }
    </section>

    @if (store.parkedChapters().size > 0) {
      <button matRipple class="parked-chip" (click)="rebalanceOpen.set(true)">
        <mat-icon>inventory_2</mat-icon>
        {{ store.parkedChapters().size }} chapters parked
      </button>
    }

    <!-- Retention: the half of progress a tick box cannot show. -->
    <section class="group">
      <h2 class="label">What you still hold</h2>

      <div class="card">
        <div class="stat-row">
          <span class="stat">
            <span class="stat-value">{{ held() }}</span>
            <span class="stat-caption">holding</span>
          </span>
          <span class="stat">
            <span class="stat-value">{{ counts().fresh }}</span>
            <span class="stat-caption">fresh</span>
          </span>
          <span class="stat">
            <span class="stat-value" [class.down]="outstanding() > 0">{{ outstanding() }}</span>
            <span class="stat-caption">to revise</span>
          </span>
          <span class="stat">
            <span class="stat-value" [class.down]="counts().lost > 0">{{ counts().lost }}</span>
            <span class="stat-caption">gone cold</span>
          </span>
        </div>

        <div class="ahead">
          @for (d of dueAhead(); track $index) {
            <span class="ahead-day">
              <span class="ahead-bar" [style.height.%]="aheadHeight(d.count)" [class.none]="d.count === 0"></span>
              <span class="ahead-count">{{ d.count }}</span>
              <span class="ahead-label">{{ $index === 0 ? 'due' : shortDay(d.date) }}</span>
            </span>
          }
        </div>
        <p class="foot">Outstanding now, then what falls due each day this week.</p>

        @if (worst().length > 0) {
          <h3 class="sub-label">Slipping fastest</h3>
          @for (row of worst(); track row.chapter.id) {
            <div class="attn">
              <span class="attn-name">
                {{ row.chapter.name }}
                <span class="attn-sub">
                  {{ subjectName(row.chapter) }} · {{ stateLabel(row.state) }}{{ row.overdue > 0 ? ', ' + row.overdue + 'd late' : '' }}
                </span>
              </span>
              <span class="hold">
                <span class="hold-track">
                  <span class="hold-fill" [style.width.%]="Math.max(4, row.strength * 100)"></span>
                </span>
                <span class="hold-value">{{ Math.round(row.strength * 100) }}%</span>
              </span>
            </div>
          }
        }
      </div>
    </section>

    <!-- Consistency -->
    <section class="group">
      <h2 class="label">Consistency</h2>

      <div class="card">
        <div class="stat-row">
          <span class="stat">
            <span class="stat-value">{{ streak().current }}</span>
            <span class="stat-caption">day streak</span>
          </span>
          <span class="stat">
            <span class="stat-value">{{ streak().longest }}</span>
            <span class="stat-caption">longest</span>
          </span>
          <span class="stat">
            <span class="stat-value">{{ compact(thisWeek()) }}</span>
            <span class="stat-caption">last 7 days</span>
          </span>
          <span class="stat">
            <span class="stat-value" [class.up]="weekDelta() >= 10" [class.down]="weekDelta() <= -10">
              {{ weekDelta() > 0 ? '+' : '' }}{{ weekDelta() }}%
            </span>
            <span class="stat-caption">vs 7 before</span>
          </span>
        </div>

        <div class="months" [style.grid-template-columns]="'16px repeat(' + heatmapWeeks + ', 1fr)'">
          @for (m of monthLabels(); track m.index) {
            <span class="month-tick" [style.grid-column]="m.index + 2">{{ m.label }}</span>
          }
        </div>

        <div class="heat">
          <div class="heat-days">
            <span></span><span>M</span><span></span><span>W</span><span></span><span>F</span><span></span>
          </div>
          <div class="heat-grid">
            @for (cell of heatmap(); track cell.key) {
              <span class="heat-cell"
                    [class]="'l' + level(cell.minutes)"
                    [class.future]="cell.future"
                    [class.today]="isToday(cell.date)"
                    [attr.title]="cellTitle(cell)"></span>
            }
          </div>
        </div>

        <div class="legend">
          <span class="legend-text">Less</span>
          @for (l of [0, 1, 2, 3, 4]; track l) { <span class="heat-cell" [class]="'l' + l"></span> }
          <span class="legend-text">More</span>
        </div>
      </div>
    </section>

    <!-- Hours -->
    <section class="group">
      <h2 class="label">Hours, last {{ hoursDays }} days</h2>

      <div class="card">
        <div class="chart-head">
          <span class="chart-value">{{ hours(averageMinutes()) }}<span class="chart-unit"> / day</span></span>
          <span class="chart-caption">target {{ store.weekdayHours() }}h weekday · {{ store.weekendHours() }}h weekend</span>
        </div>

        <div class="bars" [style.--target]="targetRatio()">
          <span class="target-line"></span>
          @for (d of daily(); track d.key) {
            <span class="bar-slot" [attr.title]="barTitle(d)">
              <span class="bar" [class.empty]="d.minutes === 0" [style.height.%]="barHeight(d.minutes)"></span>
            </span>
          }
        </div>

        <div class="bar-axis">
          <span>{{ daily()[0].date | date: 'd MMM' }}</span>
          <span>today</span>
        </div>
      </div>
    </section>

    <!-- Depth -->
    <section class="group">
      <h2 class="label">Revision depth</h2>

      <div class="card">
        @for (d of depth(); track d.id) {
          <div class="depth">
            <span class="depth-head">
              <span class="depth-name">{{ d.name }}</span>
              <span class="depth-count">{{ d.learnt }}/{{ d.total }} learnt</span>
            </span>
            <span class="depth-track">
              <span class="seg s3" [style.width.%]="pct(d.r3, d.total)"></span>
              <span class="seg s2" [style.width.%]="pct(d.r2 - d.r3, d.total)"></span>
              <span class="seg s1" [style.width.%]="pct(d.r1 - d.r2, d.total)"></span>
              <span class="seg s0" [style.width.%]="pct(d.learnt - d.r1, d.total)"></span>
            </span>
          </div>
        }

        <div class="key">
          @for (k of depthKey; track k.cls) {
            <span class="key-item"><span class="swatch" [class]="k.cls"></span>{{ k.label }}</span>
          }
        </div>
      </div>
    </section>

    <!-- Accuracy -->
    <section class="group">
      <h2 class="label">Accuracy</h2>

      <div class="card">
        @for (s of subjects; track s.id) {
          <div class="acc">
            <span class="acc-name">{{ s.name }}</span>
            <span class="acc-track">
              <span class="acc-fill" [style.width.%]="subjectRate(s) ?? 0"></span>
            </span>
            <span class="acc-value">{{ subjectRate(s) === null ? '—' : subjectRate(s) + '%' }}</span>
          </div>
        }
        <p class="foot">{{ attemptedTotal() }} questions logged in practice.</p>
      </div>
    </section>

    <!-- Rebalance: a gap is a decision to make, not a scolding. -->
    @if (rebalanceOpen()) {
      <div class="scrim" (click)="rebalanceOpen.set(false)"></div>
      <div class="sheet" role="dialog" aria-label="Rebalance the plan">
        <span class="handle"></span>
        <h3 class="sheet-title">{{ behind() ? "The plan doesn't fit" : 'Adjust the plan' }}</h3>
        <p class="sheet-sub">
          {{ store.requiredHours() }}h of syllabus, {{ store.days() }} days, and you are
          averaging {{ perDay().toFixed(1) }}h a day.
          {{ behind() ? 'Something has to give — you pick which.' : 'It fits, for now.' }}
        </p>

        @if (behind()) {
        <button matRipple class="option" (click)="applyHours()">
          <mat-icon>schedule</mat-icon>
          <span class="option-text">
            <span class="option-head">Study more</span>
            <span class="option-sub">
              {{ neededPerDay().toFixed(1) }}h a day covers it — up from {{ store.weekdayHours() }}h
              on weekdays. Only real if the hours exist.
            </span>
          </span>
        </button>

        @if (store.dateMode() === 'syllabus') {
          <button matRipple class="option" (click)="applyDate()">
            <mat-icon>event</mat-icon>
            <span class="option-text">
              <span class="option-head">Move the target</span>
              <span class="option-sub">
                At this pace the syllabus lands {{ finishLabel() }}. Set that as the date and
                the plan is honest again.
              </span>
            </span>
          </button>
        } @else {
          <div class="option muted">
            <mat-icon>event_busy</mat-icon>
            <span class="option-text">
              <span class="option-head">The date can't move</span>
              <span class="option-sub">
                The exam is {{ store.targetDate() | date: 'd MMM y' }}. At this pace the syllabus
                would land {{ finishLabel() }}.
              </span>
            </span>
          </div>
        }

        <button matRipple class="option" (click)="applyPark()">
          <mat-icon>content_cut</mat-icon>
          <span class="option-text">
            <span class="option-head">Cut scope</span>
            <span class="option-sub">
              Park the {{ parkCount() }} chapters that fit least — {{ parkedMarks() }} marks of
              the paper — and finish the rest properly.
            </span>
          </span>
        </button>
        }

        @if (store.parkedChapters().size > 0) {
          <button matRipple class="text-line" (click)="unpark()">
            Bring back {{ store.parkedChapters().size }} parked chapters
          </button>
        }

        <p class="sheet-foot">Nothing here is permanent. Change it again whenever.</p>
      </div>
    }

    <!-- What to do about it -->
    <section class="group">
      <h2 class="label">Needs attention</h2>

      <div class="card">
        @if (weak().length > 0) {
          <h3 class="sub-label">Lowest accuracy</h3>
          @for (row of weak(); track row.chapter.id) {
            <div class="attn">
              <span class="attn-name">
                {{ row.chapter.name }}
                <span class="attn-sub">{{ subjectName(row.chapter) }} · {{ row.stat.attempted }} questions</span>
              </span>
              <span class="attn-value warn">{{ rate(row.rate) }}%</span>
            </div>
          }
        }

        @if (stale().length > 0) {
          <h3 class="sub-label">Learnt but never revised</h3>
          @for (row of stale(); track row.chapter.id) {
            <div class="attn">
              <span class="attn-name">
                {{ row.chapter.name }}
                <span class="attn-sub">{{ subjectName(row.chapter) }} · {{ row.overdue }} days past due</span>
              </span>
              <mat-icon class="attn-icon">schedule</mat-icon>
            </div>
          }
        }

        @if (weak().length === 0 && stale().length === 0) {
          <div class="empty">
            <mat-icon>done_all</mat-icon>
            <span>Nothing flagged. Not enough history yet, or nothing is slipping.</span>
          </div>
        }
      </div>
    </section>
  `,
  styles: `
    :host {
      display: flex;
      flex-direction: column;
      gap: 24px;
      height: 100%;
      overflow-y: auto;
      overflow-x: hidden;
      padding: 16px 16px 24px;
    }

    .group { display: flex; flex-direction: column; gap: 12px; }
    .label { margin: 0; font: var(--mat-sys-title-small); color: var(--mat-sys-on-surface-variant); }

    .card {
      display: flex;
      flex-direction: column;
      gap: 16px;
      padding: 16px;
      border-radius: var(--mat-sys-corner-large);
      background: var(--mat-sys-surface-container-low);
    }

    /* Hero ------------------------------------------------------------- */
    .hero { display: flex; align-items: center; gap: 16px; }

    .ring-wrap { position: relative; width: 116px; height: 116px; flex: none; }
    .ring { width: 100%; height: 100%; transform: rotate(-90deg); }

    .ring-track {
      fill: none;
      stroke: var(--mat-sys-surface-container-highest);
      stroke-width: 10;
    }

    .ring-fill {
      fill: none;
      stroke: var(--mat-sys-primary);
      stroke-width: 10;
      stroke-linecap: round;
      transition: stroke-dashoffset 400ms ease;
    }

    .ring-text {
      position: absolute;
      inset: 0;
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
    }

    .ring-value { font: var(--mat-sys-headline-medium); color: var(--mat-sys-on-surface); }
    .ring-unit { font: var(--mat-sys-label-small); color: var(--mat-sys-on-surface-variant); }

    /* Three facts as a table, not three sentences. The eye lands on the
       numbers; the labels are there only when it needs them. */
    .hero-side {
      flex: 1;
      min-width: 0;
      margin: 0;
      display: flex;
      flex-direction: column;
      gap: 10px;
    }

    .hero-stat { display: flex; align-items: baseline; justify-content: space-between; gap: 12px; }
    .hero-stat dt { font: var(--mat-sys-body-medium); color: var(--mat-sys-on-surface-variant); }

    .hero-stat dd {
      margin: 0;
      font: var(--mat-sys-title-large);
      color: var(--mat-sys-on-surface);
    }

    .of { font: var(--mat-sys-body-medium); color: var(--mat-sys-on-surface-variant); }

    /* flex: none — a bare child of the scrolling column gets squashed
       otherwise, the same way the chip did at 2px tall. */
    .parked-chip {
      flex: none;
      display: flex;
      align-items: center;
      gap: 8px;
      align-self: flex-start;
      height: 32px;
      padding: 0 14px 0 10px;
      border: 1px solid var(--mat-sys-outline-variant);
      border-radius: var(--mat-sys-corner-full);
      background: transparent;
      color: var(--mat-sys-on-surface-variant);
      font: var(--mat-sys-label-large);
      cursor: pointer;
    }

    .parked-chip mat-icon { font-size: 18px; width: 18px; height: 18px; }

    /* Verdict ----------------------------------------------------------- */
    .verdict {
      display: flex;
      align-items: flex-start;
      gap: 12px;
      padding: 16px;
      border-radius: var(--mat-sys-corner-large);
      background: var(--mat-sys-secondary-container);
      color: var(--mat-sys-on-secondary-container);
    }

    .verdict.behind {
      background: var(--mat-sys-error-container);
      color: var(--mat-sys-on-error-container);
    }

    /* No history is not a failure — it gets the quiet treatment. */
    .verdict.unknown {
      background: transparent;
      box-shadow: inset 0 0 0 1px var(--mat-sys-outline-variant);
      color: var(--mat-sys-on-surface-variant);
    }

    .verdict { flex-direction: column; gap: 12px; }
    .verdict-row { display: flex; align-items: flex-start; gap: 12px; }
    .verdict-text { display: flex; flex-direction: column; gap: 2px; }

    /* On the red card the button inverts; on the calm one it stays outlined. */
    .verdict:not(.behind) .verdict-cta {
      background: transparent;
      box-shadow: inset 0 0 0 1px currentColor;
      color: inherit;
    }

    .verdict-cta {
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 8px;
      height: 40px;
      border: none;
      border-radius: var(--mat-sys-corner-full);
      background: var(--mat-sys-on-error-container);
      color: var(--mat-sys-error-container);
      font: var(--mat-sys-label-large);
      cursor: pointer;
    }

    .verdict-cta mat-icon { font-size: 18px; width: 18px; height: 18px; }

    /* Sheet */
    .scrim { position: absolute; inset: 0; z-index: 3; background: rgb(0 0 0 / .32); }

    .sheet {
      position: absolute;
      left: 0;
      right: 0;
      bottom: 0;
      z-index: 4;
      display: flex;
      flex-direction: column;
      padding: 8px 16px 24px;
      border-radius: 28px 28px 0 0;
      background: var(--mat-sys-surface-container-low);
      color: var(--mat-sys-on-surface);
    }

    .handle {
      width: 32px;
      height: 4px;
      margin: 0 auto 8px;
      border-radius: 2px;
      background: var(--mat-sys-outline-variant);
    }

    .sheet-title { margin: 4px 0 0; font: var(--mat-sys-title-large); }
    .sheet-sub { margin: 4px 0 8px; font: var(--mat-sys-body-medium); color: var(--mat-sys-on-surface-variant); }

    .option {
      display: flex;
      align-items: flex-start;
      gap: 16px;
      padding: 12px 8px;
      border: none;
      background: transparent;
      color: var(--mat-sys-on-surface);
      text-align: left;
      cursor: pointer;
    }

    .option mat-icon { color: var(--mat-sys-primary); }
    .option.muted { cursor: default; }
    .option.muted mat-icon { color: var(--mat-sys-on-surface-variant); }
    .option-text { display: flex; flex-direction: column; gap: 2px; }
    .option-head { font: var(--mat-sys-title-small); }
    .option-sub { font: var(--mat-sys-body-small); color: var(--mat-sys-on-surface-variant); }

    .text-line {
      margin-top: 4px;
      padding: 8px;
      border: none;
      background: transparent;
      color: var(--mat-sys-primary);
      font: var(--mat-sys-label-large);
      text-align: left;
      cursor: pointer;
    }

    .sheet-foot { margin: 8px 0 0; font: var(--mat-sys-label-small); color: var(--mat-sys-on-surface-variant); }
    .verdict-head { font: var(--mat-sys-title-small); }
    .verdict-sub { font: var(--mat-sys-body-small); opacity: .85; }

    /* Due look-ahead ---------------------------------------------------- */
    .ahead { display: flex; align-items: flex-end; gap: 6px; height: 84px; }

    .ahead-day {
      flex: 1;
      height: 100%;
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: flex-end;
      gap: 4px;
    }

    .ahead-bar {
      width: 100%;
      min-height: 3px;
      border-radius: 3px 3px 1px 1px;
      background: var(--mat-sys-primary);
    }

    .ahead-bar.none { background: var(--mat-sys-surface-container-highest); }
    .ahead-count { font: var(--mat-sys-label-small); color: var(--mat-sys-on-surface); }
    .ahead-label { font: var(--mat-sys-label-small); color: var(--mat-sys-on-surface-variant); }

    /* How much of a chapter is probably still there. */
    .hold { width: 56px; flex: none; display: flex; flex-direction: column; gap: 3px; align-items: flex-end; }
    .hold-value { font: var(--mat-sys-label-small); color: var(--mat-sys-on-surface-variant); }

    .hold-track {
      display: block;
      width: 100%;
      height: 6px;
      border-radius: 3px;
      background: var(--mat-sys-surface-container-highest);
      overflow: hidden;
    }

    .hold-fill { display: block; height: 100%; background: var(--mat-sys-error); }

    /* Stats row --------------------------------------------------------- */
    .stat-row { display: flex; gap: 8px; }
    .stat { flex: 1; display: flex; flex-direction: column; gap: 2px; min-width: 0; }
    .stat-value {
      font: var(--mat-sys-title-large);
      color: var(--mat-sys-on-surface);
      white-space: nowrap;
    }
    .stat-value.up { color: var(--mat-sys-primary); }
    .stat-value.down { color: var(--mat-sys-error); }

    .stat-caption {
      font: var(--mat-sys-label-small);
      color: var(--mat-sys-on-surface-variant);
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }

    /* Heatmap ----------------------------------------------------------- */
    .months {
      display: grid;
      gap: 3px;
      margin-bottom: -8px;
    }

    .month-tick {
      grid-row: 1;
      font: var(--mat-sys-label-small);
      color: var(--mat-sys-on-surface-variant);
    }

    .heat { display: flex; gap: 3px; }

    .heat-days {
      display: grid;
      grid-template-rows: repeat(7, 1fr);
      gap: 3px;
      width: 13px;
      flex: none;
      font: var(--mat-sys-label-small);
      color: var(--mat-sys-on-surface-variant);
    }

    .heat-days span { display: grid; place-items: center; font-size: 9px; line-height: 1; }

    .heat-grid {
      flex: 1;
      display: grid;
      grid-template-rows: repeat(7, 1fr);
      grid-auto-flow: column;
      grid-auto-columns: 1fr;
      gap: 3px;
    }

    /* Intensity is one hue stepped against the card, so it reads as a ramp
       rather than five unrelated colours. */
    .heat-cell {
      aspect-ratio: 1;
      border-radius: 2px;
      background: var(--mat-sys-surface-container-highest);
    }

    .heat-cell.l1 { background: color-mix(in srgb, var(--mat-sys-primary) 28%, var(--mat-sys-surface-container-highest)); }
    .heat-cell.l2 { background: color-mix(in srgb, var(--mat-sys-primary) 50%, var(--mat-sys-surface-container-highest)); }
    .heat-cell.l3 { background: color-mix(in srgb, var(--mat-sys-primary) 74%, var(--mat-sys-surface-container-highest)); }
    .heat-cell.l4 { background: var(--mat-sys-primary); }
    .heat-cell.future { opacity: .35; }
    .heat-cell.today { box-shadow: 0 0 0 1.5px var(--mat-sys-on-surface); }

    .legend { display: flex; align-items: center; gap: 3px; }
    .legend .heat-cell { width: 11px; aspect-ratio: 1; }
    .legend-text { font: var(--mat-sys-label-small); color: var(--mat-sys-on-surface-variant); }
    .legend-text:first-child { margin-right: 4px; }
    .legend-text:last-child { margin-left: 4px; }

    /* Hours bars -------------------------------------------------------- */
    .chart-head { display: flex; flex-direction: column; gap: 2px; }
    .chart-value { font: var(--mat-sys-headline-small); color: var(--mat-sys-on-surface); }
    .chart-unit { font: var(--mat-sys-body-medium); color: var(--mat-sys-on-surface-variant); }
    .chart-caption { font: var(--mat-sys-label-medium); color: var(--mat-sys-on-surface-variant); }
    .chart-caption.up { color: var(--mat-sys-primary); }

    .bars {
      position: relative;
      display: flex;
      align-items: flex-end;
      gap: 4px;
      height: 104px;
    }

    /* The plan's own target, so a bar can be read as short or tall. */
    .target-line {
      position: absolute;
      left: 0;
      right: 0;
      bottom: calc(var(--target) * 100%);
      height: 0;
      border-top: 1px dashed var(--mat-sys-outline);
      pointer-events: none;
    }

    .bar-slot { flex: 1; height: 100%; display: flex; align-items: flex-end; }

    .bar {
      width: 100%;
      min-height: 3px;
      border-radius: 3px 3px 1px 1px;
      background: var(--mat-sys-primary);
    }

    .bar.empty { background: var(--mat-sys-surface-container-highest); }

    .bar-axis {
      display: flex;
      justify-content: space-between;
      font: var(--mat-sys-label-small);
      color: var(--mat-sys-on-surface-variant);
    }

    /* Depth ------------------------------------------------------------- */
    .depth { display: flex; flex-direction: column; gap: 6px; }
    .depth-head { display: flex; justify-content: space-between; align-items: baseline; gap: 8px; }
    .depth-name { font: var(--mat-sys-body-large); }
    .depth-count { font: var(--mat-sys-label-small); color: var(--mat-sys-on-surface-variant); }

    .depth-track {
      display: flex;
      height: 10px;
      border-radius: 5px;
      overflow: hidden;
      background: var(--mat-sys-surface-container-highest);
    }

    .seg { height: 100%; }
    .s3 { background: var(--mat-sys-primary); }
    .s2 { background: color-mix(in srgb, var(--mat-sys-primary) 72%, var(--mat-sys-surface-container-highest)); }
    .s1 { background: color-mix(in srgb, var(--mat-sys-primary) 46%, var(--mat-sys-surface-container-highest)); }
    .s0 { background: color-mix(in srgb, var(--mat-sys-primary) 22%, var(--mat-sys-surface-container-highest)); }

    .key { display: flex; flex-wrap: wrap; gap: 12px; }
    .key-item { display: flex; align-items: center; gap: 6px; font: var(--mat-sys-label-small); color: var(--mat-sys-on-surface-variant); }
    .swatch { width: 10px; height: 10px; border-radius: 2px; }

    /* Accuracy ---------------------------------------------------------- */
    .acc { display: flex; align-items: center; gap: 12px; }
    .acc-name { width: 76px; flex: none; font: var(--mat-sys-body-medium); }

    .acc-track {
      flex: 1;
      height: 8px;
      border-radius: 4px;
      background: var(--mat-sys-surface-container-highest);
      overflow: hidden;
    }

    .acc-fill { display: block; height: 100%; background: var(--mat-sys-primary); }
    .acc-value { width: 44px; text-align: right; font: var(--mat-sys-label-large); color: var(--mat-sys-on-surface-variant); }
    .foot { margin: 0; font: var(--mat-sys-label-small); color: var(--mat-sys-on-surface-variant); }

    /* Attention --------------------------------------------------------- */
    .sub-label { margin: 0; font: var(--mat-sys-label-large); color: var(--mat-sys-on-surface-variant); }
    .attn { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
    .attn-name { display: flex; flex-direction: column; gap: 2px; font: var(--mat-sys-body-medium); min-width: 0; }
    .attn-sub { font: var(--mat-sys-label-small); color: var(--mat-sys-on-surface-variant); }
    .attn-value { font: var(--mat-sys-title-medium); }
    .attn-value.warn { color: var(--mat-sys-error); }
    .attn-icon { color: var(--mat-sys-on-surface-variant); }

    .empty {
      display: flex;
      align-items: center;
      gap: 8px;
      color: var(--mat-sys-on-surface-variant);
      font: var(--mat-sys-body-medium);
    }
  `,
})
export class ProgressTab {
  protected readonly store = inject(OnboardingStore);
  protected readonly study = inject(StudyStore);
  protected readonly subjects = PACK.subjects;
  protected readonly hoursDays = HOURS_DAYS;
  protected readonly heatmapWeeks = HEATMAP_WEEKS;
  protected readonly circumference = 2 * Math.PI * 52;
  protected readonly Math = Math;

  /** Listed in the order the bar stacks them: deepest pass on the left. */
  protected readonly depthKey = [
    { cls: 's3', label: 'R3' },
    { cls: 's2', label: 'R2' },
    { cls: 's1', label: 'R1' },
    { cls: 's0', label: 'Learnt only' },
  ];

  protected readonly rounds = computed(() => this.study.rounds());

  /** Chapters actually in the plan — parked ones are out of the denominator. */
  protected readonly inPlay = computed(
    () => this.rounds().total - this.store.parkedChapters().size,
  );

  /* ---- Retention ------------------------------------------------------ */

  protected readonly counts = computed(() => {
    const rows = this.study.retention();
    const of = (state: RetentionState) => rows.filter((r) => r.state === state).length;
    return { fresh: of('fresh'), due: of('due'), slipping: of('slipping'), lost: of('lost') };
  });

  protected held(): string {
    const value = this.study.heldStrength();
    return value === null ? '—' : Math.round(value * 100) + '%';
  }

  /** Everything already due, whatever its state — what the planner will pick. */
  protected readonly outstanding = computed(() => this.study.dueNow().length);

  protected readonly dueAhead = computed(() => this.study.dueOver(7));

  private readonly aheadMax = computed(() =>
    Math.max(1, ...this.dueAhead().map((d) => d.count)),
  );

  protected aheadHeight(count: number): number {
    return count === 0 ? 3 : Math.round((count / this.aheadMax()) * 100);
  }

  protected shortDay(date: Date): string {
    return date.toLocaleDateString(undefined, { weekday: 'narrow' });
  }

  protected readonly worst = computed(() =>
    this.study.slipping().slice(0, 3),
  );

  protected stateLabel(state: RetentionState): string {
    return { fresh: 'holding', due: 'due today', slipping: 'slipping', lost: 'gone cold', new: 'not started' }[state];
  }

  /* ---- Readiness ------------------------------------------------------ */

  /**
   * Coverage 40, retention 30, accuracy 30. Coverage counts only what is still
   * in the plan, and is discounted by how much of it is actually still held —
   * a syllabus learnt once and abandoned should not read as progress.
   */
  protected readonly readiness = computed(() => {
    const r = this.rounds();
    const hold = this.study.heldStrength() ?? 0;
    const coverage = (r.learned / Math.max(1, this.inPlay())) * (0.4 + 0.6 * hold);
    const accuracy = (this.study.accuracy() ?? 0) / 100;
    return Math.round((coverage * 0.4 + hold * 0.3 + accuracy * 0.3) * 100);
  });

  protected accuracyLabel(): string {
    const a = this.study.accuracy();
    return a === null ? 'no' : a + '%';
  }

  /* ---- Pace ----------------------------------------------------------- */

  /** Hours still owed. Parked chapters are not owed. */
  private readonly remainingHours = computed(() =>
    ALL_CHAPTERS.filter(
      (c) => !chapterIsDone(c, this.store.doneUnits()) && !this.store.isParked(c.id),
    ).reduce((n, c) => n + c.hours, 0),
  );

  /** Days the remaining syllabus needs at the pace actually being kept. */
  protected readonly daysNeeded = computed(() => {
    const perDay = this.study.averageMinutes(HOURS_DAYS) / 60;
    if (perDay < 0.25) return null;
    return Math.ceil(this.remainingHours() / perDay);
  });

  protected readonly behind = computed(() => {
    const needed = this.daysNeeded();
    return needed === null ? true : needed > this.store.days();
  });

  /* ---- Rebalance ------------------------------------------------------ */

  protected readonly rebalanceOpen = signal(false);

  protected perDay(): number {
    return this.study.averageMinutes(HOURS_DAYS) / 60;
  }

  /** Hours a day the remaining syllabus needs to land on the target date. */
  protected neededPerDay(): number {
    return this.remainingHours() / Math.max(1, this.store.days());
  }

  protected finishLabel(): string {
    const needed = this.daysNeeded();
    if (needed === null) return '—';
    return addDays(startOfToday(), needed).toLocaleDateString(undefined, {
      day: 'numeric',
      month: 'short',
      year: 'numeric',
    });
  }

  /**
   * The tail that has to go if neither the hours nor the date move: whole
   * chapters, lowest paper weight first, until the rest fits the runway.
   */
  private readonly parkList = computed(() => {
    const capacity = this.perDay() * this.store.days();
    const done = this.store.doneUnits();
    const remaining = ALL_CHAPTERS.filter(
      (c) => !chapterIsDone(c, done) && !this.store.isParked(c.id),
    );

    // Cheapest to drop first: the subject with fewest marks per hour, and
    // within it the chapters furthest down the book.
    const ordered = [...remaining].sort(
      (a, b) => marksPerHour(a) - marksPerHour(b) || b.id.localeCompare(a.id),
    );

    const out: Chapter[] = [];
    let total = remaining.reduce((n, c) => n + c.hours, 0);
    for (const chapter of ordered) {
      if (total <= capacity) break;
      out.push(chapter);
      total -= chapter.hours;
    }
    return out;
  });

  protected parkCount(): number { return this.parkList().length; }

  protected parkedMarks(): number {
    return this.parkList().reduce((n, c) => n + marksOf(c), 0);
  }

  protected applyHours(): void {
    const factor = this.neededPerDay() / Math.max(0.5, this.perDay());
    this.store.scaleHours(factor);
    this.rebalanceOpen.set(false);
  }

  protected applyDate(): void {
    const needed = this.daysNeeded();
    if (needed !== null) this.store.targetDate.set(addDays(startOfToday(), needed));
    this.rebalanceOpen.set(false);
  }

  protected applyPark(): void {
    this.store.park(this.parkList().map((c) => c.id));
    this.rebalanceOpen.set(false);
  }

  protected unpark(): void {
    this.store.unparkAll();
    this.rebalanceOpen.set(false);
  }

  protected verdictIcon(): string {
    if (this.daysNeeded() === null) return 'hourglass_empty';
    return this.behind() ? 'trending_down' : 'trending_up';
  }

  protected verdictHead(): string {
    const needed = this.daysNeeded();
    if (needed === null) return 'Not enough history to judge pace';
    const slack = this.store.days() - needed;
    if (slack >= 0) return `On track — ${slack} days of slack`;
    // Stated as a shortfall to close, not a debt already run up.
    return `The plan needs ${(this.neededPerDay()).toFixed(1)}h a day`;
  }

  protected verdictSub(): string {
    const needed = this.daysNeeded();
    if (needed === null) return 'Log a few days of study and this fills in.';
    const slack = this.store.days() - needed;
    if (slack >= 0) {
      return `${this.remainingHours()}h left · ${this.perDay().toFixed(1)}h a day average`;
    }
    return `You are averaging ${this.perDay().toFixed(1)}h. ${this.remainingHours()}h of syllabus, ${this.store.days()} days.`;
  }

  /* ---- Consistency ---------------------------------------------------- */

  protected readonly streak = computed(() => this.study.streak());
  protected readonly heatmap = computed(() => this.study.heatmap(HEATMAP_WEEKS));

  private readonly weeks = computed(() => this.study.rollingWeeks(2));
  protected readonly thisWeek = computed(() => this.weeks()[1].minutes);

  protected readonly weekDelta = computed(() => {
    const [last, current] = this.weeks();
    if (last.minutes === 0) return current.minutes === 0 ? 0 : 100;
    return Math.round(((current.minutes - last.minutes) / last.minutes) * 100);
  });

  /** Where each month starts along the grid, for the ticks above it. */
  protected readonly monthLabels = computed(() => {
    const cells = this.heatmap();
    const out: { index: number; label: string }[] = [];
    for (let week = 0; week < HEATMAP_WEEKS; week++) {
      const date = cells[week * 7].date;
      if (date.getDate() <= 7) {
        out.push({ index: week, label: date.toLocaleDateString(undefined, { month: 'short' }) });
      }
    }
    return out;
  });

  /** Five steps, keyed to the day the plan actually asks for. */
  protected level(minutes: number): number {
    if (minutes === 0) return 0;
    const target = this.store.weekdayHours() * 60;
    const ratio = minutes / target;
    if (ratio < 0.34) return 1;
    if (ratio < 0.67) return 2;
    if (ratio < 1) return 3;
    return 4;
  }

  protected isToday(date: Date): boolean {
    return date.getTime() === startOfToday().getTime();
  }

  protected cellTitle(cell: { date: Date; minutes: number }): string {
    const day = cell.date.toLocaleDateString(undefined, { day: 'numeric', month: 'short' });
    return cell.minutes === 0 ? `${day} — nothing logged` : `${day} — ${this.hours(cell.minutes)}`;
  }

  /* ---- Hours ---------------------------------------------------------- */

  protected readonly daily = computed(() => this.study.daily(HOURS_DAYS));
  protected readonly averageMinutes = computed(() => Math.round(this.study.averageMinutes(HOURS_DAYS)));

  private readonly barMax = computed(() =>
    Math.max(this.store.weekendHours() * 60, ...this.daily().map((d) => d.minutes), 60),
  );

  protected barHeight(minutes: number): number {
    return Math.round((minutes / this.barMax()) * 100);
  }

  protected targetRatio(): number {
    return (this.store.weekdayHours() * 60) / this.barMax();
  }

  protected barTitle(d: { date: Date; minutes: number }): string {
    return `${d.date.toLocaleDateString(undefined, { weekday: 'short', day: 'numeric' })} — ${this.hours(d.minutes)}`;
  }

  /* ---- Depth ---------------------------------------------------------- */

  protected readonly depth = computed<Depth[]>(() => {
    const done = this.store.doneUnits();
    return PACK.subjects.map((subject) => {
      const chapters = subject.sections.flatMap((s) => s.chapters);
      const learnt = chapters.filter((c) => chapterIsDone(c, done));
      const at = (n: number) => learnt.filter((c) => this.study.stat(c.id).revisions >= n).length;
      return {
        id: subject.id,
        name: subject.name,
        total: chapters.length,
        learnt: learnt.length,
        r1: at(1),
        r2: at(2),
        r3: at(3),
      };
    });
  });

  protected pct(part: number, total: number): number {
    return total === 0 ? 0 : Math.max(0, (part / total) * 100);
  }

  /* ---- Accuracy ------------------------------------------------------- */

  protected subjectRate(subject: Subject): number | null {
    let attempted = 0;
    let correct = 0;
    for (const chapter of subject.sections.flatMap((s) => s.chapters)) {
      const stat = this.study.stat(chapter.id);
      attempted += stat.attempted;
      correct += stat.correct;
    }
    return attempted === 0 ? null : Math.round((correct / attempted) * 100);
  }

  protected readonly attemptedTotal = computed(() => {
    let total = 0;
    for (const s of this.study.stats().values()) total += s.attempted;
    return total;
  });

  /* ---- Attention ------------------------------------------------------ */

  protected readonly weak = computed(() => this.study.weakChapters().slice(0, 3));
  protected readonly stale = computed(() => this.study.staleChapters().slice(0, 3));

  protected rate(value: number): number { return Math.round(value * 100); }

  protected subjectName(chapter: Chapter): string {
    const id = chapter.id.split('.')[0];
    return PACK.subjects.find((s) => s.id === id)?.name ?? '';
  }

  protected since(key: string | null): string {
    if (!key) return 'never';
    const [y, m, d] = key.split('-').map(Number);
    const days = Math.round((startOfToday().getTime() - new Date(y, m - 1, d).getTime()) / 86_400_000);
    if (days <= 0) return 'today';
    return days === 1 ? 'yesterday' : `${days} days ago`;
  }

  /* ---- Formatting ----------------------------------------------------- */

  /** Short enough for a stat tile: 15h, 45m, 2.5h. */
  protected compact(minutes: number): string {
    if (minutes <= 0) return '0h';
    if (minutes < 60) return `${Math.round(minutes)}m`;
    const h = minutes / 60;
    return h >= 10 ? `${Math.round(h)}h` : `${(Math.round(h * 10) / 10).toString().replace('.0', '')}h`;
  }

  protected hours(minutes: number): string {
    if (minutes <= 0) return '0h';
    const h = Math.floor(minutes / 60);
    const m = Math.round(minutes % 60);
    if (h === 0) return `${m}m`;
    return m === 0 ? `${h}h` : `${h}h ${m}m`;
  }
}

/** Marks the paper gives a chapter, spread evenly inside its subject. */
function marksOf(chapter: Chapter): number {
  const subject = PACK.subjects.find((s) => chapter.id.startsWith(s.id + '.'));
  if (!subject) return 0;
  const count = subject.sections.reduce((n, sec) => n + sec.chapters.length, 0);
  return Math.round(subject.marks / count);
}

/** What an hour spent on a chapter is worth. The triage order. */
function marksPerHour(chapter: Chapter): number {
  return marksOf(chapter) / Math.max(0.5, chapter.hours);
}
