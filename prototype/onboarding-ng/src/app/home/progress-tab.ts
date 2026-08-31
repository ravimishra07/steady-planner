import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';
import { MatRippleModule } from '@angular/material/core';
import { OnboardingStore, addDays, startOfToday } from '../onboarding/state';
import { Chapter, PACK, chapterIsDone, subjectNameOf } from '../onboarding/exam-pack';
import { StudyStore, dateKey } from '../study/study-store';

/**
 * Weeks the consistency grid shows. Long enough to read as a habit, short
 * enough that a new account is not mostly dead squares.
 */
const HEATMAP_WEEKS = 18;
/** Days in the hours chart. */
const HOURS_DAYS = 14;


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
    <!-- Coverage: a count of ticks the user made. Not a score about them. -->
    <header class="hero">
      <div class="ring-wrap">
        <svg class="ring" viewBox="0 0 120 120" aria-hidden="true">
          <circle class="ring-track" cx="60" cy="60" r="52" />
          <circle class="ring-fill" cx="60" cy="60" r="52"
                  [attr.stroke-dasharray]="circumference"
                  [attr.stroke-dashoffset]="circumference * (1 - coverage() / 100)" />
        </svg>
        <span class="ring-text">
          <span class="ring-value">{{ coverage() }}<span class="pct">%</span></span>
          <span class="ring-unit">covered</span>
        </span>
      </div>

      <dl class="facts">
        <div class="fact">
          <dt>Chapters done</dt>
          <dd>{{ rounds().learned }}<span class="of"> of {{ inPlay() }}</span></dd>
        </div>
        <div class="fact">
          <dt>Hours logged</dt>
          <dd>{{ compact(study.totalMinutes()) }}</dd>
        </div>
        <div class="fact">
          <dt>Days studied</dt>
          <dd>{{ study.daysStudied() }}</dd>
        </div>
      </dl>
    </header>

    <section class="pace" [class.behind]="behind()" [class.unknown]="daysNeeded() === null">
      <span class="pace-head">
        <mat-icon>{{ verdictIcon() }}</mat-icon>
        {{ verdictHead() }}
      </span>
      <span class="pace-sub">{{ verdictSub() }}</span>

      @if (daysNeeded() !== null) {
        <div class="pace-actions">
          <button matRipple class="pace-cta" [class.urgent]="behind()" (click)="rebalanceOpen.set(true)">
            {{ behind() ? 'Rebalance' : 'Adjust plan' }}
          </button>
          @if (store.parkedChapters().size > 0) {
            <span class="pace-chip">{{ store.parkedChapters().size }} parked</span>
          }
        </div>
      }
    </section>

    <section class="block">
      <div class="block-head">
        <h2 class="block-title">Due to revise</h2>
        <div class="range">
          @for (r of ranges; track r) {
            <button matRipple class="range-btn" [class.on]="dueRange() === r" (click)="dueRange.set(r)">{{ r }}d</button>
          }
        </div>
      </div>

      <div class="plot">
        @for (d of dueAhead(); track $index) {
          <span class="plot-col" [attr.title]="d.label + ' — ' + d.count">
            <span class="plot-num" [class.zero]="d.count === 0">{{ d.count }}</span>
            <span class="plot-bar" [style.height.%]="aheadHeight(d.count)" [class.none]="d.count === 0"></span>
            <span class="plot-tick">{{ d.tick }}</span>
          </span>
        }
      </div>

      @if (dueList().length > 0) {
        <ul class="rows">
          @for (row of dueList(); track row.chapter.id) {
            <li>
              <button matRipple class="row" (click)="addToday(row.chapter)">
                <span class="row-text">
                  <span class="row-name">{{ row.chapter.name }}</span>
                  <span class="row-meta">{{ subjectName(row.chapter) }} · {{ lastSeen(row.stat.lastTouched) }}</span>
                </span>
                <span class="row-action" [class.done]="isQueued(row.chapter)">
                  <mat-icon>{{ isQueued(row.chapter) ? 'check' : 'add' }}</mat-icon>
                  {{ isQueued(row.chapter) ? 'Added' : 'Today' }}
                </span>
              </button>
            </li>
          }
        </ul>

        @if (dueNow().length > 3) {
          <button matRipple class="more" (click)="showAllDue.set(!showAllDue())">
            {{ showAllDue() ? 'Show less' : 'Show all ' + dueNow().length }}
          </button>
        }
      } @else {
        <p class="empty">Nothing due — everything is on its schedule.</p>
      }
    </section>

    <section class="block">
      <div class="block-head">
        <h2 class="block-title">Time</h2>
        <span class="block-aside">{{ streak().current }} day streak</span>
      </div>

      <p class="lead">{{ hours(averageMinutes()) }} <span class="lead-unit">a day, last {{ hoursDays }} days</span></p>

      <div class="plot tall" [style.--target]="targetRatio()">
        <span class="target"><span class="target-tag">{{ store.weekdayHours() }}h target</span></span>
        @for (d of daily(); track d.key) {
          <span class="plot-col" [attr.title]="barTitle(d)">
            <span class="plot-bar" [class.none]="d.minutes === 0" [style.height.%]="barHeight(d.minutes)"></span>
          </span>
        }
      </div>

      <div class="months" [style.grid-template-columns]="'14px repeat(' + heatmapWeeks + ', 1fr)'">
        @for (m of monthLabels(); track m.index) {
          <span class="month-tick" [style.grid-column]="m.index + 2">{{ m.label }}</span>
        }
      </div>

      <div class="heat">
        <div class="heat-days"><span></span><span>M</span><span></span><span>W</span><span></span><span>F</span><span></span></div>
        <div class="heat-grid">
          @for (cell of heatmap(); track cell.key) {
            <span class="cell" [class]="'l' + level(cell.minutes)"
                  [class.future]="cell.future" [class.now]="isToday(cell.date)"
                  [attr.title]="cellTitle(cell)"></span>
          }
        </div>
      </div>
    </section>

    @if (rebalanceOpen()) {
      <div class="scrim" (click)="rebalanceOpen.set(false)"></div>
      <div class="sheet" role="dialog" aria-label="Adjust the plan">
        <span class="handle"></span>
        <h3 class="sheet-title">{{ behind() ? "The plan doesn't fit" : 'Adjust the plan' }}</h3>
        <p class="sheet-sub">
          {{ store.requiredHours() }}h of syllabus, {{ store.days() }} days,
          {{ perDay().toFixed(1) }}h a day so far.
          {{ behind() ? 'Something has to give — you pick which.' : 'It fits, for now.' }}
        </p>

        @if (behind()) {
          <button matRipple class="option" (click)="applyHours()">
            <mat-icon>schedule</mat-icon>
            <span class="option-text">
              <span class="option-head">Study more</span>
              <span class="option-sub">
                {{ neededPerDay().toFixed(1) }}h a day covers it, up from {{ store.weekdayHours() }}h.
              </span>
            </span>
          </button>

          @if (store.dateMode() === 'syllabus') {
            <button matRipple class="option" (click)="applyDate()">
              <mat-icon>event</mat-icon>
              <span class="option-text">
                <span class="option-head">Move the target</span>
                <span class="option-sub">At this pace the syllabus lands {{ finishLabel() }}.</span>
              </span>
            </button>
          } @else {
            <div class="option muted">
              <mat-icon>event_busy</mat-icon>
              <span class="option-text">
                <span class="option-head">The date can't move</span>
                <span class="option-sub">
                  The exam is {{ store.targetDate() | date: 'd MMM y' }}; this pace lands {{ finishLabel() }}.
                </span>
              </span>
            </div>
          }

          <button matRipple class="option" (click)="applyPark()">
            <mat-icon>content_cut</mat-icon>
            <span class="option-text">
              <span class="option-head">Cut scope</span>
              <span class="option-sub">
                Park the {{ parkCount() }} chapters worth least per hour — {{ parkedMarks() }} marks.
              </span>
            </span>
          </button>
        }

        @if (store.parkedChapters().size > 0) {
          <button matRipple class="option" (click)="unpark()">
            <mat-icon>unarchive</mat-icon>
            <span class="option-text">
              <span class="option-head">Bring back {{ store.parkedChapters().size }} parked chapters</span>
              <span class="option-sub">Nothing here is permanent.</span>
            </span>
          </button>
        }
      </div>
    }
  `,
  styles: `
    /* M3: don't force content into cards when spacing, headlines and dividers
       give a simpler hierarchy. Only the pace block is a card — it is a single
       subject and it carries an action. */
    :host {
      display: block;
      height: 100%;
      overflow-y: auto;
      overflow-x: hidden;
      padding: 8px 16px 32px;
      background: var(--mat-sys-surface);
    }

    /* Hero ------------------------------------------------------------- */
    .hero {
      position: relative;
      display: flex;
      align-items: center;
      gap: 24px;
      padding: 16px 0 24px;
    }


    /* The ring is the affordance for "what is this number?" — no separate
       glyph competing with the figures beside it. */
    .ring-wrap {
      position: relative;
      width: 112px;
      height: 112px;
      flex: none;
      padding: 0;
      border: none;
      border-radius: 50%;
      background: transparent;
      cursor: pointer;
    }
    .ring { width: 100%; height: 100%; transform: rotate(-90deg); }
    .ring-track { fill: none; stroke: var(--mat-sys-surface-container-highest); stroke-width: 8; }

    .ring-fill {
      fill: none;
      stroke: var(--mat-sys-primary);
      stroke-width: 8;
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

    /* Display size is for exactly this: a short, important numeral. */
    .ring-value { font: var(--mat-sys-display-small); line-height: 1; color: var(--mat-sys-on-surface); }
    .pct { font: var(--mat-sys-title-medium); color: var(--mat-sys-on-surface-variant); }
    .ring-unit {
      display: flex;
      align-items: center;
      gap: 3px;
      margin-top: 4px;
      font: var(--mat-sys-label-medium);
      color: var(--mat-sys-on-surface-variant);
    }

    .ring-unit mat-icon { font-size: 13px; width: 13px; height: 13px; opacity: .7; }

    .facts { flex: 1; min-width: 0; margin: 0; display: flex; flex-direction: column; gap: 8px; }
    .fact { display: flex; align-items: baseline; justify-content: space-between; gap: 12px; }
    .fact dt { font: var(--mat-sys-body-medium); color: var(--mat-sys-on-surface-variant); }
    .fact dd { margin: 0; font: var(--mat-sys-title-medium); color: var(--mat-sys-on-surface); }
    .of { font: var(--mat-sys-body-small); color: var(--mat-sys-on-surface-variant); }

    /* Pace: the one card ------------------------------------------------ */
    /* Everything in the card shares one left edge: the icon sits inline with
       the title rather than pushing a column the actions don't respect. */
    .pace {
      display: flex;
      flex-direction: column;
      gap: 4px;
      margin-bottom: 8px;
      padding: 16px;
      border-radius: var(--mat-sys-corner-large);
      background: var(--mat-sys-surface-container);
      color: var(--mat-sys-on-surface);
    }

    .pace.behind { background: var(--mat-sys-error-container); color: var(--mat-sys-on-error-container); }
    .pace.unknown { background: transparent; box-shadow: inset 0 0 0 1px var(--mat-sys-outline-variant); }

    .pace-head { display: flex; align-items: center; gap: 8px; font: var(--mat-sys-title-small); }
    .pace-head mat-icon { font-size: 20px; width: 20px; height: 20px; }
    .pace-sub { font: var(--mat-sys-body-small); opacity: .8; }

    .pace-actions { display: flex; align-items: center; gap: 8px; margin-top: 12px; }

    /* Tonal while the plan is fine; filled only when it is the thing to do. */
    .pace-cta {
      height: 36px;
      padding: 0 16px;
      border: none;
      border-radius: var(--mat-sys-corner-full);
      background: var(--mat-sys-secondary-container);
      color: var(--mat-sys-on-secondary-container);
      font: var(--mat-sys-label-large);
      cursor: pointer;
    }

    .pace-cta.urgent {
      background: var(--mat-sys-on-error-container);
      color: var(--mat-sys-error-container);
    }

    /* An assist chip, not a stray caption sitting beside a button. */
    .pace-chip {
      display: inline-flex;
      align-items: center;
      height: 32px;
      padding: 0 12px;
      border: 1px solid var(--mat-sys-outline-variant);
      border-radius: var(--mat-sys-corner-full);
      font: var(--mat-sys-label-medium);
      opacity: .9;
    }

    .pace-note { font: var(--mat-sys-label-medium); opacity: .8; }

    /* Blocks: heading + content, separated by space and a hairline -------- */
    .block { padding: 24px 0; border-top: 1px solid var(--mat-sys-outline-variant); }
    .block:first-of-type { border-top: none; }
    /* The pace card is its own boundary — no divider stacked against it. */
    .pace + .block { border-top: none; }

    .block-title {
      margin: 0 0 16px;
      font: var(--mat-sys-title-medium);
      color: var(--mat-sys-on-surface);
    }

    .block-head { display: flex; align-items: baseline; justify-content: space-between; gap: 12px; }
    .block-head .block-title { margin-bottom: 16px; }
    .block-aside { font: var(--mat-sys-label-small); color: var(--mat-sys-on-surface-variant); }

    /* Range control: the window is the user's choice, not a constant. */
    .range { display: flex; gap: 4px; }

    .range-btn {
      height: 28px;
      padding: 0 10px;
      border: 1px solid var(--mat-sys-outline-variant);
      border-radius: var(--mat-sys-corner-full);
      background: transparent;
      color: var(--mat-sys-on-surface-variant);
      font: var(--mat-sys-label-small);
      cursor: pointer;
    }

    .range-btn.on {
      border-color: transparent;
      background: var(--mat-sys-secondary-container);
      color: var(--mat-sys-on-secondary-container);
    }

    .sub-title {
      margin: 24px 0 8px;
      font: var(--mat-sys-title-small);
      color: var(--mat-sys-on-surface-variant);
    }

    .caption { margin: 8px 0 0; font: var(--mat-sys-label-small); color: var(--mat-sys-on-surface-variant); }
    .lead { margin: 0 0 12px; font: var(--mat-sys-headline-small); color: var(--mat-sys-on-surface); }
    .lead-unit { font: var(--mat-sys-body-medium); color: var(--mat-sys-on-surface-variant); }

    /* Metrics: one row component, used identically everywhere ------------- */
    .metrics { display: flex; gap: 8px; margin-bottom: 20px; }
    .metric { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 2px; }

    .metric-value {
      font: var(--mat-sys-title-large);
      color: var(--mat-sys-on-surface);
      white-space: nowrap;
    }

    .metric-value.alert { color: var(--mat-sys-error); }
    .metric-value.good { color: var(--mat-sys-primary); }

    .metric-label {
      font: var(--mat-sys-label-small);
      color: var(--mat-sys-on-surface-variant);
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }

    /* Plots: one component, two heights ---------------------------------- */
    .plot { position: relative; display: flex; align-items: flex-end; gap: 5px; height: 76px; margin-bottom: 20px; }
    .plot.tall { height: 104px; }
    .plot-col { flex: 1; height: 100%; display: flex; flex-direction: column; justify-content: flex-end; gap: 4px; }

    .plot-bar {
      width: 100%;
      min-height: 3px;
      border-radius: 4px 4px 2px 2px;
      background: var(--mat-sys-primary);
    }

    .plot-bar.none { background: var(--mat-sys-surface-container-highest); }

    .plot-num {
      font: var(--mat-sys-label-small);
      color: var(--mat-sys-on-surface);
      text-align: center;
    }

    .plot-num.zero { color: var(--mat-sys-on-surface-variant); opacity: .5; }
    .plot-tick { font: var(--mat-sys-label-small); color: var(--mat-sys-on-surface-variant); text-align: center; }

    .target {
      position: absolute;
      left: 0;
      right: 0;
      bottom: calc(var(--target) * 100%);
      border-top: 1px dashed var(--mat-sys-outline);
      pointer-events: none;
    }

    /* The line labels itself instead of a sentence under the chart. */
    .target-tag {
      position: absolute;
      right: 0;
      top: -14px;
      font: var(--mat-sys-label-small);
      color: var(--mat-sys-on-surface-variant);
    }

    /* Rows: one list component ------------------------------------------- */
    .rows { margin: 0; padding: 0; list-style: none; }

    /* Every row here does something, so every row is a button. */
    .row {
      width: 100%;
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 16px;
      min-height: 56px;
      padding: 8px 0;
      border: none;
      background: transparent;
      color: var(--mat-sys-on-surface);
      text-align: left;
      cursor: pointer;
    }

    .rows li + li .row { border-top: 1px solid var(--mat-sys-outline-variant); }

    .row-action {
      flex: none;
      display: flex;
      align-items: center;
      gap: 4px;
      font: var(--mat-sys-label-large);
      color: var(--mat-sys-primary);
    }

    .row-action.done { color: var(--mat-sys-on-surface-variant); }
    .row-action mat-icon { font-size: 18px; width: 18px; height: 18px; }

    .more {
      margin-top: 8px;
      padding: 8px 0;
      border: none;
      background: transparent;
      color: var(--mat-sys-primary);
      font: var(--mat-sys-label-large);
      text-align: left;
      cursor: pointer;
    }

    .empty {
      margin: 0;
      padding: 8px 0;
      font: var(--mat-sys-body-medium);
      color: var(--mat-sys-on-surface-variant);
    }
    .row-text { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 4px; }
    .row-name { font: var(--mat-sys-body-large); color: var(--mat-sys-on-surface); }
    .row-meta { font: var(--mat-sys-label-small); color: var(--mat-sys-on-surface-variant); }
    .row-value { font: var(--mat-sys-title-small); color: var(--mat-sys-on-surface-variant); }
    .row-value.alert { color: var(--mat-sys-error); }
    .row-stack { flex: none; display: flex; flex-direction: column; align-items: flex-end; gap: 2px; }
    .row-unit { font: var(--mat-sys-label-small); color: var(--mat-sys-on-surface-variant); }
    .bar-row .row-text { gap: 8px; }

    .track {
      display: flex;
      height: 8px;
      border-radius: 4px;
      overflow: hidden;
      background: var(--mat-sys-surface-container-highest);
    }

    .seg { height: 100%; }
    .s3 { background: var(--mat-sys-primary); }
    .s2 { background: color-mix(in srgb, var(--mat-sys-primary) 70%, var(--mat-sys-surface-container-highest)); }
    .s1 { background: color-mix(in srgb, var(--mat-sys-primary) 45%, var(--mat-sys-surface-container-highest)); }
    .s0 { background: color-mix(in srgb, var(--mat-sys-primary) 22%, var(--mat-sys-surface-container-highest)); }

    .key { display: flex; flex-wrap: wrap; gap: 16px; margin-top: 16px; }

    .key-item {
      display: flex;
      align-items: center;
      gap: 6px;
      font: var(--mat-sys-label-small);
      color: var(--mat-sys-on-surface-variant);
    }

    .swatch { width: 10px; height: 10px; border-radius: 2px; }

    /* Heatmap ------------------------------------------------------------ */
    .months { display: grid; gap: 3px; margin-bottom: 4px; }
    .month-tick { grid-row: 1; font: var(--mat-sys-label-small); color: var(--mat-sys-on-surface-variant); }

    .heat { display: flex; gap: 3px; }

    .heat-days {
      display: grid;
      grid-template-rows: repeat(7, 1fr);
      gap: 3px;
      width: 11px;
      flex: none;
    }

    .heat-days span {
      display: grid;
      place-items: center;
      font-size: 9px;
      line-height: 1;
      color: var(--mat-sys-on-surface-variant);
    }

    .heat-grid {
      flex: 1;
      display: grid;
      grid-template-rows: repeat(7, 1fr);
      grid-auto-flow: column;
      grid-auto-columns: 1fr;
      gap: 3px;
    }

    .cell { aspect-ratio: 1; border-radius: 2px; background: var(--mat-sys-surface-container-highest); }
    .cell.l1 { background: color-mix(in srgb, var(--mat-sys-primary) 28%, var(--mat-sys-surface-container-highest)); }
    .cell.l2 { background: color-mix(in srgb, var(--mat-sys-primary) 50%, var(--mat-sys-surface-container-highest)); }
    .cell.l3 { background: color-mix(in srgb, var(--mat-sys-primary) 74%, var(--mat-sys-surface-container-highest)); }
    .cell.l4 { background: var(--mat-sys-primary); }
    .cell.future { opacity: .3; }
    .cell.now { box-shadow: inset 0 0 0 1.5px var(--mat-sys-on-surface); }

    .legend { display: flex; align-items: center; gap: 3px; margin-top: 12px; }
    .legend .cell { width: 11px; aspect-ratio: 1; }
    .legend .caption { margin: 0 4px; }

    /* Sheet -------------------------------------------------------------- */
    .scrim { position: fixed; inset: 0; z-index: 3; background: rgb(0 0 0 / .32); }

    .sheet {
      position: fixed;
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
    .notes {
      margin: 12px 0 0;
      padding-left: 18px;
      display: flex;
      flex-direction: column;
      gap: 12px;
      font: var(--mat-sys-body-medium);
      color: var(--mat-sys-on-surface-variant);
    }

    .notes b { color: var(--mat-sys-on-surface); font-weight: 600; }

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

    .option mat-icon { color: var(--mat-sys-primary); flex: none; }
    .option.muted { cursor: default; }
    .option.muted mat-icon { color: var(--mat-sys-on-surface-variant); }
    .option-text { display: flex; flex-direction: column; gap: 2px; }
    .option-head { font: var(--mat-sys-title-small); }
    .option-sub { font: var(--mat-sys-body-small); color: var(--mat-sys-on-surface-variant); }
  `,

})
export class ProgressTab {
  protected readonly store = inject(OnboardingStore);
  protected readonly study = inject(StudyStore);
  protected readonly hoursDays = HOURS_DAYS;
  protected readonly heatmapWeeks = HEATMAP_WEEKS;
  protected readonly circumference = 2 * Math.PI * 52;
  protected readonly Math = Math;

  protected readonly rounds = computed(() => this.study.rounds());

  /** Chapters actually in the plan — parked ones are out of the denominator. */
  protected readonly inPlay = computed(
    () => this.rounds().total - this.store.parkedChapters().size,
  );

  /** Plain coverage: ticks the user made, over the chapters still in play. */
  protected readonly coverage = computed(() =>
    Math.round((this.rounds().learned / Math.max(1, this.inPlay())) * 100),
  );

  /* ---- Due to revise -------------------------------------------------- */

  protected readonly ranges = [7, 30, 90];
  protected readonly dueRange = signal(7);
  protected readonly showAllDue = signal(false);
  protected readonly dueNow = computed(() => this.study.dueNow());

  /** Three at a time until asked for the rest. */
  protected readonly dueList = computed(() =>
    this.showAllDue() ? this.dueNow() : this.dueNow().slice(0, 3),
  );

  /**
   * Outstanding first, then what falls due. A week or less goes day by day;
   * longer ranges bucket into weeks so the axis stays readable.
   */
  protected readonly dueAhead = computed(() => {
    const days = this.dueRange();
    const raw = this.study.dueOver(days);
    const head = { count: raw[0].count, tick: 'due', label: 'Outstanding' };
    if (days <= 7) {
      return [
        head,
        ...raw.slice(1).map((d) => ({
          count: d.count,
          tick: this.shortDay(d.date),
          label: d.date.toLocaleDateString(),
        })),
      ];
    }
    const out = [head];
    for (let i = 1; i < raw.length; i += 7) {
      const week = raw.slice(i, i + 7);
      out.push({
        count: week.reduce((n, d) => n + d.count, 0),
        tick: '+' + Math.ceil((i + 6) / 7) + 'w',
        label: 'week of ' + week[0].date.toLocaleDateString(),
      });
    }
    return out;
  });

  /** The row does something: drop a due chapter into today. */
  protected addToday(chapter: Chapter): void {
    if (this.isQueued(chapter)) return;
    this.study.addExtra({
      dateKey: dateKey(startOfToday()),
      startMinute: 20 * 60,
      minutes: 30,
      task: 'Revise',
      chapterId: chapter.id,
    });
  }

  protected isQueued(chapter: Chapter): boolean {
    return this.study
      .extrasOn(dateKey(startOfToday()))
      .some((e) => e.chapterId === chapter.id);
  }

  /** A date the user can check, not a modelled percentage. */
  protected lastSeen(key: string | null): string {
    if (!key) return 'not opened yet';
    const [y, m, d] = key.split('-').map(Number);
    const days = Math.round((startOfToday().getTime() - new Date(y, m - 1, d).getTime()) / 86_400_000);
    if (days <= 0) return 'last opened today';
    if (days === 1) return 'last opened yesterday';
    return `last opened ${days} days ago`;
  }


  private readonly aheadMax = computed(() =>
    Math.max(1, ...this.dueAhead().map((d) => d.count)),
  );

  protected aheadHeight(count: number): number {
    return count === 0 ? 3 : Math.round((count / this.aheadMax()) * 100);
  }

  protected shortDay(date: Date): string {
    return date.toLocaleDateString(undefined, { weekday: 'narrow' });
  }

  /* ---- Pace ----------------------------------------------------------- */

  /** Hours still owed. Parked chapters are not owed. */
  private readonly remainingHours = computed(() =>
    this.store.allChapters().filter(
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
  protected readonly explainOpen = signal(false);

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
    const remaining = this.store.allChapters().filter(
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
    if (slack >= 0) return `On track — ${slack} days spare`;
    // Stated as a shortfall to close, not a debt already run up.
    return `The plan needs ${(this.neededPerDay()).toFixed(1)}h a day`;
  }

  protected verdictSub(): string {
    const needed = this.daysNeeded();
    if (needed === null) return 'Log a few days of study and this fills in.';
    const slack = this.store.days() - needed;
    if (slack >= 0) {
      return `${Math.round(this.remainingHours())}h left · ${this.perDay().toFixed(1)}h a day over ${HOURS_DAYS} days`;
    }
    return `${Math.round(this.remainingHours())}h of syllabus, ${this.store.days()} days, ${this.perDay().toFixed(1)}h a day so far.`;
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

  protected subjectName(chapter: Chapter): string {
    return subjectNameOf(chapter.id);
  }
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
