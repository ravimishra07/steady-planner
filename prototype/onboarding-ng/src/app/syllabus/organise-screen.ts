import { ChangeDetectionStrategy, Component, computed, inject, output, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';
import { MatRippleModule } from '@angular/material/core';
import { ORDER_MODES, OrderMode, OnboardingStore, addDays, startOfToday } from '../onboarding/state';
import { Chapter, CustomChapter, PACK, Subtopic, chapterIsDone, mergedSubjects } from '../onboarding/exam-pack';
import { availableChapters, marksOf, orderedChapters } from '../onboarding/sequence';
import { StudyStore, dateKey } from '../study/study-store';
import { Landing, overflow, project } from './projection';
import { DayPlanner } from '../home/day-planner';

/** Everything the student is changing, held apart until they approve it. */
interface Draft {
  parked: Set<string>;
  orderModes: Map<string, OrderMode>;
  customOrder: Map<string, string[]>;
  taughtUpTo: Map<string, string | null>;
  customChapters: CustomChapter[];
  chapterNames: Map<string, string>;
  customSubtopics: Map<string, Subtopic[]>;
  subtopicNames: Map<string, string>;
  hiddenSubtopics: Set<string>;
}

/**
 * Organise: pick the order, say how far the class has reached, and drop what
 * is not being attempted — then see what it does to the plan before it takes
 * effect. Edits are held in a draft, because changing the syllabus silently
 * reshapes tomorrow and the student should see that first.
 */
@Component({
  selector: 'app-organise-screen',
  imports: [MatIconModule, MatRippleModule, DatePipe, DecimalPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <header class="bar">
      <button matRipple class="icon-btn" (click)="close.emit()" aria-label="Back">
        <mat-icon>arrow_back</mat-icon>
      </button>
      <h1 class="bar-title">Organise</h1>
      @if (dirty()) {
        <span class="dirty">{{ changeCount() }} {{ changeCount() === 1 ? 'change' : 'changes' }}</span>
      }
    </header>

    <div class="view-tabs" role="tablist" aria-label="Organise views">
      <button matRipple role="tab" [attr.aria-selected]="view() === 'plan'" [class.on]="view() === 'plan'" (click)="view.set('plan')">Plan</button>
      <button matRipple role="tab" [attr.aria-selected]="view() === 'syllabus'" [class.on]="view() === 'syllabus'" (click)="view.set('syllabus')">Syllabus</button>
    </div>

    @if (view() === 'syllabus') {
      <div class="tabs">
        @for (s of subjects(); track s.id) {
          <button matRipple class="tab" [class.on]="current() === s.id" (click)="current.set(s.id)">{{ s.name }}</button>
        }
      </div>
    }

    <div class="scroll">
      @if (view() === 'plan') {
        <section class="plan-section">
          <span class="eyebrow">Today</span>
          <h2 class="plan-title">{{ todayMinutes() / 60 | number: '1.0-1' }}h planned</h2>
          <div class="today-list">
            @for (block of todayBlocks(); track blockKey(block)) {
              <div class="today-row">
                <span class="task-icon"><mat-icon>{{ taskIcon(block.task) }}</mat-icon></span>
                <span class="today-copy"><strong>{{ block.title }}</strong><small>{{ block.task }} · {{ block.minutes }}m · {{ minuteLabel(block.startMinute) }}</small></span>
              </div>
            } @empty {
              <p class="empty">No study blocks fit today. Check fixed hours or daily hours.</p>
            }
          </div>
        </section>

        <section class="plan-section">
          <span class="eyebrow">Next 7 days</span>
          <div class="week-strip" role="list" aria-label="Seven day forecast">
            @for (day of week(); track day.key; let i = $index) {
              <button matRipple role="listitem" [class.on]="selectedDay() === i" (click)="selectedDay.set(i)">
                <span>{{ day.weekday }}</span><strong>{{ day.day }}</strong><small>{{ day.hours }}h</small>
              </button>
            }
          </div>
          @if (week()[selectedDay()]; as day) {
            <div class="day-detail">
              <span><strong>{{ day.label }}</strong><small>{{ day.hours }}h study · {{ day.revision }}h revision load</small></span>
              @if (day.finishes.length) {
                <span class="finishes">Finish: {{ day.finishes.join(', ') }}</span>
              } @else {
                <span class="finishes muted">Work continues; no chapter finishes this day</span>
              }
            </div>
          }
        </section>

        <section class="plan-section">
          <span class="eyebrow">Timeline to exam</span>
          <h2 class="plan-title">What will be done by when</h2>
          <div class="timeline">
            @for (item of visibleMilestones(); track item.chapter.id) {
              <div class="milestone" [class.late]="!item.fits">
                <span class="rail-dot"></span>
                <span class="milestone-date">{{ item.date | date: 'd MMM' }}</span>
                <span class="milestone-copy"><strong>{{ item.chapter.name }}</strong><small>{{ subjectName(item.chapter) }} · {{ item.fits ? 'finished' : 'after exam' }}</small></span>
              </div>
            } @empty {
              <p class="empty">Everything currently in play is already done.</p>
            }
            <div class="exam-marker"><mat-icon>flag</mat-icon><span><strong>Exam</strong><small>{{ store.targetDate() | date: 'd MMM yyyy' }}</small></span></div>
          </div>
          @if (landings().length > milestoneLimit()) {
            <button matRipple class="more-milestones" (click)="milestoneLimit.set(milestoneLimit() + 12)">Show later milestones</button>
          }
        </section>

        @if (missed().length > 0) {
          <div class="warn plan-warn"><mat-icon>event_busy</mat-icon><span class="warn-text"><span class="warn-head">{{ missed().length }} chapters miss the exam</span><span class="warn-sub">{{ missedHours() }} estimated hours remain after {{ store.targetDate() | date: 'd MMM' }}</span></span><button matRipple class="warn-btn" (click)="view.set('syllabus')">Adjust</button></div>
        }
      } @else {
      <h2 class="group">Order</h2>
      <div class="chips">
        @for (m of orderModes; track m.id) {
          <button matRipple class="chip" [class.on]="mode() === m.id" (click)="setMode(m.id)">
            {{ m.label }}
          </button>
        }
      </div>
      <p class="hint">{{ modeHint() }}</p>

      @if (missed().length > 0) {
        <div class="warn">
          <mat-icon>schedule</mat-icon>
          <span class="warn-text">
            <span class="warn-head">
              {{ missed().length }} chapters land after {{ store.targetDate() | date: 'd MMM' }}
            </span>
            <span class="warn-sub">
              {{ missedHours() }}h and {{ missedMarks() }} marks, at {{ pace() }} a day
            </span>
          </span>
          <button matRipple class="warn-btn" (click)="excludeMissed()">Drop them</button>
        </div>
      }

      <h2 class="group">
        Chapters
        <span class="group-actions">
          <span class="group-aside">{{ inPlayCount() }} in play</span>
          <button matRipple class="small-action" (click)="selectionMode.set(!selectionMode())">
            {{ selectionMode() ? 'Done' : 'Select' }}
          </button>
          <button matRipple class="small-action" (click)="adding.set(true)">Add</button>
        </span>
      </h2>

      @if (selectionMode()) {
        <div class="bulk" aria-live="polite">
          <span>{{ selected().size }} selected</span>
          <button matRipple (click)="bulkPark(false)" [disabled]="selected().size === 0">Include</button>
          <button matRipple (click)="bulkPark(true)" [disabled]="selected().size === 0">Exclude</button>
        </div>
      }

      @if (adding()) {
        <div class="editor">
          <label>Chapter name<input [value]="newName()" (input)="newName.set($any($event.target).value)" /></label>
          <div class="editor-row">
            <label>Class<select [value]="newClass()" (change)="setNewClass($any($event.target).value)"><option value="11">11</option><option value="12">12</option></select></label>
            <label>Estimated hours<input type="number" min="0.5" step="0.5" [value]="newHours()" (input)="newHours.set(+$any($event.target).value)" /></label>
          </div>
          <p class="hint">Hours are your planning estimate, not exam data.</p>
          <div class="editor-actions"><button matRipple class="text-btn" (click)="adding.set(false)">Cancel</button><button matRipple class="filled-btn" [disabled]="!canAdd()" (click)="addChapter()">Add chapter</button></div>
        </div>
      }

      <div class="sheet">
        @for (row of rows(); track row.chapter.id) {
          <div class="row" [class.off]="row.parked" [class.beyond]="row.beyond">
            <button matRipple class="pick" (click)="selectionMode() ? toggleSelected(row.chapter.id) : togglePark(row.chapter)"
                    [attr.aria-label]="(row.parked ? 'Include ' : 'Exclude ') + row.chapter.name">
              <mat-icon [class.filled]="selectionMode() ? selected().has(row.chapter.id) : !row.parked">
                {{ selectionMode() ? (selected().has(row.chapter.id) ? 'check_box' : 'check_box_outline_blank') : (row.parked ? 'check_box_outline_blank' : 'check_box') }}
              </mat-icon>
            </button>

            <span class="row-text">
              @if (editingId() === row.chapter.id) {
                <span class="rename"><input [value]="editingName()" (input)="editingName.set($any($event.target).value)" (keydown.enter)="saveRename(row.chapter)" /><button matRipple class="icon-btn small" (click)="saveRename(row.chapter)" aria-label="Save name"><mat-icon>check</mat-icon></button></span>
              } @else {
                <span class="row-name">{{ row.chapter.name }}</span>
              }
              <span class="row-meta">
                @if (row.parked) {
                  Excluded · {{ row.chapter.hours }}h · {{ marks(row.chapter) }} marks
                } @else if (row.beyond) {
                  Not taught yet
                } @else if (landingFor(row.chapter); as l) {
                  <span [class.late]="!l.fits">
                    {{ l.fits ? 'Reached' : 'Misses the exam,' }} {{ l.date | date: 'd MMM' }}
                  </span>
                  · {{ row.chapter.hours }}h
                } @else {
                  Already done · {{ row.chapter.hours }}h
                }
              </span>
            </span>

            @if (!selectionMode() && editingId() !== row.chapter.id) {
              <button matRipple class="icon-btn small" (click)="startRename(row.chapter)" [attr.aria-label]="'Rename ' + row.chapter.name"><mat-icon>edit</mat-icon></button>
              <button matRipple class="icon-btn small" (click)="toggleTopicEditor(row.chapter.id)" [attr.aria-label]="'Edit topics in ' + row.chapter.name"><mat-icon>format_list_bulleted</mat-icon></button>
            }

            @if (mode() === 'custom') {
              <span class="moves">
                <button matRipple class="icon-btn small" [disabled]="$first"
                        (click)="move(row.chapter, -1)" aria-label="Move up">
                  <mat-icon>arrow_upward</mat-icon>
                </button>
                <button matRipple class="icon-btn small" [disabled]="$last"
                        (click)="move(row.chapter, 1)" aria-label="Move down">
                  <mat-icon>arrow_downward</mat-icon>
                </button>
              </span>
            } @else {
              <button matRipple class="marker" [class.on]="taught() === row.chapter.id"
                      (click)="setTaught(row.chapter)">
                {{ taught() === row.chapter.id ? 'Taught to here' : 'Mark' }}
              </button>
            }
          </div>
          @if (topicEditorId() === row.chapter.id) {
            <div class="topic-editor">
              <div class="topic-head"><span><strong>Topics</strong><small>{{ row.chapter.name }}</small></span>@if (isCustomChapter(row.chapter)) { <button matRipple class="danger-action" (click)="deleteChapter(row.chapter)">Delete chapter</button> }</div>
              @for (topic of row.chapter.subtopics; track topic.id) {
                <div class="topic-row">
                  @if (topicEditingId() === topic.id) {
                    <input [value]="topicEditingName()" (input)="topicEditingName.set($any($event.target).value)" (keydown.enter)="saveTopicRename(topic)" />
                    <button matRipple class="icon-btn small" (click)="saveTopicRename(topic)" aria-label="Save topic name"><mat-icon>check</mat-icon></button>
                  } @else {
                    <span>{{ topic.name }}</span>
                    <button matRipple class="icon-btn small" (click)="startTopicRename(topic)" [attr.aria-label]="'Rename ' + topic.name"><mat-icon>edit</mat-icon></button>
                    <button matRipple class="icon-btn small" (click)="removeTopic(row.chapter, topic)" [attr.aria-label]="'Remove ' + topic.name"><mat-icon>delete</mat-icon></button>
                  }
                </div>
              } @empty {
                <p class="hint">No topics yet. Add the units your class uses.</p>
              }
              <div class="add-topic"><input placeholder="New topic" [value]="newTopicName()" (input)="newTopicName.set($any($event.target).value)" (keydown.enter)="addTopic(row.chapter)" /><button matRipple class="filled-btn compact" [disabled]="!newTopicName().trim()" (click)="addTopic(row.chapter)">Add</button></div>
            </div>
          }
        }
      </div>

      @if (taught()) {
        <button matRipple class="clear" (click)="setTaught(null)">
          Clear the taught marker — plan the whole subject
        </button>
      }
      }
    </div>

    @if (dirty()) {
      <footer class="actions">
        <button matRipple class="text-btn" (click)="discard()">Discard</button>
        <button matRipple class="filled-btn" (click)="preview.set(true)">Preview plan</button>
      </footer>
    }

    @if (preview()) {
      <div class="scrim" (click)="preview.set(false)"></div>
      <div class="sheet-modal" role="dialog" aria-label="Plan preview">
        <span class="handle"></span>
        <h3 class="modal-title">What this changes</h3>

        <div class="deltas">
          @for (d of deltas(); track d.label) {
            <div class="delta">
              <span class="delta-label">{{ d.label }}</span>
              <span class="delta-values">
                <span class="was">{{ d.before }}</span>
                <mat-icon>arrow_forward</mat-icon>
                <span class="now" [class.worse]="d.worse">{{ d.after }}</span>
              </span>
            </div>
          }
        </div>

        @if (deltas().length === 0) {
          <p class="hint">Nothing measurable moves — the order changes, the workload does not.</p>
        }

        <h4 class="modal-label">When it lands, at {{ pace() }} a day</h4>
        <div class="peek">
          @for (m of months(); track m.label) {
            <div class="month" [class.after]="m.after">
              <span class="month-head">
                <span class="month-label">{{ m.label }}</span>
                <span class="month-count">{{ m.count }}</span>
              </span>
              <span class="month-names">{{ m.names }}</span>
            </div>
          } @empty {
            <p class="hint">Nothing left to schedule — everything in play is done.</p>
          }
        </div>

        <div class="modal-actions">
          <button matRipple class="text-btn" (click)="preview.set(false)">Keep editing</button>
          <button matRipple class="filled-btn" (click)="apply()">Apply changes</button>
        </div>
      </div>
    }
  `,
  styles: `
    :host {
      display: flex;
      flex-direction: column;
      height: 100%;
      background: var(--mat-sys-surface);
      color: var(--mat-sys-on-surface);
    }

    .view-tabs { flex: none; display: grid; grid-template-columns: 1fr 1fr; min-height: 48px; border-bottom: 1px solid var(--mat-sys-outline-variant); }
    .view-tabs button { position: relative; border: 0; background: transparent; color: var(--mat-sys-on-surface-variant); font: var(--mat-sys-title-small); cursor: pointer; }
    .view-tabs button.on { color: var(--mat-sys-primary); }
    .view-tabs button.on::after { content: ''; position: absolute; left: 16px; right: 16px; bottom: 0; height: 3px; border-radius: var(--mat-sys-corner-full) var(--mat-sys-corner-full) 0 0; background: var(--mat-sys-primary); }

    .plan-section + .plan-section { margin-top: 24px; }
    .eyebrow { display: block; margin-bottom: 8px; color: var(--mat-sys-on-surface-variant); font: var(--mat-sys-label-large); }
    .plan-title { margin: 0 0 12px; font: var(--mat-sys-title-large); }
    .today-list { display: flex; flex-direction: column; border-radius: var(--mat-sys-corner-large); background: var(--mat-sys-surface-container); overflow: hidden; }
    .today-row { display: flex; align-items: center; gap: 12px; min-height: 64px; padding: 8px 12px; }
    .today-row + .today-row { box-shadow: inset 0 1px 0 var(--mat-sys-outline-variant); }
    .task-icon { width: 40px; height: 40px; display: grid; place-items: center; flex: none; border-radius: var(--mat-sys-corner-full); background: var(--mat-sys-secondary-container); color: var(--mat-sys-on-secondary-container); }
    .today-copy, .day-detail > span, .exam-marker > span, .milestone-copy { display: flex; flex-direction: column; gap: 2px; min-width: 0; }
    .today-copy strong, .day-detail strong, .milestone-copy strong, .exam-marker strong { font: var(--mat-sys-body-large); }
    .today-copy small, .day-detail small, .milestone-copy small, .exam-marker small { color: var(--mat-sys-on-surface-variant); font: var(--mat-sys-body-small); }

    .week-strip { display: grid; grid-template-columns: repeat(7, 1fr); gap: 4px; }
    .week-strip button { min-width: 0; min-height: 72px; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 2px; padding: 4px 0; border: 0; border-radius: var(--mat-sys-corner-large); background: var(--mat-sys-surface-container); color: var(--mat-sys-on-surface); cursor: pointer; }
    .week-strip button.on { background: var(--mat-sys-primary-container); color: var(--mat-sys-on-primary-container); }
    .week-strip span, .week-strip small { font: var(--mat-sys-label-small); }
    .week-strip strong { font: var(--mat-sys-title-medium); }
    .day-detail { display: flex; flex-direction: column; gap: 8px; margin-top: 8px; padding: 12px; border-radius: var(--mat-sys-corner-large); background: var(--mat-sys-surface-container-low); }
    .finishes { font: var(--mat-sys-body-small); color: var(--mat-sys-primary); }
    .muted { color: var(--mat-sys-on-surface-variant); }

    .timeline { display: flex; flex-direction: column; }
    .milestone { position: relative; display: grid; grid-template-columns: 12px 48px 1fr; align-items: start; gap: 8px; min-height: 56px; }
    .milestone::before { content: ''; position: absolute; left: 5px; top: 14px; bottom: -2px; width: 2px; background: var(--mat-sys-outline-variant); }
    .milestone:last-of-type::before { display: none; }
    .rail-dot { z-index: 1; width: 12px; height: 12px; margin-top: 4px; border-radius: var(--mat-sys-corner-full); background: var(--mat-sys-primary); }
    .milestone.late .rail-dot { background: var(--mat-sys-error); }
    .milestone-date { padding-top: 2px; color: var(--mat-sys-on-surface-variant); font: var(--mat-sys-label-medium); }
    .exam-marker { display: flex; align-items: center; gap: 12px; min-height: 56px; padding: 8px 12px; border-radius: var(--mat-sys-corner-large); background: var(--mat-sys-tertiary-container); color: var(--mat-sys-on-tertiary-container); }
    .more-milestones { width: 100%; height: 40px; margin-top: 8px; border: 0; border-radius: var(--mat-sys-corner-full); background: transparent; color: var(--mat-sys-primary); font: var(--mat-sys-label-large); cursor: pointer; }
    .empty { margin: 0; padding: 16px; color: var(--mat-sys-on-surface-variant); font: var(--mat-sys-body-medium); }
    .plan-warn { margin-top: 24px; }

    .bar { flex: none; display: flex; align-items: center; gap: 8px; height: 64px; padding: 0 16px 0 4px; }
    .bar-title { flex: 1; margin: 0; font: var(--mat-sys-title-large); }
    .dirty { font: var(--mat-sys-label-medium); color: var(--mat-sys-primary); }

    .icon-btn {
      display: grid;
      place-items: center;
      width: 48px;
      height: 48px;
      flex: none;
      border: none;
      border-radius: var(--mat-sys-corner-full);
      background: transparent;
      color: var(--mat-sys-on-surface);
      cursor: pointer;
    }

    .icon-btn.small { width: 32px; height: 32px; color: var(--mat-sys-on-surface-variant); }
    .icon-btn:disabled { opacity: .3; cursor: default; }

    .tabs { flex: none; display: flex; gap: 4px; padding: 0 16px 8px; overflow-x: auto; }

    .tab {
      flex: none;
      height: 36px;
      padding: 0 14px;
      border: none;
      border-radius: var(--mat-sys-corner-full);
      background: transparent;
      color: var(--mat-sys-on-surface-variant);
      font: var(--mat-sys-label-large);
      cursor: pointer;
    }

    .tab.on { background: var(--mat-sys-secondary-container); color: var(--mat-sys-on-secondary-container); }

    .scroll { flex: 1; min-height: 0; overflow-y: auto; padding: 8px 16px 24px; }

    .group {
      display: flex;
      align-items: baseline;
      justify-content: space-between;
      margin: 16px 0 8px;
      font: var(--mat-sys-title-small);
      color: var(--mat-sys-on-surface-variant);
    }

    .group:first-child { margin-top: 0; }
    .group-aside { font: var(--mat-sys-label-small); }
    .group-actions { display: flex; align-items: center; gap: 8px; }
    .small-action { border: 0; background: transparent; color: var(--mat-sys-primary); font: var(--mat-sys-label-large); cursor: pointer; }
    .chips { display: flex; flex-wrap: wrap; gap: 8px; }

    .chip {
      height: 36px;
      padding: 0 14px;
      border: 1px solid var(--mat-sys-outline-variant);
      border-radius: var(--mat-sys-corner-full);
      background: transparent;
      color: var(--mat-sys-on-surface-variant);
      font: var(--mat-sys-label-large);
      cursor: pointer;
    }

    .chip.on { border-color: transparent; background: var(--mat-sys-secondary-container); color: var(--mat-sys-on-secondary-container); }
    .hint { margin: 8px 0 0; font: var(--mat-sys-body-small); color: var(--mat-sys-on-surface-variant); }

    .sheet {
      display: flex;
      flex-direction: column;
      border-radius: var(--mat-sys-corner-large);
      background: var(--mat-sys-surface-container);
      overflow: hidden;
    }

    .row { display: flex; align-items: center; gap: 8px; min-height: 64px; padding: 8px 12px; }
    .row + .row { box-shadow: inset 0 1px 0 var(--mat-sys-outline-variant); }
    .row.off .row-name { text-decoration: line-through; }
    .row.off, .row.beyond { color: var(--mat-sys-on-surface-variant); }

    .pick {
      display: grid;
      place-items: center;
      width: 40px;
      height: 40px;
      flex: none;
      border: none;
      border-radius: var(--mat-sys-corner-full);
      background: transparent;
      color: var(--mat-sys-primary);
      cursor: pointer;
    }

    .row.off .pick { color: var(--mat-sys-on-surface-variant); }
    .row-text { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 2px; }
    .row-name { font: var(--mat-sys-body-large); }
    .rename { display: flex; align-items: center; gap: 8px; }
    .rename input { flex: 1; min-width: 0; }
    .row-meta { font: var(--mat-sys-label-small); color: var(--mat-sys-on-surface-variant); }
    .moves { display: flex; flex-direction: column; }

    .marker {
      flex: none;
      height: 32px;
      padding: 0 12px;
      border: 1px solid var(--mat-sys-outline-variant);
      border-radius: var(--mat-sys-corner-full);
      background: transparent;
      color: var(--mat-sys-on-surface-variant);
      font: var(--mat-sys-label-small);
      cursor: pointer;
    }

    .marker.on { border-color: transparent; background: var(--mat-sys-primary); color: var(--mat-sys-on-primary); }

    .clear {
      margin-top: 12px;
      padding: 8px 0;
      border: none;
      background: transparent;
      color: var(--mat-sys-primary);
      font: var(--mat-sys-label-large);
      text-align: left;
      cursor: pointer;
    }

    /* Nothing is committed until this bar is answered. */
    .actions {
      flex: none;
      display: flex;
      align-items: center;
      justify-content: flex-end;
      gap: 8px;
      padding: 12px 16px;
      border-top: 1px solid var(--mat-sys-outline-variant);
      background: var(--mat-sys-surface);
    }

    .text-btn {
      height: 40px;
      padding: 0 16px;
      border: none;
      border-radius: var(--mat-sys-corner-full);
      background: transparent;
      color: var(--mat-sys-primary);
      font: var(--mat-sys-label-large);
      cursor: pointer;
    }

    .filled-btn {
      height: 40px;
      padding: 0 24px;
      border: none;
      border-radius: var(--mat-sys-corner-full);
      background: var(--mat-sys-primary);
      color: var(--mat-sys-on-primary);
      font: var(--mat-sys-label-large);
      cursor: pointer;
    }

    .scrim { position: absolute; inset: 0; z-index: 5; background: color-mix(in srgb, var(--mat-sys-scrim) 40%, transparent); }

    .sheet-modal {
      position: absolute;
      left: 0;
      right: 0;
      bottom: 0;
      z-index: 6;
      max-height: 84%;
      display: flex;
      flex-direction: column;
      padding: 8px 16px 24px;
      border-radius: 28px 28px 0 0;
      background: var(--mat-sys-surface-container-low);
    }

    .handle { width: 32px; height: 4px; margin: 0 auto 12px; border-radius: 2px; background: var(--mat-sys-outline-variant); }
    .modal-title { margin: 0 0 4px; font: var(--mat-sys-title-large); }
    .modal-label { margin: 20px 0 8px; font: var(--mat-sys-title-small); color: var(--mat-sys-on-surface-variant); }

    .deltas { display: flex; flex-direction: column; }
    .delta { display: flex; align-items: center; justify-content: space-between; gap: 12px; min-height: 48px; }
    .delta + .delta { box-shadow: inset 0 1px 0 var(--mat-sys-outline-variant); }
    .delta-label { font: var(--mat-sys-body-medium); }
    .delta-values { display: flex; align-items: center; gap: 8px; font: var(--mat-sys-body-medium); }
    .was { color: var(--mat-sys-on-surface-variant); text-decoration: line-through; }
    .now { color: var(--mat-sys-primary); }
    .now.worse { color: var(--mat-sys-error); }
    .delta-values mat-icon { font-size: 16px; width: 16px; height: 16px; color: var(--mat-sys-on-surface-variant); }

    .peek { display: flex; flex-direction: column; overflow-y: auto; }

    .month { display: flex; flex-direction: column; gap: 2px; padding: 10px 0; }
    .month + .month { box-shadow: inset 0 1px 0 var(--mat-sys-outline-variant); }
    .month-head { display: flex; align-items: baseline; justify-content: space-between; gap: 12px; }
    .month-label { font: var(--mat-sys-body-large); }
    .month-count { font: var(--mat-sys-label-large); color: var(--mat-sys-on-surface-variant); }

    .month-names {
      font: var(--mat-sys-label-small);
      color: var(--mat-sys-on-surface-variant);
      overflow: hidden;
      white-space: nowrap;
      text-overflow: ellipsis;
    }

    /* Past the exam date — the months that will not happen. */
    .month.after .month-label { color: var(--mat-sys-error); }
    .month.after .month-label::after { content: ' · after the exam'; font: var(--mat-sys-label-small); }

    .late { color: var(--mat-sys-error); }

    .warn {
      display: flex;
      align-items: center;
      gap: 12px;
      margin: 16px 0 0;
      padding: 12px 16px;
      border-radius: var(--mat-sys-corner-large);
      background: var(--mat-sys-error-container);
      color: var(--mat-sys-on-error-container);
    }

    .warn-text { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 2px; }
    .warn-head { font: var(--mat-sys-title-small); }
    .warn-sub { font: var(--mat-sys-body-small); opacity: .85; }

    .warn-btn {
      flex: none;
      height: 32px;
      padding: 0 14px;
      border: none;
      border-radius: var(--mat-sys-corner-full);
      background: var(--mat-sys-on-error-container);
      color: var(--mat-sys-error-container);
      font: var(--mat-sys-label-large);
      cursor: pointer;
    }
    .modal-actions { display: flex; justify-content: flex-end; gap: 8px; margin-top: 20px; }
    .bulk { position: sticky; top: 0; z-index: 2; display: flex; align-items: center; gap: 8px; min-height: 48px; margin-bottom: 8px; padding: 0 12px; border-radius: var(--mat-sys-corner-large); background: var(--mat-sys-secondary-container); color: var(--mat-sys-on-secondary-container); font: var(--mat-sys-label-large); }
    .bulk span { flex: 1; }
    .bulk button { border: 0; background: transparent; color: inherit; font: var(--mat-sys-label-large); cursor: pointer; }
    .bulk button:disabled { opacity: .38; }
    .editor { display: flex; flex-direction: column; gap: 8px; margin-bottom: 16px; padding: 16px; border-radius: var(--mat-sys-corner-large); background: var(--mat-sys-surface-container); }
    .editor label { display: flex; flex-direction: column; gap: 8px; font: var(--mat-sys-label-medium); color: var(--mat-sys-on-surface-variant); }
    .editor input, .editor select, .rename input { height: 40px; padding: 0 12px; border: 1px solid var(--mat-sys-outline); border-radius: var(--mat-sys-corner-small); background: var(--mat-sys-surface); color: var(--mat-sys-on-surface); font: var(--mat-sys-body-large); }
    .editor-row { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
    .editor-actions { display: flex; justify-content: flex-end; gap: 8px; }
    .topic-editor { display: flex; flex-direction: column; gap: 8px; padding: 12px 16px 16px; background: var(--mat-sys-surface-container-low); box-shadow: inset 0 1px 0 var(--mat-sys-outline-variant); }
    .topic-head { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
    .topic-head > span { display: flex; flex-direction: column; gap: 2px; }
    .topic-head strong { font: var(--mat-sys-title-small); }
    .topic-head small { color: var(--mat-sys-on-surface-variant); font: var(--mat-sys-body-small); }
    .topic-row { display: flex; align-items: center; gap: 8px; min-height: 48px; padding-left: 8px; }
    .topic-row + .topic-row { box-shadow: inset 0 1px 0 var(--mat-sys-outline-variant); }
    .topic-row > span, .topic-row > input { flex: 1; min-width: 0; }
    .topic-row > span { font: var(--mat-sys-body-medium); }
    .topic-row input, .add-topic input { height: 40px; padding: 0 12px; border: 1px solid var(--mat-sys-outline); border-radius: var(--mat-sys-corner-small); background: var(--mat-sys-surface); color: var(--mat-sys-on-surface); font: var(--mat-sys-body-large); }
    .add-topic { display: flex; gap: 8px; padding-top: 8px; }
    .add-topic input { flex: 1; min-width: 0; }
    .filled-btn.compact { padding: 0 16px; }
    .danger-action { height: 40px; padding: 0 12px; border: 0; border-radius: var(--mat-sys-corner-full); background: transparent; color: var(--mat-sys-error); font: var(--mat-sys-label-large); cursor: pointer; }
  `,
})
export class OrganiseScreen {
  readonly close = output<void>();

  protected readonly store = inject(OnboardingStore);
  private readonly study = inject(StudyStore);
  private readonly planner = inject(DayPlanner);

  protected readonly subjects = computed(() => this.draftSubjects());
  protected readonly orderModes = ORDER_MODES;
  protected readonly current = signal(PACK.subjects[0].id);
  protected readonly preview = signal(false);
  protected readonly view = signal<'plan' | 'syllabus'>('plan');
  protected readonly selectedDay = signal(0);
  protected readonly milestoneLimit = signal(12);
  protected readonly selectionMode = signal(false);
  protected readonly selected = signal<ReadonlySet<string>>(new Set());
  protected readonly adding = signal(false);
  protected readonly newName = signal('');
  protected readonly newClass = signal<11 | 12>(11);
  protected readonly newHours = signal(1);
  protected readonly editingId = signal<string | null>(null);
  protected readonly editingName = signal('');
  protected readonly topicEditorId = signal<string | null>(null);
  protected readonly newTopicName = signal('');
  protected readonly topicEditingId = signal<string | null>(null);
  protected readonly topicEditingName = signal('');

  /** Held apart from the store until Apply. */
  protected readonly draft = signal<Draft>(this.snapshot());

  private snapshot(): Draft {
    return {
      parked: new Set(this.store.parkedChapters()),
      orderModes: new Map(this.store.orderModes()),
      customOrder: new Map(this.store.customOrder()),
      taughtUpTo: new Map(this.store.taughtUpTo()),
      customChapters: this.store.customChapters().map((chapter) => ({ ...chapter })),
      chapterNames: new Map(this.store.chapterNames()),
      customSubtopics: new Map([...this.store.customSubtopics()].map(([id, topics]) => [id, topics.map((topic) => ({ ...topic }))])),
      subtopicNames: new Map(this.store.subtopicNames()),
      hiddenSubtopics: new Set(this.store.hiddenSubtopics()),
    };
  }

  private get subject() {
    return this.draftSubjects().find((s) => s.id === this.current())!;
  }

  private draftSubjects() { const d = this.draft(); return mergedSubjects(d.customChapters, d.chapterNames, d.customSubtopics, d.subtopicNames, d.hiddenSubtopics); }

  protected readonly todayBlocks = computed(() => this.planner.blocksFor(startOfToday()).filter((block) => block.kind === 'study'));
  protected readonly todayMinutes = computed(() => this.todayBlocks().reduce((sum, block) => sum + block.minutes, 0));
  protected blockKey(block: { chapterId: string; task: string; subtopicId?: string }): string { return `${block.chapterId}|${block.task}|${block.subtopicId ?? ''}`; }
  protected taskIcon(task: string): string { return task === 'Revise' ? 'history' : task === 'Practice' ? 'quiz' : 'menu_book'; }
  protected minuteLabel(minute: number): string { const h = Math.floor(minute / 60); const m = minute % 60; return `${h % 12 || 12}:${String(m).padStart(2, '0')} ${h < 12 ? 'AM' : 'PM'}`; }

  protected readonly week = computed(() => Array.from({ length: 7 }, (_, index) => {
    const date = addDays(startOfToday(), index);
    const revision = this.study.revisionMinutesOn(date);
    const minutes = this.planner.capacityOn(date, revision);
    const finishes = this.landings().filter((landing) => dateKey(landing.date) === dateKey(date)).map((landing) => landing.chapter.name);
    return {
      key: dateKey(date), date, revision: Math.round((revision / 60) * 10) / 10,
      hours: Math.round((minutes / 60) * 10) / 10,
      weekday: date.toLocaleDateString(undefined, { weekday: 'narrow' }),
      day: date.getDate(), label: date.toLocaleDateString(undefined, { weekday: 'long', day: 'numeric', month: 'short' }), finishes,
    };
  }));
  protected readonly visibleMilestones = computed(() => this.landings().slice(0, this.milestoneLimit()));

  protected mode(): OrderMode {
    return this.draft().orderModes.get(this.current()) ?? 'book';
  }

  protected modeHint(): string {
    return ORDER_MODES.find((m) => m.id === this.mode())!.hint;
  }

  protected taught(): string | null {
    return this.draft().taughtUpTo.get(this.current()) ?? null;
  }

  /** The subject in its chosen order, flagged for excluded and untaught. */
  protected readonly rows = computed(() => {
    const d = this.draft();
    const subject = this.subject;
    const ordered = orderedChapters(subject, this.mode(), d.customOrder.get(subject.id));
    const marker = d.taughtUpTo.get(subject.id) ?? null;
    const cut = marker ? ordered.findIndex((c) => c.id === marker) : -1;

    return ordered.map((chapter, i) => ({
      chapter,
      parked: d.parked.has(chapter.id),
      beyond: cut !== -1 && i > cut,
    }));
  });

  protected inPlayCount(): number {
    return this.rows().filter((r) => !r.parked && !r.beyond).length;
  }

  protected marks(chapter: Chapter): number { return marksOf(chapter); }

  /* ---- Where things land ---------------------------------------------- */

  /** The pace actually kept, falling back to what the plan asks for. */
  private hoursPerDay(): number {
    const logged = this.study.averageMinutes(14) / 60;
    if (logged >= 0.25) return logged;
    return (this.store.weekdayHours() * 5 + this.store.weekendHours() * 2) / 7;
  }

  protected pace(): string { return `${this.hoursPerDay().toFixed(1)}h`; }

  protected readonly landings = computed<Landing[]>(() => {
    const d = this.draft();
    return project({
      orderModes: d.orderModes,
      customOrder: d.customOrder,
      taughtUpTo: d.taughtUpTo,
      parked: d.parked,
      doneUnits: this.store.doneUnits(),
      hoursPerDay: this.hoursPerDay(),
      examDate: this.store.targetDate(),
      subjects: this.draftSubjects(),
      capacityOn: (date, revision) => this.planner.capacityOn(date, revision),
      revisionMinutesOn: (date) => this.study.revisionMinutesOn(date),
    });
  });

  private readonly landingBy = computed(
    () => new Map(this.landings().map((l) => [l.chapter.id, l])),
  );

  protected landingFor(chapter: Chapter): Landing | undefined {
    return this.landingBy().get(chapter.id);
  }

  /** Chapters the plan only reaches after the exam has been sat. */
  protected readonly missed = computed(() => overflow(this.landings()));

  protected readonly missedHours = computed(() =>
    Math.round(this.missed().reduce((n, l) => n + l.chapter.hours, 0)),
  );

  protected readonly missedMarks = computed(() =>
    this.missed().reduce((n, l) => n + marksOf(l.chapter), 0),
  );

  /** Drop everything that cannot be reached in time, in one move. */
  protected excludeMissed(): void {
    const ids = this.missed().map((l) => l.chapter.id);
    this.edit((d) => ids.forEach((id) => d.parked.add(id)));
  }

  /** The projection grouped by month, so the shape of the year is visible. */
  protected readonly months = computed(() => {
    const groups = new Map<string, { label: string; count: number; names: string[]; after: boolean }>();

    for (const landing of this.landings()) {
      const key = `${landing.date.getFullYear()}-${landing.date.getMonth()}`;
      const label = landing.date.toLocaleDateString(undefined, { month: 'long', year: 'numeric' });
      const group = groups.get(key) ?? { label, count: 0, names: [], after: false };
      group.count++;
      if (group.names.length < 3) group.names.push(landing.chapter.name);
      group.after = group.after || !landing.fits;
      groups.set(key, group);
    }

    return [...groups.values()].map((g) => ({
      label: g.label,
      count: g.count,
      after: g.after,
      names: g.names.join(', ') + (g.count > g.names.length ? ` +${g.count - g.names.length} more` : ''),
    }));
  });

  protected subjectName(chapter: Chapter): string {
    const id = chapter.id.split('.')[0];
    return this.draftSubjects().find((s) => s.id === id)?.name ?? '';
  }

  /* ---- Editing the draft ---------------------------------------------- */

  private edit(change: (d: Draft) => void): void {
    const next: Draft = {
      parked: new Set(this.draft().parked),
      orderModes: new Map(this.draft().orderModes),
      customOrder: new Map(this.draft().customOrder),
      taughtUpTo: new Map(this.draft().taughtUpTo),
      customChapters: this.draft().customChapters.map((chapter) => ({ ...chapter })),
      chapterNames: new Map(this.draft().chapterNames),
      customSubtopics: new Map([...this.draft().customSubtopics].map(([id, topics]) => [id, topics.map((topic) => ({ ...topic }))])),
      subtopicNames: new Map(this.draft().subtopicNames),
      hiddenSubtopics: new Set(this.draft().hiddenSubtopics),
    };
    change(next);
    this.draft.set(next);
  }

  protected setMode(mode: OrderMode): void {
    this.edit((d) => {
      d.orderModes.set(this.current(), mode);
      // Switching to custom starts from whatever is on screen right now.
      if (mode === 'custom' && !d.customOrder.has(this.current())) {
        d.customOrder.set(this.current(), this.rows().map((r) => r.chapter.id));
      }
    });
  }

  protected togglePark(chapter: Chapter): void {
    this.edit((d) => {
      d.parked.has(chapter.id) ? d.parked.delete(chapter.id) : d.parked.add(chapter.id);
    });
  }

  protected toggleSelected(id: string): void { const next = new Set(this.selected()); next.has(id) ? next.delete(id) : next.add(id); this.selected.set(next); }
  protected bulkPark(parked: boolean): void { const ids = this.selected(); this.edit((d) => ids.forEach((id) => parked ? d.parked.add(id) : d.parked.delete(id))); this.selected.set(new Set()); }
  protected canAdd(): boolean { return this.newName().trim().length > 0 && this.newHours() >= .5; }
  protected setNewClass(value: string): void { this.newClass.set(value === '12' ? 12 : 11); }
  protected addChapter(): void { if (!this.canAdd()) return; const id = `${this.current()}.custom.${crypto.randomUUID()}`; this.edit((d) => { d.customChapters.push({ id, subjectId: this.current(), name: this.newName().trim(), cls: this.newClass(), hours: this.newHours(), subtopics: [], custom: true }); const order = d.customOrder.get(this.current()); if (order) d.customOrder.set(this.current(), [...order, id]); }); this.newName.set(''); this.newHours.set(1); this.adding.set(false); }
  protected startRename(chapter: Chapter): void { this.editingId.set(chapter.id); this.editingName.set(chapter.name); }
  protected saveRename(chapter: Chapter): void { const name = this.editingName().trim(); if (name) this.edit((d) => d.chapterNames.set(chapter.id, name)); this.editingId.set(null); }
  protected toggleTopicEditor(id: string): void { this.topicEditorId.set(this.topicEditorId() === id ? null : id); this.newTopicName.set(''); }
  protected isCustomChapter(chapter: Chapter): boolean { return this.draft().customChapters.some((item) => item.id === chapter.id); }
  protected addTopic(chapter: Chapter): void { const name = this.newTopicName().trim(); if (!name) return; this.edit((d) => { const topics = [...(d.customSubtopics.get(chapter.id) ?? [])]; topics.push({ id: `${chapter.id}.custom.${crypto.randomUUID()}`, name, custom: true }); d.customSubtopics.set(chapter.id, topics); }); this.newTopicName.set(''); }
  protected startTopicRename(topic: Subtopic): void { this.topicEditingId.set(topic.id); this.topicEditingName.set(topic.name); }
  protected saveTopicRename(topic: Subtopic): void { const name = this.topicEditingName().trim(); if (name) this.edit((d) => d.subtopicNames.set(topic.id, name)); this.topicEditingId.set(null); }
  protected removeTopic(chapter: Chapter, topic: Subtopic): void { this.edit((d) => { const custom = d.customSubtopics.get(chapter.id) ?? []; if (custom.some((item) => item.id === topic.id)) d.customSubtopics.set(chapter.id, custom.filter((item) => item.id !== topic.id)); else d.hiddenSubtopics.add(topic.id); }); }
  protected deleteChapter(chapter: Chapter): void { this.edit((d) => { d.customChapters = d.customChapters.filter((item) => item.id !== chapter.id); d.parked.delete(chapter.id); d.customSubtopics.delete(chapter.id); for (const [subjectId, order] of d.customOrder) d.customOrder.set(subjectId, order.filter((id) => id !== chapter.id)); }); this.topicEditorId.set(null); }

  protected setTaught(chapter: Chapter | null): void {
    this.edit((d) => {
      const same = chapter && d.taughtUpTo.get(this.current()) === chapter.id;
      if (!chapter || same) d.taughtUpTo.delete(this.current());
      else d.taughtUpTo.set(this.current(), chapter.id);
    });
  }

  protected move(chapter: Chapter, by: number): void {
    this.edit((d) => {
      const ids = [...(d.customOrder.get(this.current()) ?? this.rows().map((r) => r.chapter.id))];
      const from = ids.indexOf(chapter.id);
      const to = from + by;
      if (from === -1 || to < 0 || to >= ids.length) return;
      ids.splice(to, 0, ids.splice(from, 1)[0]);
      d.customOrder.set(this.current(), ids);
    });
  }

  /* ---- What the draft would do ---------------------------------------- */

  protected readonly changeCount = computed(() => {
    const d = this.draft();
    const now = this.snapshotOfStore();
    let n = 0;
    for (const id of new Set([...d.parked, ...now.parked])) {
      if (d.parked.has(id) !== now.parked.has(id)) n++;
    }
    for (const s of this.draftSubjects()) {
      if ((d.orderModes.get(s.id) ?? 'book') !== (now.orderModes.get(s.id) ?? 'book')) n++;
      if ((d.taughtUpTo.get(s.id) ?? null) !== (now.taughtUpTo.get(s.id) ?? null)) n++;
      const a = (d.customOrder.get(s.id) ?? []).join();
      const b = (now.customOrder.get(s.id) ?? []).join();
      if (a !== b) n++;
    }
    if (JSON.stringify(d.customChapters) !== JSON.stringify(now.customChapters)) n++;
    if (JSON.stringify([...d.chapterNames]) !== JSON.stringify([...now.chapterNames])) n++;
    if (JSON.stringify([...d.customSubtopics]) !== JSON.stringify([...now.customSubtopics])) n++;
    if (JSON.stringify([...d.subtopicNames]) !== JSON.stringify([...now.subtopicNames])) n++;
    if ([...new Set([...d.hiddenSubtopics, ...now.hiddenSubtopics])].some((id) => d.hiddenSubtopics.has(id) !== now.hiddenSubtopics.has(id))) n++;
    return n;
  });

  protected readonly dirty = computed(() => this.changeCount() > 0);

  private snapshotOfStore(): Draft {
    return {
      parked: new Set(this.store.parkedChapters()),
      orderModes: new Map(this.store.orderModes()),
      customOrder: new Map(this.store.customOrder()),
      taughtUpTo: new Map(this.store.taughtUpTo()),
      customChapters: this.store.customChapters().map((chapter) => ({ ...chapter })),
      chapterNames: new Map(this.store.chapterNames()),
      customSubtopics: new Map([...this.store.customSubtopics()].map(([id, topics]) => [id, topics.map((topic) => ({ ...topic }))])),
      subtopicNames: new Map(this.store.subtopicNames()),
      hiddenSubtopics: new Set(this.store.hiddenSubtopics()),
    };
  }

  /** Before and after, in the numbers the student is actually trading. */
  protected readonly deltas = computed(() => {
    const before = this.measure(this.snapshotOfStore());
    const after = this.measure(this.draft());
    const perDay = Math.max(0.25, this.study.averageMinutes(14) / 60);

    const finish = (hours: number) => addDays(startOfToday(), Math.ceil(hours / perDay));
    const fmt = (d: Date) => d.toLocaleDateString(undefined, { day: 'numeric', month: 'short', year: 'numeric' });

    const rows = [
      {
        label: 'Chapters in play',
        before: `${before.count}`,
        after: `${after.count}`,
        worse: after.count > before.count,
      },
      {
        label: 'Hours of syllabus left',
        before: `${Math.round(before.hours)}h`,
        after: `${Math.round(after.hours)}h`,
        worse: after.hours > before.hours,
      },
      {
        label: 'Marks in play',
        before: `${before.marks}`,
        after: `${after.marks}`,
        worse: after.marks < before.marks,
      },
      {
        label: 'Syllabus done by',
        before: fmt(finish(before.hours)),
        after: fmt(finish(after.hours)),
        worse: finish(after.hours) > this.store.targetDate(),
      },
    ];

    // A row that reads "218h → 218h" is noise. Only show what moved.
    return rows.filter((r) => r.before !== r.after);
  });

  private measure(d: Draft) {
    let count = 0;
    let hours = 0;
    let marks = 0;
    const done = this.store.doneUnits();

    for (const subject of mergedSubjects(d.customChapters, d.chapterNames, d.customSubtopics, d.subtopicNames, d.hiddenSubtopics)) {
      const available = availableChapters(
        subject,
        d.orderModes.get(subject.id) ?? 'book',
        d.customOrder.get(subject.id),
        d.taughtUpTo.get(subject.id) ?? null,
      );
      for (const chapter of available) {
        if (d.parked.has(chapter.id)) continue;
        count++;
        marks += marksOf(chapter);
        if (!chapterIsDone(chapter, done)) hours += chapter.hours;
      }
    }
    return { count, hours, marks };
  }

  /** The first few chapters the plan would reach for under the draft. */
  protected readonly peek = computed(() => {
    const d = this.draft();
    const done = this.store.doneUnits();
    const out: Chapter[] = [];

    for (const subject of mergedSubjects(d.customChapters, d.chapterNames, d.customSubtopics, d.subtopicNames, d.hiddenSubtopics)) {
      const available = availableChapters(
        subject,
        d.orderModes.get(subject.id) ?? 'book',
        d.customOrder.get(subject.id),
        d.taughtUpTo.get(subject.id) ?? null,
      );
      const next = available.find((c) => !d.parked.has(c.id) && !chapterIsDone(c, done));
      if (next) out.push(next);
    }
    return out.slice(0, 4);
  });

  /* ---- Commit ---------------------------------------------------------- */

  protected apply(): void {
    const d = this.draft();
    this.store.parkedChapters.set(new Set(d.parked));
    this.store.orderModes.set(new Map(d.orderModes));
    this.store.customOrder.set(new Map(d.customOrder));
    this.store.taughtUpTo.set(
      new Map([...d.taughtUpTo].filter((e): e is [string, string] => e[1] !== null)),
    );
    this.store.customChapters.set(d.customChapters.map((chapter) => ({ ...chapter })));
    this.store.chapterNames.set(new Map(d.chapterNames));
    this.store.customSubtopics.set(new Map([...d.customSubtopics].map(([id, topics]) => [id, topics.map((topic) => ({ ...topic }))])));
    this.store.subtopicNames.set(new Map(d.subtopicNames));
    this.store.hiddenSubtopics.set(new Set(d.hiddenSubtopics));
    this.preview.set(false);
    this.close.emit();
  }

  protected discard(): void {
    this.draft.set(this.snapshot());
    this.preview.set(false);
  }
}
