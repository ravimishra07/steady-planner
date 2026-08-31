import { ChangeDetectionStrategy, Component, computed, inject, output, signal } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { MatRippleModule } from '@angular/material/core';
import { OnboardingStore, startOfToday } from '../onboarding/state';
import { Chapter } from '../onboarding/exam-pack';
import { StudyStore, dateKey } from '../study/study-store';

/**
 * The one question the app asks each evening. Coaching decides what gets
 * taught and in what order; the plan's job is to keep up, not to guess ahead
 * of it. Everything downstream — sequence, what tonight leads with, what is
 * even available to study — comes from this list.
 */
@Component({
  selector: 'app-class-log-screen',
  imports: [MatIconModule, MatRippleModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <header class="bar">
      <button matRipple class="icon-btn" (click)="close.emit()" aria-label="Back">
        <mat-icon>arrow_back</mat-icon>
      </button>
      <h1 class="bar-title">What did class cover?</h1>
    </header>

    <div class="tabs">
      @for (s of store.subjects(); track s.id) {
        <button matRipple class="tab" [class.on]="current() === s.id" (click)="current.set(s.id)">
          {{ s.name }}
          @if (countIn(s.id) > 0) { <span class="pip">{{ countIn(s.id) }}</span> }
        </button>
      }
    </div>

    <div class="scroll">
      @if (suggested().length > 0) {
        <h2 class="group">Probably next</h2>
        <div class="sheet">
          @for (c of suggested(); track c.id) {
            <button matRipple class="row" (click)="toggle(c)">
              <mat-icon [class.filled]="picked().has(c.id)">
                {{ picked().has(c.id) ? 'check_circle' : 'radio_button_unchecked' }}
              </mat-icon>
              <span class="row-name">{{ c.name }}</span>
            </button>
          }
        </div>
      }

      <h2 class="group">Everything else</h2>
      <div class="sheet">
        @for (c of rest(); track c.id) {
          <button matRipple class="row" (click)="toggle(c)">
            <mat-icon [class.filled]="picked().has(c.id)">
              {{ picked().has(c.id) ? 'check_circle' : 'radio_button_unchecked' }}
            </mat-icon>
            <span class="row-name">{{ c.name }}</span>
            @if (alreadyCovered(c)) { <span class="row-note">done earlier</span> }
          </button>
        } @empty {
          <p class="note">No chapters in this subject yet.</p>
        }
      </div>
    </div>

    <footer class="actions">
      <button matRipple class="text-btn" (click)="nothing()">Class didn't happen</button>
      <button matRipple class="filled-btn" (click)="save()">
        Save{{ picked().size > 0 ? ' ' + picked().size : '' }}
      </button>
    </footer>
  `,
  styles: `
    :host {
      display: flex;
      flex-direction: column;
      height: 100%;
      background: var(--mat-sys-surface);
      color: var(--mat-sys-on-surface);
    }

    .bar { flex: none; display: flex; align-items: center; gap: 4px; height: 64px; padding: 0 16px 0 4px; }
    .bar-title { flex: 1; margin: 0; font: var(--mat-sys-title-large); }

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

    .tabs { flex: none; display: flex; gap: 4px; padding: 0 16px 8px; overflow-x: auto; }

    .tab {
      flex: none;
      display: flex;
      align-items: center;
      gap: 6px;
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

    .pip {
      display: grid;
      place-items: center;
      min-width: 18px;
      height: 18px;
      padding: 0 5px;
      border-radius: var(--mat-sys-corner-full);
      background: var(--mat-sys-primary);
      color: var(--mat-sys-on-primary);
      font: var(--mat-sys-label-small);
    }

    .scroll { flex: 1; min-height: 0; overflow-y: auto; padding: 0 16px 16px; }

    .group {
      margin: 16px 0 8px;
      font: var(--mat-sys-title-small);
      color: var(--mat-sys-on-surface-variant);
    }

    .sheet {
      display: flex;
      flex-direction: column;
      border-radius: var(--mat-sys-corner-large);
      background: var(--mat-sys-surface-container);
      overflow: hidden;
    }

    .row {
      display: flex;
      align-items: center;
      gap: 16px;
      width: 100%;
      min-height: 56px;
      padding: 10px 16px;
      border: none;
      background: transparent;
      color: var(--mat-sys-on-surface);
      text-align: left;
      font: var(--mat-sys-body-large);
      cursor: pointer;
    }

    .row + .row { box-shadow: inset 0 1px 0 var(--mat-sys-outline-variant); }
    .row mat-icon { flex: none; color: var(--mat-sys-primary); }
    .row-name { flex: 1; min-width: 0; }
    .row-note { flex: none; font: var(--mat-sys-label-small); color: var(--mat-sys-on-surface-variant); }
    .note { margin: 16px; font: var(--mat-sys-body-medium); color: var(--mat-sys-on-surface-variant); }

    .actions {
      flex: none;
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 8px;
      padding: 12px 16px;
      border-top: 1px solid var(--mat-sys-outline-variant);
    }

    .text-btn {
      height: 40px;
      padding: 0 12px;
      border: none;
      background: transparent;
      color: var(--mat-sys-on-surface-variant);
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
  `,
})
export class ClassLogScreen {
  readonly close = output<void>();

  protected readonly store = inject(OnboardingStore);
  private readonly study = inject(StudyStore);

  protected readonly current = signal(this.store.subjects()[0]?.id ?? '');
  protected readonly picked = signal<ReadonlySet<string>>(
    new Set(this.study.classOn(dateKey(startOfToday()))),
  );

  private chapters(): Chapter[] {
    const subject = this.store.subjects().find((s) => s.id === this.current());
    return subject ? subject.sections.flatMap((sec) => sec.chapters) : [];
  }

  /** The chapters just past where class has already reached. */
  protected readonly suggested = computed(() => {
    const covered = this.study.classCovered();
    const list = this.chapters();
    const lastIndex = list.reduce(
      (last, c, i) => (covered.includes(c.id) ? i : last),
      -1,
    );
    return list.slice(lastIndex + 1, lastIndex + 4);
  });

  protected readonly rest = computed(() => {
    const shown = new Set(this.suggested().map((c) => c.id));
    return this.chapters().filter((c) => !shown.has(c.id));
  });

  protected alreadyCovered(chapter: Chapter): boolean {
    return this.study.classCovered().includes(chapter.id);
  }

  protected countIn(subjectId: string): number {
    return [...this.picked()].filter((id) => id.startsWith(subjectId + '.')).length;
  }

  protected toggle(chapter: Chapter): void {
    const next = new Set(this.picked());
    next.has(chapter.id) ? next.delete(chapter.id) : next.add(chapter.id);
    this.picked.set(next);
  }

  protected save(): void {
    this.study.logClass(dateKey(startOfToday()), [...this.picked()]);
    this.close.emit();
  }

  /** A day with no class is worth recording too — it is not the same as unasked. */
  protected nothing(): void {
    this.study.logClass(dateKey(startOfToday()), []);
    this.close.emit();
  }
}
