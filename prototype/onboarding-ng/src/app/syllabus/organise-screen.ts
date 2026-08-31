import { ChangeDetectionStrategy, Component, computed, inject, output, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';
import { MatRippleModule } from '@angular/material/core';
import { ORDER_MODES, OrderMode, OnboardingStore, addDays, startOfToday } from '../onboarding/state';
import { ALL_CHAPTERS, Chapter, PACK, chapterIsDone } from '../onboarding/exam-pack';
import { availableChapters, marksOf, orderedChapters } from '../onboarding/sequence';
import { StudyStore } from '../study/study-store';

/** Everything the student is changing, held apart until they approve it. */
interface Draft {
  parked: Set<string>;
  orderModes: Map<string, OrderMode>;
  customOrder: Map<string, string[]>;
  taughtUpTo: Map<string, string | null>;
}

/**
 * Organise: pick the order, say how far the class has reached, and drop what
 * is not being attempted — then see what it does to the plan before it takes
 * effect. Edits are held in a draft, because changing the syllabus silently
 * reshapes tomorrow and the student should see that first.
 */
@Component({
  selector: 'app-organise-screen',
  imports: [MatIconModule, MatRippleModule, DatePipe],
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

    <div class="tabs">
      @for (s of subjects; track s.id) {
        <button matRipple class="tab" [class.on]="current() === s.id" (click)="current.set(s.id)">
          {{ s.name }}
        </button>
      }
    </div>

    <div class="scroll">
      <h2 class="group">Order</h2>
      <div class="chips">
        @for (m of orderModes; track m.id) {
          <button matRipple class="chip" [class.on]="mode() === m.id" (click)="setMode(m.id)">
            {{ m.label }}
          </button>
        }
      </div>
      <p class="hint">{{ modeHint() }}</p>

      <h2 class="group">
        Chapters
        <span class="group-aside">{{ inPlayCount() }} in play</span>
      </h2>

      <div class="sheet">
        @for (row of rows(); track row.chapter.id) {
          <div class="row" [class.off]="row.parked" [class.beyond]="row.beyond">
            <button matRipple class="pick" (click)="togglePark(row.chapter)"
                    [attr.aria-label]="(row.parked ? 'Include ' : 'Exclude ') + row.chapter.name">
              <mat-icon [class.filled]="!row.parked">
                {{ row.parked ? 'check_box_outline_blank' : 'check_box' }}
              </mat-icon>
            </button>

            <span class="row-text">
              <span class="row-name">{{ row.chapter.name }}</span>
              <span class="row-meta">
                Class {{ row.chapter.cls }} · {{ row.chapter.hours }}h · {{ marks(row.chapter) }} marks
                @if (row.beyond) { · not taught yet }
              </span>
            </span>

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
        }
      </div>

      @if (taught()) {
        <button matRipple class="clear" (click)="setTaught(null)">
          Clear the taught marker — plan the whole subject
        </button>
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

        <h4 class="modal-label">Tomorrow would start with</h4>
        <div class="peek">
          @for (c of peek(); track c.id) {
            <div class="peek-row">
              <span class="peek-name">{{ c.name }}</span>
              <span class="peek-meta">{{ subjectName(c) }}</span>
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

    .scrim { position: absolute; inset: 0; z-index: 5; background: rgb(0 0 0 / .4); }

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
    .peek-row { display: flex; align-items: baseline; justify-content: space-between; gap: 12px; min-height: 44px; }
    .peek-name { font: var(--mat-sys-body-medium); }
    .peek-meta { font: var(--mat-sys-label-small); color: var(--mat-sys-on-surface-variant); }
    .modal-actions { display: flex; justify-content: flex-end; gap: 8px; margin-top: 20px; }
  `,
})
export class OrganiseScreen {
  readonly close = output<void>();

  private readonly store = inject(OnboardingStore);
  private readonly study = inject(StudyStore);

  protected readonly subjects = PACK.subjects;
  protected readonly orderModes = ORDER_MODES;
  protected readonly current = signal(PACK.subjects[0].id);
  protected readonly preview = signal(false);

  /** Held apart from the store until Apply. */
  protected readonly draft = signal<Draft>(this.snapshot());

  private snapshot(): Draft {
    return {
      parked: new Set(this.store.parkedChapters()),
      orderModes: new Map(this.store.orderModes()),
      customOrder: new Map(this.store.customOrder()),
      taughtUpTo: new Map(this.store.taughtUpTo()),
    };
  }

  private get subject() {
    return PACK.subjects.find((s) => s.id === this.current())!;
  }

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

  protected subjectName(chapter: Chapter): string {
    const id = chapter.id.split('.')[0];
    return PACK.subjects.find((s) => s.id === id)?.name ?? '';
  }

  /* ---- Editing the draft ---------------------------------------------- */

  private edit(change: (d: Draft) => void): void {
    const next: Draft = {
      parked: new Set(this.draft().parked),
      orderModes: new Map(this.draft().orderModes),
      customOrder: new Map(this.draft().customOrder),
      taughtUpTo: new Map(this.draft().taughtUpTo),
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
    for (const s of PACK.subjects) {
      if ((d.orderModes.get(s.id) ?? 'book') !== (now.orderModes.get(s.id) ?? 'book')) n++;
      if ((d.taughtUpTo.get(s.id) ?? null) !== (now.taughtUpTo.get(s.id) ?? null)) n++;
      const a = (d.customOrder.get(s.id) ?? []).join();
      const b = (now.customOrder.get(s.id) ?? []).join();
      if (a !== b) n++;
    }
    return n;
  });

  protected readonly dirty = computed(() => this.changeCount() > 0);

  private snapshotOfStore(): Draft {
    return {
      parked: new Set(this.store.parkedChapters()),
      orderModes: new Map(this.store.orderModes()),
      customOrder: new Map(this.store.customOrder()),
      taughtUpTo: new Map(this.store.taughtUpTo()),
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

    for (const subject of PACK.subjects) {
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

    for (const subject of PACK.subjects) {
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
    this.preview.set(false);
    this.close.emit();
  }

  protected discard(): void {
    this.draft.set(this.snapshot());
    this.preview.set(false);
  }
}
