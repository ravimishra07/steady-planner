import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { MatTabsModule } from '@angular/material/tabs';
import { MatRippleModule } from '@angular/material/core';
import { OnboardingStore } from '../onboarding/state';
import { PACK, Subject, Section, Chapter, chapterIsDone } from '../onboarding/exam-pack';

/**
 * Syllabus browser, following SyllabusScreen.kt: a subject chip row, a
 * filter/stats bar, then a card per subject whose details open into a tree of
 * rows that each carry progress, a tick and a start control.
 */
@Component({
  selector: 'app-syllabus-browser',
  imports: [MatIconModule, MatRippleModule, MatTabsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <mat-tab-group
      class="subjects"
      [selectedIndex]="index()"
      (selectedIndexChange)="pick($event)"
      animationDuration="120ms">
      @for (s of subjects; track s.id) {
        <mat-tab><ng-template mat-tab-label>{{ s.name }}</ng-template></mat-tab>
      }
    </mat-tab-group>

    <div class="filters">
      <div class="chips">
        @for (f of filterChips; track f.id) {
          <button
            matRipple
            class="chip"
            [class.on]="isFilterOn(f.id)"
            (click)="toggleFilter(f.id)">
            @if (isFilterOn(f.id)) { <mat-icon>check</mat-icon> }
            {{ f.label }}@if (f.count !== null) { <span class="count">{{ f.count() }}</span> }
          </button>
        }
      </div>

      <button
        matRipple
        class="more"
        [class.badged]="hiddenFilterOn()"
        aria-label="More filters"
        (click)="sheetOpen.set(true)">
        <mat-icon>tune</mat-icon>
      </button>
    </div>

    @for (s of visible(); track s.id) {
      <section class="subject" [id]="'subject-' + s.id">
        <header class="head">
          <span class="ring" [style.background]="ringFill(percent(s))">
            <span class="ring-value">{{ percent(s) }}%</span>
          </span>
          <span class="titles">
            <span class="name">{{ s.name }}</span>
            <span class="meta">{{ subjectMeta(s) }}</span>
          </span>
          <button matRipple class="play filled" [attr.aria-label]="'Start ' + s.name">
            <mat-icon>play_arrow</mat-icon>
          </button>
        </header>

        <button matRipple class="details" (click)="toggleCard(s.id)">
          {{ isCardOpen(s.id) ? 'Hide details' : 'See details' }}
          <mat-icon>{{ isCardOpen(s.id) ? 'keyboard_arrow_up' : 'keyboard_arrow_down' }}</mat-icon>
        </button>

        @if (isCardOpen(s.id)) {
          <div class="tree">
            @for (section of sections(s); track section.name) {
              <div class="node depth0">
                <button matRipple class="node-main" (click)="toggleSection(s.id + section.name)">
                  <mat-icon class="chev">
                    {{ isSectionOpen(s.id + section.name) ? 'keyboard_arrow_up' : 'keyboard_arrow_down' }}
                  </mat-icon>
                  <span class="node-body">
                    <span class="node-name">{{ section.name }}</span>
                    <span class="bar">
                      <span class="track"><i [style.width.%]="sectionPercent(section)"></i></span>
                      <span class="pct">{{ sectionPercent(section) }}%</span>
                    </span>
                  </span>
                </button>
                <button
                  matRipple
                  class="tick"
                  [class.on]="sectionDone(section)"
                  [attr.aria-label]="'Mark ' + section.name + ' done'"
                  (click)="toggleSectionDone(section)">
                  <mat-icon>check</mat-icon>
                </button>
              </div>

              @if (isSectionOpen(s.id + section.name)) {
                <div class="branch">
                @for (chapter of chapters(section); track chapter.id) {
                  <div class="node depth1">
                    <button
                      matRipple
                      class="node-main"
                      [disabled]="chapter.subtopics.length === 0"
                      (click)="toggleChapter(chapter.id)">
                      @if (chapter.subtopics.length > 0) {
                        <mat-icon class="chev">
                          {{ isChapterOpen(chapter.id) ? 'keyboard_arrow_up' : 'keyboard_arrow_down' }}
                        </mat-icon>
                      }
                      <span class="node-body">
                        <span class="node-name" [class.muted]="isDone(chapter)">{{ chapter.name }}</span>
                        <span class="bar">
                          <span class="track"><i [style.width.%]="chapterPercent(chapter)"></i></span>
                          <span class="pct">{{ formatHours(chapter.hours) }}</span>
                        </span>
                      </span>
                    </button>
                    <button
                      matRipple
                      class="tick"
                      [class.on]="isDone(chapter)"
                      [attr.aria-label]="'Mark ' + chapter.name + ' done'"
                      (click)="toggleChapterDone(chapter)">
                      <mat-icon>check</mat-icon>
                    </button>
                    @if (chapter.subtopics.length === 0) {
                      <button matRipple class="play" [attr.aria-label]="'Start ' + chapter.name">
                        <mat-icon>play_arrow</mat-icon>
                      </button>
                    }
                  </div>

                  @if (isChapterOpen(chapter.id)) {
                    <div class="branch">
                    @for (topic of chapter.subtopics; track topic.id) {
                      <div class="node depth2">
                        <span class="node-body">
                          <span class="node-name" [class.muted]="store.doneUnits().has(topic.id)">
                            {{ topic.name }}
                          </span>
                        </span>
                        <button
                          matRipple
                          class="tick"
                          [class.on]="store.doneUnits().has(topic.id)"
                          [attr.aria-label]="'Mark ' + topic.name + ' done'"
                          (click)="store.toggleUnit(topic.id)">
                          <mat-icon>check</mat-icon>
                        </button>
                        <button matRipple class="play" [attr.aria-label]="'Start ' + topic.name">
                          <mat-icon>play_arrow</mat-icon>
                        </button>
                      </div>
                    }
                    </div>
                  }
                }
                </div>
              }
            }
          </div>
        }
      </section>
    }

    @if (sheetOpen()) {
      <div class="scrim" (click)="sheetOpen.set(false)"></div>
      <div class="sheet" role="dialog" aria-label="Filters">
        <span class="handle"></span>

        @for (group of filterGroups; track group.name) {
          <h4 class="sheet-label">{{ group.name }}</h4>
          @for (f of group.options; track f.id) {
            <button matRipple class="sheet-row" (click)="toggleFilter(f.id)">
              <mat-icon>{{ isFilterOn(f.id) ? 'check_box' : 'check_box_outline_blank' }}</mat-icon>
              <span class="sheet-name">{{ f.label }}</span>
              @if (f.count !== null) { <span class="count">{{ f.count() }}</span> }
            </button>
          }
        }

        <div class="sheet-actions">
          <button matRipple class="text-button" (click)="resetFilters()">Reset</button>
          <button matRipple class="filled-button" (click)="sheetOpen.set(false)">
            Show {{ shownChapters() }} chapters
          </button>
        </div>
      </div>
    }
  `,
  styles: `

    /* Spacing is set per element, not by a host gap stacking on padding. */
    :host { display: flex; flex-direction: column; }

    /* Filter pills on the left, stat boxes on the right. */






    /* Subject card */
    .subject {
      margin-top: 0;
      padding: 16px;
      border-radius: var(--mat-sys-corner-extra-large);
      background: var(--mat-sys-surface-container);
    }

    .head { display: flex; align-items: center; gap: 12px; }

    .ring {
      position: relative;
      width: 56px;
      height: 56px;
      flex: none;
      display: grid;
      place-items: center;
      border-radius: 50%;
    }

    .ring::after {
      content: '';
      position: absolute;
      inset: 5px;
      border-radius: 50%;
      background: var(--mat-sys-surface-container);
    }

    .ring-value { position: relative; z-index: 1; font: var(--mat-sys-label-large); }

    .titles { display: flex; flex-direction: column; gap: 2px; flex: 1; min-width: 0; }
    .name { font: var(--mat-sys-title-large); }
    .meta { font: var(--mat-sys-body-medium); color: var(--mat-sys-on-surface-variant); }

    /* Start controls: filled on the subject, outlined on each node. */
    .play {
      width: 32px;
      height: 32px;
      flex: none;
      display: grid;
      place-items: center;
      border: none;
      border-radius: 50%;
      background: var(--mat-sys-surface-container-high);
      color: var(--mat-sys-primary);
      cursor: pointer;
    }

    .play.filled {
      width: 56px;
      height: 56px;
      border: none;
      background: var(--mat-sys-primary);
      color: var(--mat-sys-on-primary);
    }

    .play.filled mat-icon { font-size: 28px; width: 28px; height: 28px; }

    .details {
      display: flex;
      align-items: center;
      gap: 2px;
      margin-top: 8px;
      padding: 4px 0;
      border: none;
      background: transparent;
      color: var(--mat-sys-on-surface-variant);
      font: var(--mat-sys-label-large);
      cursor: pointer;
    }

    .details mat-icon { font-size: 18px; width: 18px; height: 18px; }

    .tree {
      margin-top: 8px;
      padding-top: 8px;
      border-top: 1px solid var(--mat-sys-outline-variant);
    }

    /* Tree rows */
    .node { display: flex; align-items: center; gap: 8px; padding: 0; min-height: 56px; }

    .node-main {
      flex: 1;
      display: flex;
      align-items: center;
      gap: 4px;
      min-width: 0;
      border: none;
      background: transparent;
      color: inherit;
      text-align: left;
      cursor: pointer;
      padding: 0;
    }

    .chev { flex: none; font-size: 18px; width: 18px; height: 18px; color: var(--mat-sys-on-surface-variant); }

    /* One rail per depth, owned by the group — the Reddit thread treatment.
       It runs the full height of the subtree instead of stubbing per row. */

    .chev { flex: none; font-size: 18px; width: 18px; height: 18px; color: var(--mat-sys-on-surface-variant); }

    /* One rail per depth, owned by the group — the Reddit thread treatment.
       It runs the full height of the subtree instead of stubbing per row. */

    .chev { flex: none; font-size: 18px; width: 18px; height: 18px; color: var(--mat-sys-on-surface-variant); }

    /* One rail per depth, owned by the group — the Reddit thread treatment.
       It runs the full height of the subtree instead of stubbing per row. */

    .chev { flex: none; font-size: 18px; width: 18px; height: 18px; color: var(--mat-sys-on-surface-variant); }

    /* One rail per depth, owned by the group — the Reddit thread treatment.
       It runs the full height of the subtree instead of stubbing per row. */

    .chev { flex: none; font-size: 18px; width: 18px; height: 18px; color: var(--mat-sys-on-surface-variant); }

    .chev { flex: none; font-size: 18px; width: 18px; height: 18px; color: var(--mat-sys-on-surface-variant); }

    .node-body { flex: 1; display: flex; flex-direction: column; gap: 8px; min-width: 0; }

    /* Rows are separated by the scale, not by their own padding. */
    .depth0 + .branch { margin-top: 8px; }
    .branch > * + * { margin-top: 8px; }

    .depth0 .node-name { font: var(--mat-sys-title-medium); }
    .depth1 .node-name { font: var(--mat-sys-body-large); }
    .depth2 .node-name { font: var(--mat-sys-body-medium); }
    .depth2 { padding: 6px 0; }
    .node-main:disabled { cursor: default; }
    .node-name.muted { color: var(--mat-sys-on-surface-variant); }

    .bar { display: flex; align-items: center; gap: 8px; }

    .track {
      flex: 1;
      height: 4px;
      border-radius: 2px;
      background: color-mix(in srgb, var(--mat-sys-on-surface) 10%, transparent);
      overflow: hidden;
    }

    .track i { display: block; height: 100%; background: var(--mat-sys-primary); }
    .pct { font: var(--mat-sys-label-small); color: var(--mat-sys-on-surface-variant); }

    /* Status dot, 30dp, as in StatusDot. */
    .tick {
      width: 30px;
      height: 30px;
      flex: none;
      display: grid;
      place-items: center;
      border: none;
      border-radius: 50%;
      background: var(--mat-sys-surface-container-highest);
      color: var(--mat-sys-on-surface-variant);
      cursor: pointer;
    }

    .tick.on { background: var(--mat-sys-primary); color: var(--mat-sys-on-primary); }
    .tick mat-icon, .play mat-icon { font-size: 18px; width: 18px; height: 18px; }

    /* One rail per level, under the parent chevron. Every child hooks off it
       into its first line; the rail stops after the last hook. */
    .branch {
      position: relative;
      padding-left: 32px;
    }

    .branch::before {
      content: '';
      position: absolute;
      left: 10px;
      top: 0;
      bottom: 0;
      width: 1px;
      background: var(--mat-sys-outline-variant);
    }

    .branch > * { position: relative; }

    .branch > *:not(.branch)::before {
      content: '';
      position: absolute;
      left: -22px;
      top: 18px;
      width: 20px;
      height: 12px;
      border-left: 1px solid var(--mat-sys-outline-variant);
      border-bottom: 1px solid var(--mat-sys-outline-variant);
      border-bottom-left-radius: 12px;
    }

    .branch > *:last-child:not(.branch)::after {
      content: '';
      position: absolute;
      left: -24px;
      top: 29px;
      bottom: -2px;
      width: 4px;
      background: var(--mat-sys-surface-container);
    }

    /* M3 secondary tabs, 48dp, with a rounded full-width indicator. */
    .subjects {
      display: block;
      margin: 0 -16px;
      --mat-tab-container-height: 48px;
    }

    ::ng-deep .subjects .mdc-tab { padding: 0 4px; min-width: 0; }

    ::ng-deep .subjects .mdc-tab .mdc-tab__text-label {
      font: var(--mat-sys-title-small);
      letter-spacing: 0.1px;
      color: var(--mat-sys-on-surface-variant);
    }

    ::ng-deep .subjects .mdc-tab--active .mdc-tab__text-label {
      color: var(--mat-sys-primary);
    }

    ::ng-deep .subjects .mdc-tab-indicator__content--underline {
      width: 100%;
      border-top-width: 3px;
      border-radius: 3px 3px 0 0;
      border-color: var(--mat-sys-primary);
    }

    ::ng-deep .subjects .mat-mdc-tab-header { border-bottom: none; }

    .filters {
      position: sticky;
      top: 0;
      z-index: 2;
      display: flex;
      align-items: center;
      gap: 8px;
      padding: 8px 0;
      background: var(--mat-sys-surface);
    }

    .chips {
      flex: 1;
      display: flex;
      gap: 8px;
      overflow-x: auto;
      scrollbar-width: none;
      mask-image: linear-gradient(to right, #000 calc(100% - 16px), transparent 100%);
    }

    .chips::-webkit-scrollbar { display: none; }

    /* M3 filter chip: 32dp, outlined until selected, then tonal with a check. */
    .chip {
      flex: none;
      display: flex;
      align-items: center;
      gap: 6px;
      height: 32px;
      padding: 0 12px;
      border: 1px solid var(--mat-sys-outline-variant);
      border-radius: 8px;
      background: transparent;
      color: var(--mat-sys-on-surface-variant);
      font: var(--mat-sys-label-large);
      cursor: pointer;
    }

    .chip.on {
      border-color: transparent;
      background: var(--mat-sys-secondary-container);
      color: var(--mat-sys-on-secondary-container);
    }

    .chip mat-icon { font-size: 18px; width: 18px; height: 18px; }
    .count { opacity: .7; }

    .more {
      position: relative;
      flex: none;
      width: 40px;
      height: 40px;
      display: grid;
      place-items: center;
      border: none;
      border-radius: 50%;
      background: transparent;
      color: var(--mat-sys-on-surface-variant);
      cursor: pointer;
    }

    /* A filter that is on but not visible in the row still has to show. */
    .more.badged::after {
      content: '';
      position: absolute;
      top: 6px;
      right: 6px;
      width: 8px;
      height: 8px;
      border-radius: 50%;
      background: var(--mat-sys-primary);
    }

    /* Bottom sheet */
    .scrim {
      position: absolute;
      inset: 0;
      z-index: 3;
      background: rgb(0 0 0 / .32);
    }

    .sheet {
      position: absolute;
      left: 0;
      right: 0;
      bottom: 0;
      z-index: 4;
      display: flex;
      flex-direction: column;
      padding: 8px 16px 16px;
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

    .sheet-label {
      margin: 12px 0 4px;
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
      cursor: pointer;
    }

    .sheet-name { flex: 1; text-align: left; }
    .sheet-row mat-icon { color: var(--mat-sys-primary); }

    .sheet-actions {
      display: flex;
      align-items: center;
      gap: 8px;
      margin-top: 16px;
    }

    .text-button {
      height: 40px;
      padding: 0 12px;
      border: none;
      border-radius: var(--mat-sys-corner-full);
      background: transparent;
      color: var(--mat-sys-primary);
      font: var(--mat-sys-label-large);
      cursor: pointer;
    }

    .filled-button {
      flex: 1;
      height: 40px;
      border: none;
      border-radius: var(--mat-sys-corner-full);
      background: var(--mat-sys-primary);
      color: var(--mat-sys-on-primary);
      font: var(--mat-sys-label-large);
      cursor: pointer;
    }
  `,
})
export class SyllabusBrowser {
  protected readonly store = inject(OnboardingStore);
  protected readonly subjects = PACK.subjects;

  /** Active filter ids. Empty means everything. */
  private readonly active = signal<ReadonlySet<string>>(new Set());
  protected readonly sheetOpen = signal(false);

  /** The cuts that ride in the chip row; the rest live in the sheet. */
  protected readonly filterChips = [
    { id: 'due', label: 'Due', count: () => this.dueChapters() },
    { id: 'cls11', label: 'Class 11', count: null },
    { id: 'cls12', label: 'Class 12', count: null },
  ];

  protected readonly filterGroups = [
    {
      name: 'Status',
      options: [
        { id: 'due', label: 'Not finished', count: () => this.dueChapters() },
        { id: 'done', label: 'Finished', count: () => this.doneChapters() },
      ],
    },
    {
      name: 'Class',
      options: [
        { id: 'cls11', label: 'Class 11', count: null },
        { id: 'cls12', label: 'Class 12', count: null },
      ],
    },
  ];

  protected isFilterOn(id: string): boolean { return this.active().has(id); }

  protected toggleFilter(id: string): void {
    const next = new Set(this.active());
    next.has(id) ? next.delete(id) : next.add(id);
    // Status cuts are mutually exclusive; class cuts stack.
    if (id === 'due') next.delete('done');
    if (id === 'done') next.delete('due');
    this.active.set(next);
  }

  protected resetFilters(): void { this.active.set(new Set()); }

  /** True when something is filtering that the chip row does not show. */
  protected readonly hiddenFilterOn = computed(() =>
    [...this.active()].some((id) => !this.filterChips.some((c) => c.id === id)),
  );
  private readonly openCards = signal<ReadonlySet<string>>(new Set([PACK.subjects[0].id]));
  private readonly openSections = signal<ReadonlySet<string>>(
    new Set([PACK.subjects[0].id + PACK.subjects[0].sections[0].name]),
  );

  protected readonly current = signal(PACK.subjects[0].id);

  /** The subject on screen; the tabs are the selector. */
  protected readonly subject = computed(
    () => PACK.subjects.find((s) => s.id === this.current())!,
  );

  protected readonly index = computed(() =>
    PACK.subjects.findIndex((s) => s.id === this.current()),
  );

  protected pick(index: number): void {
    this.current.set(PACK.subjects[index].id);
  }

  /** One subject at a time: the chip row is the selector, not a jump list. */
  protected readonly visible = computed(() =>
    this.subjects.filter((s) => s.id === this.current()),
  );

  /** Groups with nothing left after filtering should not render at all. */
  protected sections(s: Subject): Section[] {
    return s.sections.filter((sec) => this.chapters(sec).length > 0);
  }

  protected chapters(section: Section): Chapter[] {
    const active = this.active();
    return section.chapters.filter((c) => {
      if (active.has('due') && this.isDone(c)) return false;
      if (active.has('done') && !this.isDone(c)) return false;
      if (active.has('cls11') && !active.has('cls12') && c.cls !== 11) return false;
      if (active.has('cls12') && !active.has('cls11') && c.cls !== 12) return false;
      return true;
    });
  }

  private allChapters(): Chapter[] {
    return this.subject().sections.flatMap((sec) => sec.chapters);
  }

  protected dueChapters(): number {
    return this.allChapters().filter((c) => !this.isDone(c)).length;
  }

  protected doneChapters(): number {
    return this.allChapters().filter((c) => this.isDone(c)).length;
  }

  protected shownChapters(): number {
    return this.subject().sections.reduce((n, sec) => n + this.chapters(sec).length, 0);
  }

  /** The card carries the numbers the stats boxes used to. */
  protected subjectMeta(s: Subject): string {
    const shown = this.shownChapters();
    const total = this.total(s);
    const hours = Math.round(
      s.sections
        .flatMap((sec) => sec.chapters)
        .filter((c) => !this.isDone(c))
        .reduce((n, c) => n + c.hours, 0),
    );
    const head = shown === total
      ? `${this.done(s)}/${total} chapters`
      : `${shown} of ${total} chapters`;
    return `${head} · ${hours}h · ${s.questions} Q`;
  }

  private readonly openChapters = signal<ReadonlySet<string>>(new Set());

  protected isDone(chapter: Chapter): boolean {
    return chapterIsDone(chapter, this.store.doneUnits());
  }

  protected isChapterOpen(id: string): boolean { return this.openChapters().has(id); }

  protected toggleChapter(id: string): void {
    this.openChapters.set(flip(this.openChapters(), id));
  }

  protected chapterPercent(chapter: Chapter): number {
    if (chapter.subtopics.length === 0) return this.isDone(chapter) ? 100 : 0;
    const done = chapter.subtopics.filter((t) => this.store.doneUnits().has(t.id)).length;
    return Math.round((done / chapter.subtopics.length) * 100);
  }

  /** Ticking a chapter ticks the subtopics under it. */
  protected toggleChapterDone(chapter: Chapter): void {
    if (chapter.subtopics.length === 0) {
      this.store.toggleUnit(chapter.id);
      return;
    }
    const all = this.isDone(chapter);
    for (const topic of chapter.subtopics) {
      if (this.store.doneUnits().has(topic.id) === all) this.store.toggleUnit(topic.id);
    }
  }

  protected total(s: Subject): number {
    return s.sections.reduce((n, sec) => n + sec.chapters.length, 0);
  }

  protected done(s: Subject): number {
    return s.sections
      .flatMap((sec) => sec.chapters)
      .filter((c) => this.isDone(c)).length;
  }

  protected percent(s: Subject): number {
    return Math.round((this.done(s) / this.total(s)) * 100);
  }

  protected sectionPercent(section: Section): number {
    const done = section.chapters.filter((c) => this.isDone(c)).length;
    return Math.round((done / section.chapters.length) * 100);
  }

  protected sectionDone(section: Section): boolean {
    return section.chapters.every((c) => this.isDone(c));
  }

  /** Ticking a section ticks every chapter under it, as the Android tree does. */
  protected toggleSectionDone(section: Section): void {
    const all = this.sectionDone(section);
    for (const chapter of section.chapters) {
      if (this.isDone(chapter) === all) this.store.toggleUnit(chapter.id);
    }
  }

  protected isCardOpen(id: string): boolean { return this.openCards().has(id); }
  protected isSectionOpen(key: string): boolean { return this.openSections().has(key); }

  protected toggleCard(id: string): void { this.openCards.set(flip(this.openCards(), id)); }
  protected toggleSection(key: string): void { this.openSections.set(flip(this.openSections(), key)); }

  protected ringFill(percent: number): string {
    return `conic-gradient(var(--mat-sys-primary) ${percent}%, ` +
      `color-mix(in srgb, var(--mat-sys-on-surface) 12%, transparent) 0)`;
  }

  protected formatHours(hours: number): string {
    const h = Math.floor(hours);
    const m = Math.round((hours - h) * 60);
    return m === 0 ? `${h}h` : `${h}h ${m}m`;
  }
}

function flip(set: ReadonlySet<string>, key: string): ReadonlySet<string> {
  const next = new Set(set);
  next.has(key) ? next.delete(key) : next.add(key);
  return next;
}
