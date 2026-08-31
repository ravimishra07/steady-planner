import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import { MatRippleModule } from '@angular/material/core';
import { COACHINGS, OnboardingStore, addDays, startOfToday } from '../onboarding/state';
import { ALL_CHAPTERS, Chapter, chapterIsDone } from '../onboarding/exam-pack';
import { StudyStore, Task, dateKey } from '../study/study-store';
import { Block, StudyBlock, freeWindows, layOutDay, subjectLabel } from './scheduler';
import { dayCandidates } from './day-plan';

interface DayCell { date: Date; day: number; planned: boolean; logged: boolean; }

const PX_PER_MINUTE = 0.75;
const MIN_BLOCK_HEIGHT = 72;

/**
 * Today: calendar chrome, one honest headline number, and the day laid out
 * against the hours that are actually free. Mirrors HomeCalendarChrome.kt +
 * HomeTimeline.kt.
 */
@Component({
  selector: 'app-today',
  imports: [MatIconModule, MatRippleModule, DatePipe, FormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <header class="chrome">
      <button matRipple class="month" (click)="expanded.set(!expanded())">
        <span class="month-title">{{ selected() | date: 'MMMM y' }}</span>
        <mat-icon [class.up]="expanded()">keyboard_arrow_down</mat-icon>
      </button>

      <div class="weekdays">
        @for (d of weekdayLabels; track $index) { <span>{{ d }}</span> }
      </div>

      <div class="grid">
        @for (cell of (expanded() ? monthCells() : weekCells()); track $index) {
          @if (cell) {
            <button
              class="cell"
              [class.on]="isSelected(cell.date)"
              [class.today]="isToday(cell.date)"
              (click)="selected.set(cell.date)">
              <span class="num">{{ cell.day }}</span>
              <span class="marks">
                @if (cell.logged) { <span class="dot done"></span> }
                @else if (cell.planned) { <span class="dot"></span> }
              </span>
            </button>
          } @else {
            <span class="cell empty"></span>
          }
        }
      </div>
    </header>

    <section class="summary">
      <span class="headline">{{ headline() }}</span>
      <span class="sub">{{ subline() }}</span>

      <div class="bar" [attr.aria-label]="loggedMinutes() + ' of ' + plannedMinutes() + ' minutes done'">
        <span class="fill" [style.width.%]="donePercent()"></span>
      </div>
    </section>

    @if (backlog() > 0) {
      <div class="anchors">
        <span class="anchor warn">
          <mat-icon>error</mat-icon>
          {{ backlog() }} chapters behind pace
        </span>
      </div>
    }

    <section class="timeline">
      @for (block of blocks(); track block.startMinute + block.kind) {
        @if (block.kind === 'break') {
          <div class="break-row">
            <span class="rail"><span class="line dashed"></span></span>
            <span class="break-body">
              <button matRipple class="break-chip" (click)="breakOpen.set(true)">
                <span>{{ block.minutes }} min break</span>
                <mat-icon>edit</mat-icon>
              </button>
            </span>
          </div>
        } @else {
          <div class="row" [style.min-height.px]="height(block)">
            <span class="rail">
              <span class="line" [class.dashed]="block.kind === 'gap'"></span>
              @if (block.kind !== 'gap') {
                <span class="node"
                      [class.done]="block.kind === 'study' && block.done"
                      [class.fixed]="block.kind === 'fixed'"></span>
              }
            </span>

            <span class="row-body">
              <span class="clock">{{ clock(block.startMinute) }}</span>

            @if (block.kind === 'study') {
              <button matRipple class="block" [class.done]="block.done" (click)="openSession(block)">
                <span class="block-head">
                  <span class="tag" [class]="'tag-' + block.task.toLowerCase()">{{ block.task }}</span>
                  <span class="len">{{ format(block.minutes) }}</span>
                </span>
                <span class="title">{{ block.title }}</span>
                <span class="context">
                  {{ block.context }}@if (block.questions) { · {{ block.questions }} Q }
                </span>
                <span class="action">
                  @if (block.done) {
                    <mat-icon>check_circle</mat-icon>Logged
                  } @else {
                    <mat-icon>play_arrow</mat-icon>Start
                  }
                </span>
              </button>
            } @else if (block.kind === 'fixed') {
              <div class="block fixed">
                <span class="block-head">
                  <span class="tag tag-class">Fixed</span>
                  <span class="len">{{ format(block.minutes) }}</span>
                </span>
                <span class="title">{{ block.title }}</span>
                <span class="context">{{ block.subject }}</span>
              </div>
            } @else {
              <button matRipple class="gap" (click)="openPicker(block.startMinute, block.minutes)">
                <mat-icon>add</mat-icon>
                {{ format(block.minutes) }} free — add something
              </button>
            }
            </span>
          </div>
        }
      } @empty {
        <div class="empty">
          <mat-icon>event_busy</mat-icon>
          <span>No room left on this day. Fixed hours take all of it.</span>
        </div>
      }
    </section>

    <!-- Session sheet: the thing Start used to not open. -->
    @if (session(); as s) {
      <div class="scrim" (click)="closeSession()"></div>
      <div class="sheet" role="dialog" aria-label="Session">
        <span class="handle"></span>
        <h3 class="sheet-title">{{ s.title }}</h3>
        <p class="sheet-sub">{{ s.context }} · {{ s.task }} · {{ format(s.minutes) }}</p>

        @if (s.task === 'Practice') {
          <div class="score-row">
            <label>
              <span class="field-label">Attempted</span>
              <input type="number" min="0" [ngModel]="attempted()" (ngModelChange)="attempted.set(+$event)" />
            </label>
            <label>
              <span class="field-label">Correct</span>
              <input type="number" min="0" [ngModel]="correct()" (ngModelChange)="correct.set(+$event)" />
            </label>
          </div>
        }

        <button matRipple class="filled-button" (click)="complete(s)">
          {{ s.task === 'Learn' ? 'Mark covered' : s.task === 'Revise' ? 'Mark revised' : 'Save attempt' }}
        </button>

        <h4 class="sheet-label">Can't do it now</h4>
        <button matRipple class="sheet-row" (click)="push(s, 30)">
          <mat-icon>schedule</mat-icon><span class="sheet-name">Push 30 minutes</span>
        </button>
        <button matRipple class="sheet-row" (click)="push(s, 24 * 60)">
          <mat-icon>event_repeat</mat-icon><span class="sheet-name">Move to tomorrow</span>
        </button>
        <button matRipple class="sheet-row" (click)="skip(s)">
          <mat-icon>block</mat-icon><span class="sheet-name">Skip it</span>
        </button>
      </div>
    }

    <!-- Break length. One setting, applied to every gap in the day. -->
    @if (breakOpen()) {
      <div class="scrim" (click)="breakOpen.set(false)"></div>
      <div class="sheet" role="dialog" aria-label="Break length">
        <span class="handle"></span>
        <h3 class="sheet-title">Break between sittings</h3>
        <p class="sheet-sub">Applies to every break the plan schedules.</p>

        <div class="tasks">
          @for (m of breakChoices; track m) {
            <button matRipple class="task-chip" [class.on]="store.breakMinutes() === m"
                    (click)="setBreak(m)">{{ m }}m</button>
          }
        </div>
      </div>
    }

    <!-- Free-slot topic picker. -->
    @if (picker(); as slot) {
      <div class="scrim" (click)="picker.set(null)"></div>
      <div class="sheet tall" role="dialog" aria-label="Add to this slot">
        <span class="handle"></span>
        <h3 class="sheet-title">{{ format(slot.minutes) }} free at {{ clock(slot.startMinute) }}</h3>

        <div class="tasks">
          @for (t of tasks; track t) {
            <button matRipple class="task-chip" [class.on]="pickTask() === t" (click)="pickTask.set(t)">{{ t }}</button>
          }
        </div>

        <div class="pick-list">
          @for (c of pickable(); track c.id) {
            <button matRipple class="sheet-row" (click)="addExtra(slot, c)">
              <mat-icon>{{ chapterIcon(c) }}</mat-icon>
              <span class="sheet-name">
                {{ c.name }}
                <span class="row-sub">{{ subject(c) }} · {{ rounds(c) }}</span>
              </span>
            </button>
          }
        </div>
      </div>
    }

  `,
  styles: `
    :host {
      position: relative;
      display: flex;
      flex-direction: column;
      height: 100%;
      overflow-y: auto;
      overflow-x: hidden;
    }

    /* Calendar chrome — the month label is a control, not a headline. */
    .chrome { flex: none; padding-bottom: 8px; border-bottom: 1px solid var(--mat-sys-outline-variant); }

    .month {
      display: flex;
      align-items: center;
      gap: 4px;
      padding: 8px 16px 4px;
      border: none;
      background: transparent;
      color: var(--mat-sys-on-surface);
      cursor: pointer;
    }

    .month-title { font: var(--mat-sys-title-medium); }
    .month mat-icon { color: var(--mat-sys-on-surface-variant); transition: transform 150ms; }
    .month mat-icon.up { transform: rotate(180deg); }

    .weekdays, .grid {
      display: grid;
      grid-template-columns: repeat(7, 1fr);
      gap: 2px;
      padding: 0 12px;
    }

    .weekdays span {
      text-align: center;
      font: var(--mat-sys-label-small);
      color: var(--mat-sys-on-surface-variant);
      padding-bottom: 2px;
    }

    .cell {
      position: relative;
      height: 40px;
      display: grid;
      place-items: center;
      border: none;
      border-radius: var(--mat-sys-corner-full);
      background: transparent;
      color: var(--mat-sys-on-surface);
      font: var(--mat-sys-body-medium);
      cursor: pointer;
    }

    .cell.today .num { color: var(--mat-sys-primary); font-weight: 600; }
    .cell.on { background: var(--mat-sys-primary); }
    .cell.on .num { color: var(--mat-sys-on-primary); }
    .cell.on .dot { background: var(--mat-sys-on-primary); }
    .cell.empty { cursor: default; }

    .marks { position: absolute; bottom: 5px; display: flex; gap: 2px; }

    .dot {
      width: 4px;
      height: 4px;
      border-radius: 50%;
      background: var(--mat-sys-outline);
    }

    .dot.done { background: var(--mat-sys-primary); }

    /* One headline number, then the qualifiers under it. */
    .summary {
      flex: none;
      display: flex;
      flex-direction: column;
      gap: 4px;
      padding: 16px 16px 12px;
    }

    .headline { font: var(--mat-sys-headline-small); }
    .sub { font: var(--mat-sys-body-medium); color: var(--mat-sys-on-surface-variant); }

    .bar {
      height: 4px;
      margin-top: 4px;
      border-radius: 2px;
      background: var(--mat-sys-surface-container-highest);
      overflow: hidden;
    }

    .fill { display: block; height: 100%; background: var(--mat-sys-primary); }


    .anchors { flex: none; display: flex; gap: 8px; padding: 0 16px 12px; }

    .anchor {
      display: flex;
      align-items: center;
      gap: 6px;
      padding: 6px 12px;
      border-radius: var(--mat-sys-corner-full);
      font: var(--mat-sys-label-large);
    }

    .anchor.warn { background: var(--mat-sys-error-container); color: var(--mat-sys-on-error-container); }
    .anchor mat-icon { font-size: 18px; width: 18px; height: 18px; }

    /* Timeline */
    .timeline { padding: 0 16px 24px; }
    .row { display: flex; align-items: stretch; }
    .break-row { display: flex; align-items: center; min-height: 28px; }

    /* The clock sits above its card. As a column it cost 52px of gutter on
       every row and gave the card nothing back. */
    .row-body {
      flex: 1;
      min-width: 0;
      display: flex;
      flex-direction: column;
      gap: 4px;
      padding-left: 8px;
    }

    .clock {
      font: var(--mat-sys-label-small);
      color: var(--mat-sys-on-surface-variant);
      line-height: 14px;
    }

    .rail {
      position: relative;
      width: 20px;
      flex: none;
      display: flex;
      justify-content: center;
    }

    .line { width: 2px; margin-top: 6px; background: var(--mat-sys-outline-variant); }
    .break-row .rail { align-self: stretch; }
    .break-row .line { margin: 0; align-self: stretch; }

    .line.dashed {
      background: repeating-linear-gradient(
        var(--mat-sys-outline-variant) 0 4px,
        transparent 4px 9px
      );
    }

    /* Hollow ahead, filled once logged — the state, not the reverse. */
    .node {
      position: absolute;
      top: 1px;
      width: 10px;
      height: 10px;
      border-radius: 50%;
      background: var(--mat-sys-surface);
      box-shadow: inset 0 0 0 2px var(--mat-sys-primary);
    }

    .node.done { background: var(--mat-sys-primary); }
    .node.fixed { box-shadow: inset 0 0 0 2px var(--mat-sys-outline); }

    /* A break is a divider, not a task: centred over the card, quiet. */
    .break-body {
      flex: 1;
      min-width: 0;
      display: flex;
      justify-content: center;
      padding: 4px 0 4px 8px;
    }

    .break-chip {
      display: flex;
      align-items: center;
      gap: 6px;
      height: 28px;
      padding: 0 10px;
      border: none;
      border-radius: var(--mat-sys-corner-full);
      background: var(--mat-sys-surface-container);
      color: var(--mat-sys-on-surface-variant);
      font: var(--mat-sys-label-small);
      cursor: pointer;
    }

    .break-chip mat-icon { font-size: 14px; width: 14px; height: 14px; }

    .block {
      flex: 1;
      display: flex;
      flex-direction: column;
      gap: 4px;
      margin: 0 0 12px;
      padding: 12px 16px;
      border: none;
      border-radius: var(--mat-sys-corner-large);
      background: var(--mat-sys-surface-container-high);
      color: var(--mat-sys-on-surface);
      text-align: left;
      cursor: pointer;
    }

    .block.done { background: var(--mat-sys-surface-container); color: var(--mat-sys-on-surface-variant); }
    .block.done .title { text-decoration: line-through; }

    .block-head { display: flex; align-items: center; gap: 8px; }

    .tag {
      padding: 2px 10px;
      border-radius: var(--mat-sys-corner-full);
      font: var(--mat-sys-label-medium);
    }

    .tag-learn { background: var(--mat-sys-secondary-container); color: var(--mat-sys-on-secondary-container); }
    .tag-practice { background: var(--mat-sys-primary); color: var(--mat-sys-on-primary); }
    .tag-revise {
      background: transparent;
      color: var(--mat-sys-primary);
      box-shadow: inset 0 0 0 1px var(--mat-sys-outline);
    }
    .tag-class { background: var(--mat-sys-surface-container-highest); color: var(--mat-sys-on-surface-variant); }

    .len { margin-left: auto; font: var(--mat-sys-label-large); color: var(--mat-sys-on-surface-variant); }

    .block.fixed { cursor: default; opacity: .7; }

    .title { font: var(--mat-sys-title-medium); }
    .context { font: var(--mat-sys-body-small); color: var(--mat-sys-on-surface-variant); }

    .action {
      display: flex;
      align-items: center;
      gap: 4px;
      margin-top: 4px;
      font: var(--mat-sys-label-large);
      color: var(--mat-sys-primary);
    }

    .block.done .action { color: var(--mat-sys-on-surface-variant); }
    .action mat-icon { font-size: 18px; width: 18px; height: 18px; }

    .gap {
      flex: 1;
      display: flex;
      align-items: center;
      gap: 8px;
      margin: 0 0 12px;
      padding: 12px 16px;
      border: 1px dashed var(--mat-sys-outline-variant);
      border-radius: var(--mat-sys-corner-large);
      background: transparent;
      color: var(--mat-sys-on-surface-variant);
      font: var(--mat-sys-label-large);
      cursor: pointer;
    }

    .gap mat-icon { font-size: 18px; width: 18px; height: 18px; }

    .empty {
      display: flex;
      align-items: center;
      gap: 8px;
      padding: 24px 0;
      color: var(--mat-sys-on-surface-variant);
      font: var(--mat-sys-body-medium);
    }

    /* Bottom sheets */
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

    .sheet.tall { max-height: 78%; }

    .handle {
      width: 32px;
      height: 4px;
      margin: 0 auto 8px;
      border-radius: 2px;
      background: var(--mat-sys-outline-variant);
    }

    .sheet-title { margin: 4px 0 0; font: var(--mat-sys-title-large); }
    .sheet-sub { margin: 4px 0 0; font: var(--mat-sys-body-medium); color: var(--mat-sys-on-surface-variant); }

    .sheet-label {
      margin: 24px 0 4px;
      font: var(--mat-sys-title-small);
      color: var(--mat-sys-on-surface-variant);
    }

    .sheet-row {
      display: flex;
      align-items: center;
      gap: 16px;
      min-height: 56px;
      padding: 0 4px;
      border: none;
      background: transparent;
      color: var(--mat-sys-on-surface);
      font: var(--mat-sys-body-large);
      text-align: left;
      cursor: pointer;
    }

    .sheet-row.static { cursor: default; }
    .sheet-row mat-icon { color: var(--mat-sys-on-surface-variant); }
    .sheet-row mat-icon.ok { color: var(--mat-sys-primary); }
    .sheet-name { flex: 1; display: flex; flex-direction: column; gap: 2px; }
    .row-sub { font: var(--mat-sys-label-small); color: var(--mat-sys-on-surface-variant); }

    .pick-list { overflow-y: auto; }

    .tasks { display: flex; gap: 8px; margin-top: 16px; }

    .task-chip {
      flex: 1;
      height: 32px;
      border: 1px solid var(--mat-sys-outline-variant);
      border-radius: var(--mat-sys-corner-full);
      background: transparent;
      color: var(--mat-sys-on-surface);
      font: var(--mat-sys-label-large);
      cursor: pointer;
    }

    .task-chip.on {
      border-color: transparent;
      background: var(--mat-sys-secondary-container);
      color: var(--mat-sys-on-secondary-container);
    }

    .score-row { display: flex; align-items: flex-end; gap: 8px; margin-top: 16px; }
    .score-row label { flex: 1; display: flex; flex-direction: column; gap: 4px; }
    .field-label { font: var(--mat-sys-label-small); color: var(--mat-sys-on-surface-variant); }

    .score-row input {
      height: 40px;
      padding: 0 12px;
      border: 1px solid var(--mat-sys-outline);
      border-radius: var(--mat-sys-corner-small);
      background: transparent;
      color: var(--mat-sys-on-surface);
      font: var(--mat-sys-body-medium);
    }

    .filled-button {
      height: 48px;
      margin-top: 16px;
      border: none;
      border-radius: var(--mat-sys-corner-full);
      background: var(--mat-sys-primary);
      color: var(--mat-sys-on-primary);
      font: var(--mat-sys-label-large);
      cursor: pointer;
    }
  `,
})
export class TodayScreen {
  protected readonly store = inject(OnboardingStore);
  protected readonly study = inject(StudyStore);
  protected readonly weekdayLabels = ['S', 'M', 'T', 'W', 'T', 'F', 'S'];
  protected readonly tasks: Task[] = ['Learn', 'Practice', 'Revise'];

  protected readonly expanded = signal(false);
  protected readonly selected = signal(startOfToday());

  /** Minutes pushed onto a block, keyed by chapter+task, for this day only. */
  private readonly pushed = signal<ReadonlyMap<string, number>>(new Map());
  private readonly skipped = signal<ReadonlySet<string>>(new Set());

  protected readonly session = signal<StudyBlock | null>(null);
  protected readonly picker = signal<{ startMinute: number; minutes: number } | null>(null);
  protected readonly pickTask = signal<Task>('Learn');
  protected readonly breakOpen = signal(false);
  protected readonly breakChoices = [5, 10, 15, 20, 30];
  protected readonly attempted = signal(0);
  protected readonly correct = signal(0);

  protected readonly key = computed(() => dateKey(this.selected()));

  /* ---- Calendar ------------------------------------------------------ */

  protected readonly weekCells = computed<DayCell[]>(() => {
    const start = addDays(this.selected(), -this.selected().getDay());
    return Array.from({ length: 7 }, (_, i) => this.cell(addDays(start, i)));
  });

  protected readonly monthCells = computed<(DayCell | null)[]>(() => {
    const first = new Date(this.selected().getFullYear(), this.selected().getMonth(), 1);
    const days = new Date(first.getFullYear(), first.getMonth() + 1, 0).getDate();
    const lead = Array.from({ length: first.getDay() }, () => null);
    const cells = Array.from({ length: days }, (_, i) =>
      this.cell(new Date(first.getFullYear(), first.getMonth(), i + 1)),
    );
    return [...lead, ...cells];
  });

  /* ---- The day ------------------------------------------------------- */

  protected readonly blocks = computed<Block[]>(() => {
    const date = this.selected();
    const weekday = date.getDay();
    const commitments = this.store.commitments();

    const windows = freeWindows(
      commitments,
      weekday,
      this.store.wakeMinute(),
      this.store.sleepMinute(),
    );

    const askedHours = weekday === 0 || weekday === 6
      ? this.store.weekendHours()
      : this.store.weekdayHours();
    const free = windows.reduce((n, w) => n + w.minutes, 0);
    // Never plan more than the day physically has, even if the slider says so.
    const target = Math.min(askedHours * 60, free);

    const candidates = dayCandidates({
      doneUnits: this.planningDone(),
      learnedSubtopics: this.study.learnedSubtopics(),
      stat: (id) => this.study.stat(id),
      slots: 8,
    });

    const laid = layOutDay(
      windows,
      commitments,
      weekday,
      candidates,
      target,
      this.coachingName(),
      this.store.breakMinutes(),
    );

    return this.applyEdits(laid);
  });

  /**
   * Ticks made *today* are held back from the planner, so finishing a block
   * does not rewrite the day underneath the user — the block stays where it
   * is and turns into a logged one.
   */
  private readonly planningDone = computed(() => {
    const today = new Set(
      this.study.sessionsOn(this.key()).map((s) => s.subtopicId).filter((id): id is string => !!id),
    );
    if (today.size === 0) return this.store.doneUnits();
    const out = new Set(this.store.doneUnits());
    for (const id of today) out.delete(id);
    return out as ReadonlySet<string>;
  });

  /** Pushes, skips, extras and logged state, applied over the generated day. */
  private applyEdits(blocks: Block[]): Block[] {
    const key = this.key();
    const pushed = this.pushed();
    const skipped = this.skipped();

    const out = blocks
      .filter((b) => b.kind !== 'study' || !skipped.has(blockKey(b)))
      .map((b) => {
        if (b.kind !== 'study') return b;
        return {
          ...b,
          startMinute: b.startMinute + (pushed.get(blockKey(b)) ?? 0),
          done: this.study.isLogged(key, b.chapterId, b.task, b.subtopicId),
        };
      });

    for (const extra of this.study.extrasOn(key)) {
      const chapter = ALL_CHAPTERS.find((c) => c.id === extra.chapterId);
      if (!chapter) continue;
      const subtopic = chapter.subtopics.find((t) => t.id === extra.subtopicId);
      out.push({
        kind: 'study',
        startMinute: extra.startMinute,
        minutes: extra.minutes,
        task: extra.task,
        title: subtopic?.name ?? chapter.name,
        context: subtopic ? `${subjectLabel(chapter)} · ${chapter.name}` : subjectLabel(chapter),
        chapterId: chapter.id,
        subtopicId: extra.subtopicId,
        questions: extra.task === 'Practice' ? Math.round(extra.minutes / 1.2) : undefined,
        done: this.study.isLogged(key, chapter.id, extra.task, extra.subtopicId),
      });
    }

    // An added block eats the free slot it was dropped into.
    const claimed = this.study.extrasOn(key);
    return out
      .filter(
        (b) =>
          b.kind !== 'gap' ||
          !claimed.some((e) => e.startMinute >= b.startMinute && e.startMinute < b.startMinute + b.minutes),
      )
      .sort((a, b) => a.startMinute - b.startMinute);
  }

  protected readonly plannedMinutes = computed(() =>
    this.blocks().filter((b) => b.kind === 'study').reduce((n, b) => n + b.minutes, 0),
  );

  protected readonly loggedMinutes = computed(() => this.study.minutesOn(this.key()));

  protected readonly donePercent = computed(() => {
    const planned = this.plannedMinutes();
    return planned === 0 ? 0 : Math.min(100, Math.round((this.loggedMinutes() / planned) * 100));
  });

  /** The one number the screen is about: what is still owed today. */
  protected headline(): string {
    const left = Math.max(0, this.plannedMinutes() - this.loggedMinutes());
    if (this.plannedMinutes() === 0) return 'Nothing scheduled';
    return left === 0 ? 'Day complete' : `${this.format(left)} left`;
  }

  protected subline(): string {
    const parts = [`${this.format(this.loggedMinutes())} of ${this.format(this.plannedMinutes())} done`];
    const fixed = this.blocks().filter((b) => b.kind === 'fixed').reduce((n, b) => n + b.minutes, 0);
    if (fixed > 0) parts.push(`${this.format(fixed)} fixed`);
    parts.push(`${this.store.days()} days to exam`);
    return parts.join(' · ');
  }

  protected readonly backlog = computed(() => {
    const elapsed = Math.max(0, Math.round((startOfToday().getTime() - this.planStart) / 86_400_000));
    const expected = Math.floor(elapsed / 3);
    const done = ALL_CHAPTERS.filter((c) => chapterIsDone(c, this.store.doneUnits())).length;
    return Math.max(0, expected - done);
  });

  private readonly planStart = startOfToday().getTime();

  /* ---- Sheets -------------------------------------------------------- */

  protected openSession(block: StudyBlock): void {
    this.attempted.set(block.questions ?? 0);
    this.correct.set(0);
    this.session.set(block);
  }

  protected closeSession(): void { this.session.set(null); }

  protected complete(block: StudyBlock): void {
    this.study.log({
      dateKey: this.key(),
      chapterId: block.chapterId,
      subtopicId: block.subtopicId,
      title: block.title,
      task: block.task,
      minutes: block.minutes,
      attempted: block.task === 'Practice' ? this.attempted() : undefined,
      correct: block.task === 'Practice' ? this.correct() : undefined,
    });
    this.session.set(null);
  }

  protected push(block: StudyBlock, minutes: number): void {
    if (minutes >= 24 * 60) {
      this.skip(block);
      return;
    }
    const next = new Map(this.pushed());
    next.set(blockKey(block), (next.get(blockKey(block)) ?? 0) + minutes);
    this.pushed.set(next);
    this.session.set(null);
  }

  protected skip(block: StudyBlock): void {
    this.skipped.set(new Set(this.skipped()).add(blockKey(block)));
    this.session.set(null);
  }

  protected setBreak(minutes: number): void {
    this.store.breakMinutes.set(minutes);
    this.breakOpen.set(false);
  }

  protected openPicker(startMinute: number, minutes: number): void {
    this.picker.set({ startMinute, minutes });
  }

  /** What a free slot can be filled with, given the chosen task. */
  protected readonly pickable = computed<Chapter[]>(() => {
    const done = this.store.doneUnits();
    const task = this.pickTask();
    const pool = task === 'Learn'
      ? ALL_CHAPTERS.filter((c) => !chapterIsDone(c, done))
      : ALL_CHAPTERS.filter((c) => chapterIsDone(c, done) || this.study.stat(c.id).lastTouched);
    return (pool.length > 0 ? pool : ALL_CHAPTERS).slice(0, 20);
  });

  protected addExtra(slot: { startMinute: number; minutes: number }, chapter: Chapter): void {
    const subtopic = chapter.subtopics.find((t) => !this.store.doneUnits().has(t.id));
    this.study.addExtra({
      dateKey: this.key(),
      startMinute: slot.startMinute,
      minutes: Math.min(slot.minutes, 60),
      task: this.pickTask(),
      chapterId: chapter.id,
      subtopicId: this.pickTask() === 'Learn' ? subtopic?.id : undefined,
    });
    this.picker.set(null);
  }

  /* ---- Small helpers -------------------------------------------------- */

  protected subject(chapter: Chapter): string { return subjectLabel(chapter); }

  protected chapterIcon(chapter: Chapter): string {
    return this.isDone(chapter) ? 'check_circle' : 'radio_button_unchecked';
  }

  protected isDone(chapter: Chapter): boolean {
    return chapterIsDone(chapter, this.store.doneUnits());
  }

  /** R1 / R2 / R3 — revision depth, the thing a flat percentage hides. */
  protected rounds(chapter: Chapter): string {
    const stat = this.study.stat(chapter.id);
    if (!this.isDone(chapter)) return 'not covered';
    return stat.revisions === 0 ? 'learnt, not revised' : `R${stat.revisions} done`;
  }

  private coachingName(): string {
    return COACHINGS.find((c) => c.id === this.store.coachingId())?.label ?? 'Coaching';
  }

  protected isToday(date: Date): boolean {
    return date.toDateString() === startOfToday().toDateString();
  }

  protected isSelected(date: Date): boolean {
    return date.toDateString() === this.selected().toDateString();
  }

  /** Duration has spatial meaning, but an empty evening is not worth 300px. */
  protected height(block: Block): number {
    const cap = block.kind === 'gap' ? 96 : 200;
    return Math.min(cap, Math.max(MIN_BLOCK_HEIGHT, Math.round(block.minutes * PX_PER_MINUTE)));
  }

  protected clock(minuteOfDay: number): string {
    const h = Math.floor(minuteOfDay / 60);
    const m = minuteOfDay % 60;
    const suffix = h < 12 ? 'AM' : 'PM';
    const hour = h % 12 === 0 ? 12 : h % 12;
    return `${hour}:${String(m).padStart(2, '0')} ${suffix}`;
  }

  protected format(minutes: number): string {
    if (minutes <= 0) return '0m';
    const h = Math.floor(minutes / 60);
    const m = minutes % 60;
    if (h === 0) return `${m}m`;
    return m === 0 ? `${h}h` : `${h}h ${m}m`;
  }

  private cell(date: Date): DayCell {
    const key = dateKey(date);
    return {
      date,
      day: date.getDate(),
      planned: date >= startOfToday() && date <= this.store.targetDate(),
      logged: this.study.minutesOn(key) > 0,
    };
  }
}

function blockKey(block: StudyBlock): string {
  return `${block.chapterId}|${block.task}|${block.subtopicId ?? ''}`;
}
