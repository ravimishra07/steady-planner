import { ChangeDetectionStrategy, Component, computed, inject, output, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import { MatRippleModule } from '@angular/material/core';
import { COACHINGS, OnboardingStore, addDays, startOfToday } from '../onboarding/state';
import { ALL_CHAPTERS, Chapter, chapterIsDone } from '../onboarding/exam-pack';
import { StudyStore, Task, dateKey } from '../study/study-store';
import { RECALLS, Recall, nextInterval } from '../study/retention';
import { Block, StudyBlock, subjectLabel } from './scheduler';
import { DayPlanner, blockKey } from './day-planner';
import { FocusStore } from '../focus/focus-store';

interface DayCell {
  date: Date;
  day: number;
  label: string;
  planned: boolean;
  /** Minutes logged, and the day's own target, for the completion bar. */
  minutes: number;
  target: number;
}

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

      @if (expanded()) {
        <div class="weekdays">
          @for (d of weekdayLabels; track $index) { <span>{{ d }}</span> }
        </div>
      }

      <div class="grid" [class.month-grid]="expanded()">
        @for (cell of (expanded() ? monthCells() : weekCells()); track $index) {
          @if (cell) {
            <button
              class="cell"
              [class.on]="isSelected(cell.date)"
              [class.today]="isToday(cell.date)"
              [class.past]="cell.date < today"
              (click)="selected.set(cell.date)">
              @if (!expanded()) { <span class="dow">{{ cell.label }}</span> }
              <span class="num">{{ cell.day }}</span>
              <span class="meter" [class.untouched]="cell.minutes === 0">
                <span class="meter-fill" [style.width.%]="fill(cell)"></span>
              </span>
            </button>
          } @else {
            <span class="cell empty"></span>
          }
        }
      </div>
    </header>

    <section class="summary">
      <button matRipple class="summary-head" (click)="summaryOpen.set(!summaryOpen())"
              [attr.aria-expanded]="summaryOpen()">
        <span class="headline">{{ headline() }}</span>
        <mat-icon class="summary-chevron" [class.up]="summaryOpen()">keyboard_arrow_down</mat-icon>
        <span class="grow"></span>
        <span matRipple class="edit-plan" (click)="editPlan.emit(); $event.stopPropagation()">
          <mat-icon>edit_calendar</mat-icon>
          Edit plan
        </span>
      </button>

      @if (summaryOpen()) {
      <!-- The day on a clock, not a progress bar: blocks sit where they fall
           between waking and sleeping, and the marker says where you are. -->
      <div class="clockbar" role="img" [attr.aria-label]="shapeLabel()">
        <div class="track">
          @for (seg of shape(); track seg.key) {
            <span class="seg"
                  [class]="'seg-' + seg.kind"
                  [class.done]="seg.done"
                  [class.missed]="seg.missed"
                  [style.left.%]="seg.left"
                  [style.width.%]="seg.width"
                  [attr.title]="seg.title"></span>
          }
          @if (nowFraction() !== null) {
            <span class="now" [style.left.%]="nowFraction()!"></span>
          }
        </div>
        <div class="ticks">
          <span>{{ clock(store.wakeMinute()) }}</span>
          @if (nowFraction() !== null) { <span class="now-label">now</span> }
          <span>{{ clock(store.sleepMinute()) }}</span>
        </div>
      </div>

      <div class="facts">
        <span class="fact">
          <mat-icon class="filled">check_circle</mat-icon>
          {{ format(loggedMinutes()) }} of {{ format(plannedMinutes()) }}
        </span>

        @if (fixedMinutes() > 0) {
          <span class="fact">
            <mat-icon>lock</mat-icon>
            {{ format(fixedMinutes()) }} fixed
          </span>
        }

        @if (nextUp(); as next) {
          <span class="fact">
            <mat-icon>schedule</mat-icon>
            {{ next }}
          </span>
        }
      </div>
      }
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
              <div matRipple class="block" [class.done]="block.done" (click)="openSession(block)">
                <span class="block-head">
                  <span class="tag" [class]="'tag-' + block.task.toLowerCase()">{{ block.task }}</span>
                  @if (block.overdue !== undefined && block.overdue > 0) {
                    <span class="overdue">{{ block.overdue }}d overdue</span>
                  } @else if (block.overdue === 0) {
                    <span class="due">due today</span>
                  }
                  <span class="len">{{ format(block.minutes) }}</span>
                </span>
                <span class="title">{{ block.title }}</span>
                <span class="context">
                  {{ block.context }}@if (block.questions) { · {{ block.questions }} Q }
                </span>
                @if (block.done) {
                  <span class="action">
                    <mat-icon class="filled">check_circle</mat-icon>Logged
                  </span>
                } @else {
                  <button matRipple class="action start" (click)="startTimer(block, $event)">
                    <mat-icon class="filled">play_arrow</mat-icon>
                    Start {{ format(block.minutes) }}
                  </button>
                }
              </div>
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

        <!-- Why the plan put this here, from the chapter's own record. -->
        <div class="why">
          @for (line of why(s); track line) { <span class="why-line">{{ line }}</span> }
        </div>

        <button matRipple class="filled-button" (click)="startTimer(s)">
          <mat-icon class="filled">play_arrow</mat-icon>
          Start {{ format(s.minutes) }} in Focus
        </button>

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

        <h4 class="sheet-label">Or log it without the timer</h4>
        <div class="recalls">
          @for (r of recalls; track r.id) {
            <button matRipple class="recall" [class.on]="recall() === r.id" (click)="recall.set(r.id)">
              <mat-icon>{{ r.icon }}</mat-icon>
              {{ r.label }}
            </button>
          }
        </div>
        <p class="next-due">{{ nextDueLabel(s) }}</p>

        <button matRipple class="tonal-button" (click)="complete(s)">
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
    .chrome {
      flex: none;
      display: flex;
      flex-direction: column;
      padding-bottom: 8px;
      border-bottom: 1px solid var(--mat-sys-outline-variant);
    }

    /* A control, not a caption: it has a shape, a state layer and a hit area
       that matches what it looks like. */
    .month {
      display: flex;
      align-items: center;
      gap: 2px;
      align-self: flex-start;
      height: 40px;
      margin: 4px 8px 4px 8px;
      padding: 0 8px 0 12px;
      border: none;
      border-radius: var(--mat-sys-corner-full);
      background: transparent;
      color: var(--mat-sys-on-surface);
      cursor: pointer;
      transition: background 140ms ease;
    }

    .month:hover { background: var(--mat-sys-surface-container-high); }
    .month-title { font: var(--mat-sys-title-medium); letter-spacing: .1px; }
    .month mat-icon { color: var(--mat-sys-on-surface-variant); transition: transform 150ms; }
    .month mat-icon.up { transform: rotate(180deg); }

    .weekdays, .grid {
      display: grid;
      grid-template-columns: repeat(7, 1fr);
      padding: 0 8px;
    }

    .grid { row-gap: 2px; column-gap: 2px; }

    .weekdays span {
      text-align: center;
      font: var(--mat-sys-label-medium);
      color: var(--mat-sys-on-surface-variant);
      padding-bottom: 8px;
    }

    /* A day is one object — its letter, its number and how full it was —
       rather than a letter in one row and a circle in another. */
    .cell {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      gap: 6px;
      padding: 8px 0 10px;
      border: none;
      border-radius: 22px;
      background: transparent;
      color: var(--mat-sys-on-surface);
      cursor: pointer;
      transition: background 140ms ease;
    }

    .dow { font: var(--mat-sys-label-small); color: var(--mat-sys-on-surface-variant); line-height: 1; }
    .num { font: var(--mat-sys-title-medium); line-height: 1; }
    .cell.past .num, .cell.past .dow { opacity: .55; }

    /* Selected is the whole day, not a ring around its number. */
    .cell.on {
      background: var(--mat-sys-secondary-container);
      color: var(--mat-sys-on-secondary-container);
    }

    .cell.on .dow { color: var(--mat-sys-on-secondary-container); opacity: .8; }
    .cell.on .num { font-weight: 600; }
    .cell.on.past .num, .cell.on.past .dow { opacity: 1; }
    .cell.today .num { color: var(--mat-sys-primary); }
    .cell.today.on .num { color: var(--mat-sys-on-secondary-container); }
    .cell.empty { cursor: default; }

    /* How full the day was, in place of a dot that only said yes or no. */
    .meter {
      width: 20px;
      height: 3px;
      border-radius: 2px;
      background: var(--mat-sys-surface-container-highest);
      overflow: hidden;
    }

    .meter.untouched { background: transparent; box-shadow: inset 0 0 0 1px var(--mat-sys-surface-container-highest); }
    .meter-fill { display: block; height: 100%; border-radius: 2px; background: var(--mat-sys-primary); }
    .cell.on .meter-fill { background: var(--mat-sys-on-secondary-container); }

    /* The month grid keeps the compact form: no weekday letter per cell. */
    .month-grid .cell { border-radius: 18px; padding: 6px 0 8px; gap: 4px; }

    /* A card, so the day's summary is an object rather than something that
       bleeds into the timeline underneath it. */
    .summary {
      flex: none;
      display: flex;
      flex-direction: column;
      margin: 12px 16px 20px;
      padding: 4px 16px 16px;
      border-radius: 24px;
      background: var(--mat-sys-surface-container-low);
    }

    .headline { font: var(--mat-sys-headline-small); }
    .summary-head {
      display: flex;
      align-items: center;
      gap: 4px;
      width: 100%;
      padding: 12px 0 0;
      border: none;
      background: transparent;
      color: inherit;
      text-align: left;
      cursor: pointer;
    }

    .grow { flex: 1; }
    .summary-chevron { color: var(--mat-sys-on-surface-variant); transition: transform 180ms ease; }
    .summary-chevron.up { transform: rotate(180deg); }
    /* Labelled, because an unlabelled calendar-pencil could mean anything —
       but quiet, because the headline is what the eye should land on. */
    .edit-plan {
      display: flex;
      align-items: center;
      gap: 4px;
      height: 32px;
      flex: none;
      padding: 0 4px;
      border: 0;
      background: transparent;
      color: var(--mat-sys-on-surface-variant);
      font: var(--mat-sys-label-medium);
      cursor: pointer;
    }

    .edit-plan mat-icon { width: 16px; height: 16px; font-size: 16px; }

    .clockbar { margin: 12px 0 2px; }

    /* The empty track is the day itself; the gaps in it are real gaps. */
    .track {
      position: relative;
      height: 10px;
      border-radius: 5px;
      background: var(--mat-sys-surface-container-high);
    }

    .seg { position: absolute; top: 0; bottom: 0; border-radius: 5px; }

    /* A sitting still to do, a sitting logged, and an hour that was never
       yours to spend — three states, one bar. */
    .seg-study { background: transparent; box-shadow: inset 0 0 0 1.5px var(--mat-sys-primary); }
    .seg-study.done { background: var(--mat-sys-primary); box-shadow: none; }

    /* Its hour came and went. Stated, not scolded — the colour is the whole
       message and there is no counter nagging about it. */
    .seg-study.missed { box-shadow: inset 0 0 0 1.5px var(--mat-sys-outline); opacity: .6; }

    .seg-fixed {
      background: repeating-linear-gradient(
        135deg,
        var(--mat-sys-surface-container-highest) 0 3px,
        transparent 3px 6px
      );
    }

    .now {
      position: absolute;
      top: -3px;
      bottom: -3px;
      width: 2px;
      border-radius: 1px;
      background: var(--mat-sys-on-surface);
      box-shadow: 0 0 0 2px var(--mat-sys-surface);
    }

    .ticks {
      display: flex;
      justify-content: space-between;
      margin-top: 6px;
      font: var(--mat-sys-label-small);
      color: var(--mat-sys-on-surface-variant);
    }

    .now-label { color: var(--mat-sys-on-surface); }

    .facts { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 12px; }

    /* Static chips, not buttons: they are readings, and giving them a
       container stops them floating loose under the bar. */
    .fact {
      display: flex;
      align-items: center;
      gap: 6px;
      height: 32px;
      padding: 0 12px 0 10px;
      border-radius: var(--mat-sys-corner-full);
      background: var(--mat-sys-surface-container-high);
      color: var(--mat-sys-on-surface-variant);
      font: var(--mat-sys-label-medium);
      white-space: nowrap;
    }

    .fact mat-icon { font-size: 16px; width: 16px; height: 16px; }
    .fact:first-child { color: var(--mat-sys-on-surface); }
    .fact:first-child mat-icon { color: var(--mat-sys-primary); }


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

    .overdue { font: var(--mat-sys-label-small); color: var(--mat-sys-error); }
    .due { font: var(--mat-sys-label-small); color: var(--mat-sys-primary); }

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

    /* Start is its own target inside the card: the card opens the detail,
       the button starts the timer. */
    .action.start {
      align-self: flex-start;
      height: 32px;
      padding: 0 14px 0 10px;
      border: none;
      border-radius: var(--mat-sys-corner-full);
      background: var(--mat-sys-secondary-container);
      color: var(--mat-sys-on-secondary-container);
      font: var(--mat-sys-label-large);
      cursor: pointer;
    }
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

    /* Three taps, no numbers — the only self-report the app asks for. */
    .recalls { display: flex; gap: 8px; margin-top: 12px; }

    .recall {
      flex: 1;
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 2px;
      padding: 10px 4px;
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

    .next-due {
      margin: 8px 0 0;
      font: var(--mat-sys-label-medium);
      color: var(--mat-sys-on-surface-variant);
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

    .why { display: flex; flex-direction: column; gap: 2px; margin: 12px 0 4px; }

    .why-line {
      font: var(--mat-sys-body-small);
      color: var(--mat-sys-on-surface-variant);
    }

    .why-line:first-child { color: var(--mat-sys-on-surface); }

    .tonal-button {
      height: 44px;
      margin-top: 12px;
      border: none;
      border-radius: var(--mat-sys-corner-full);
      background: var(--mat-sys-surface-container-highest);
      color: var(--mat-sys-on-surface);
      font: var(--mat-sys-label-large);
      cursor: pointer;
    }

    .filled-button {
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 8px;
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
  readonly editPlan = output<void>();
  readonly openFocus = output<void>();
  protected readonly store = inject(OnboardingStore);
  protected readonly study = inject(StudyStore);
  private readonly planner = inject(DayPlanner);
  private readonly focus = inject(FocusStore);
  protected readonly weekdayLabels = ['S', 'M', 'T', 'W', 'T', 'F', 'S'];
  protected readonly tasks: Task[] = ['Learn', 'Practice', 'Revise'];

  protected readonly expanded = signal(false);
  protected readonly summaryOpen = signal(true);
  protected readonly today = startOfToday();
  protected readonly selected = signal(startOfToday());

  protected readonly session = signal<StudyBlock | null>(null);
  protected readonly picker = signal<{ startMinute: number; minutes: number } | null>(null);
  protected readonly pickTask = signal<Task>('Learn');
  protected readonly breakOpen = signal(false);
  protected readonly breakChoices = [5, 10, 15, 20, 30];
  protected readonly recalls = RECALLS;
  protected readonly recall = signal<Recall>('okay');
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

  protected readonly blocks = computed<Block[]>(() => this.planner.blocksFor(this.selected()));


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

  protected readonly fixedMinutes = computed(() =>
    this.blocks().filter((b) => b.kind === 'fixed').reduce((n, b) => n + b.minutes, 0),
  );

  /** Minute of the day, kept fresh so the marker does not go stale. */
  private readonly nowMinute = signal(minuteOfDay());

  /**
   * The day laid on a clock. Placing blocks by when they happen — rather than
   * one after another — is the only way the strip says something the timeline
   * below does not: how much of the day is already behind you.
   */
  protected readonly shape = computed(() => {
    const wake = this.store.wakeMinute();
    const span = Math.max(60, this.store.sleepMinute() - wake);

    const now = this.isToday(this.selected()) ? this.nowMinute() : null;

    return this.blocks()
      .filter((b) => b.kind === 'study' || b.kind === 'fixed')
      .map((b) => ({
        key: b.kind + b.startMinute,
        kind: b.kind,
        done: b.kind === 'study' && b.done,
        // Its slot has gone by and nothing was logged against it.
        missed:
          b.kind === 'study' && !b.done && now !== null && now > b.startMinute + b.minutes,
        left: clamp(((b.startMinute - wake) / span) * 100),
        width: Math.max(1.5, clamp((b.minutes / span) * 100)),
        title:
          b.kind === 'study'
            ? `${b.task} · ${b.title} · ${this.clock(b.startMinute)}`
            : `${b.title} · ${this.clock(b.startMinute)}`,
      }));
  });

  /** Only today gets a marker — a past or future day has no "now". */
  protected readonly nowFraction = computed(() => {
    if (!this.isToday(this.selected())) return null;
    const wake = this.store.wakeMinute();
    const span = Math.max(60, this.store.sleepMinute() - wake);
    const at = ((this.nowMinute() - wake) / span) * 100;
    return at < 0 || at > 100 ? null : clamp(at);
  });

  /**
   * When the next unlogged sitting starts. The third fact worth a chip: the
   * other two say what the day holds, this one says when to look up.
   */
  protected nextUp(): string | null {
    const now = this.isToday(this.selected()) ? this.nowMinute() : null;
    const owed = this.blocks().filter(
      (b): b is StudyBlock => b.kind === 'study' && !b.done,
    );
    if (owed.length === 0) return null;
    if (now === null) return `Starts ${this.clock(owed[0].startMinute)}`;

    const ahead = owed.find((b) => b.startMinute + b.minutes > now);
    if (!ahead) return `${owed.length} to reschedule`;
    return ahead.startMinute <= now ? 'On now' : `Next ${this.clock(ahead.startMinute)}`;
  }

  protected shapeLabel(): string {
    const total = this.shape().filter((s) => s.kind === 'study').length;
    const done = this.shape().filter((s) => s.done).length;
    return `${done} of ${total} sittings done`;
  }

  protected readonly backlog = computed(() => {
    const elapsed = Math.max(0, Math.round((startOfToday().getTime() - this.planStart) / 86_400_000));
    const expected = Math.floor(elapsed / 3);
    const done = ALL_CHAPTERS.filter((c) => chapterIsDone(c, this.store.doneUnits())).length;
    return Math.max(0, expected - done);
  });

  private readonly planStart = startOfToday().getTime();

  /* ---- Sheets -------------------------------------------------------- */

  /**
   * Start actually starts. It used to open the logging sheet, which asked the
   * student to record work they had not done yet — the timer is one tab away
   * and it writes the sitting itself.
   */
  protected startTimer(block: StudyBlock, event?: Event): void {
    event?.stopPropagation();
    this.focus.start(
      {
        chapterId: block.chapterId,
        subtopicId: block.subtopicId,
        title: block.title,
        context: block.context,
        task: block.task,
        minutes: block.minutes,
      },
      block.minutes,
    );
    this.session.set(null);
    this.openFocus.emit();
  }

  /** The chapter's own record, in the order it answers "why this, now". */
  protected why(block: StudyBlock): string[] {
    const stat = this.study.stat(block.chapterId);
    const out: string[] = [];

    if (block.overdue !== undefined && block.overdue > 0) {
      out.push(`Revision was due ${block.overdue} days ago`);
    } else if (block.overdue === 0) {
      out.push('Revision falls due today');
    } else if (block.task === 'Learn') {
      out.push('Next unread section in this subject');
    } else if (block.task === 'Practice') {
      out.push('Recently covered, not yet practised much');
    }

    if (stat.lastTouched) out.push(`Last opened ${this.since(stat.lastTouched)}`);
    else out.push('Never opened');

    if (stat.revisions > 0) out.push(`${stat.revisions} revision passes done`);
    if (stat.attempted > 0) {
      out.push(`${Math.round((stat.correct / stat.attempted) * 100)}% of ${stat.attempted} questions`);
    }
    return out;
  }

  private since(key: string): string {
    const [y, m, d] = key.split('-').map(Number);
    const days = Math.round((startOfToday().getTime() - new Date(y, m - 1, d).getTime()) / 86_400_000);
    if (days <= 0) return 'today';
    return days === 1 ? 'yesterday' : `${days} days ago`;
  }

  protected openSession(block: StudyBlock): void {
    this.attempted.set(block.questions ?? 0);
    this.correct.set(0);
    this.recall.set(this.study.stat(block.chapterId).recall ?? 'okay');
    this.session.set(block);
  }

  /** Says out loud what marking this will do to the schedule. */
  protected nextDueLabel(block: StudyBlock): string {
    const stat = this.study.stat(block.chapterId);
    const revisions = block.task === 'Revise' ? Math.min(4, stat.revisions + 1) : stat.revisions;
    const days = nextInterval(revisions, this.recall());
    return `Next look in ${days} day${days === 1 ? '' : 's'}.`;
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
      recall: this.recall(),
    });
    this.session.set(null);
  }

  protected push(block: StudyBlock, minutes: number): void {
    if (minutes >= 24 * 60) {
      this.skip(block);
      return;
    }
    this.planner.push(block, minutes);
    this.session.set(null);
  }

  protected skip(block: StudyBlock): void {
    this.planner.skip(block);
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
    const weekend = date.getDay() === 0 || date.getDay() === 6;
    return {
      date,
      day: date.getDate(),
      label: this.weekdayLabels[date.getDay()],
      planned: date >= startOfToday() && date <= this.store.targetDate(),
      minutes: this.study.minutesOn(dateKey(date)),
      target: (weekend ? this.store.weekendHours() : this.store.weekdayHours()) * 60,
    };
  }

  /** How full a day is, for the bar under its number. */
  protected fill(cell: DayCell): number {
    if (cell.target <= 0) return 0;
    return Math.min(100, Math.round((cell.minutes / cell.target) * 100));
  }
}

function minuteOfDay(): number {
  const now = new Date();
  return now.getHours() * 60 + now.getMinutes();
}

function clamp(value: number): number {
  return Math.min(100, Math.max(0, value));
}
