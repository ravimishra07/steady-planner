import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { MatTabsModule } from '@angular/material/tabs';
import { MatRippleModule } from '@angular/material/core';
import { OnboardingStore } from '../onboarding/state';
import { PACK, Subject, Section, Chapter, chapterIsDone } from '../onboarding/exam-pack';

type Filter = 'all' | 'due';

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

    <div class="stats">
      <div class="filter">
        <span class="stat-label">Filter</span>
        <div class="pills">
          <button matRipple class="pill" [class.on]="filter() === 'all'" (click)="filter.set('all')">
            All ({{ subjects.length }})
          </button>
          <button matRipple class="pill" [class.on]="filter() === 'due'" (click)="filter.set('due')">
            Due ({{ dueCount() }})
          </button>
        </div>
      </div>

      <div class="boxes">
        <div class="box">
          <span class="stat-label">Completed</span>
          <span class="stat-value">{{ completedPercent() }}%</span>
        </div>
        <div class="box">
          <span class="stat-label">Planned</span>
          <span class="stat-value">{{ formatHours(plannedHours()) }}</span>
        </div>
      </div>
    </div>

    @for (s of visible(); track s.id) {
      <section class="subject" [id]="'subject-' + s.id">
        <header class="head">
          <span class="ring" [style.background]="ringFill(percent(s))">
            <span class="ring-value">{{ percent(s) }}%</span>
          </span>
          <span class="titles">
            <span class="name">{{ s.name }}</span>
            <span class="meta">{{ done(s) }}/{{ total(s) }} chapters · {{ s.questions }} Q in paper</span>
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
            @for (section of s.sections; track section.name) {
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
  `,
  styles: `
    /* Subject switching is a tab bar, not chips. */
    .subjects { display: block; margin: 0 -16px 12px; }
    ::ng-deep .subjects .mdc-tab { padding: 0 4px; min-width: 0; }

    :host { display: flex; flex-direction: column; gap: 12px; }

    /* Filter pills on the left, stat boxes on the right. */
    .stats { display: flex; align-items: flex-end; justify-content: space-between; gap: 16px; }
    .filter { display: flex; flex-direction: column; gap: 4px; }
    .pills { display: flex; gap: 8px; }

    .pill {
      height: 32px;
      padding: 0 16px;
      border: none;
      border-radius: var(--mat-sys-corner-full);
      background: var(--mat-sys-surface-container-high);
      color: var(--mat-sys-on-surface-variant);
      font: var(--mat-sys-label-large);
      cursor: pointer;
    }

    .pill.on { background: var(--mat-sys-primary); color: var(--mat-sys-on-primary); }

    .boxes { display: flex; gap: 8px; }
    .box { display: flex; flex-direction: column; align-items: center; gap: 4px; }

    .stat-label { font: var(--mat-sys-label-medium); color: var(--mat-sys-on-surface-variant); }

    .stat-value {
      padding: 6px 12px;
      border-radius: var(--mat-sys-corner-medium);
      background: var(--mat-sys-surface-container-high);
      font: var(--mat-sys-title-small);
      color: var(--mat-sys-on-surface);
    }

    /* Subject card */
    .subject {
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
      padding-top: 4px;
      border-top: 1px solid var(--mat-sys-outline-variant);
    }

    /* Tree rows */
    .node { display: flex; align-items: center; gap: 8px; padding: 8px 0; }

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

    .node-body { flex: 1; display: flex; flex-direction: column; gap: 6px; min-width: 0; }

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
  `,
})
export class SyllabusBrowser {
  protected readonly store = inject(OnboardingStore);
  protected readonly subjects = PACK.subjects;

  protected readonly filter = signal<Filter>('all');
  private readonly openCards = signal<ReadonlySet<string>>(new Set([PACK.subjects[0].id]));
  private readonly openSections = signal<ReadonlySet<string>>(
    new Set([PACK.subjects[0].id + PACK.subjects[0].sections[0].name]),
  );

  protected readonly current = signal(PACK.subjects[0].id);

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

  protected readonly dueCount = computed(
    () => this.subjects.filter((s) => this.percent(s) < 100).length,
  );

  protected readonly completedPercent = computed(() => {
    const all = this.subjects.flatMap((s) => s.sections.flatMap((sec) => sec.chapters));
    const done = all.filter((c) => this.store.doneUnits().has(c.id)).length;
    return Math.round((done / all.length) * 100);
  });

  /** Hours still to cover — what the plan has left to fit. */
  protected readonly plannedHours = computed(() => this.store.requiredHours());

  protected chapters(section: Section): Chapter[] {
    return this.filter() === 'due'
      ? section.chapters.filter((c) => !this.isDone(c))
      : section.chapters;
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
