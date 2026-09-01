import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { MatRippleModule } from '@angular/material/core';
import { MatIconModule } from '@angular/material/icon';
import { Shell } from '../shell';
import { ACCENTS, APPEARANCES, OnboardingStore } from '../state';

/** First screen: the theme choice that every later screen is rendered in. */
@Component({
  selector: 'ob-appearance-step',
  imports: [Shell, MatIconModule, MatRippleModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <ob-shell
      title="Make it yours"
      ctaLabel="Continue"
      [progressIndex]="store.progressIndex()"
      [segments]="store.progressSegments"
      (continue)="store.next()">

      <div class="group">
      <h2 class="label">Accent</h2>
      <div class="accents" role="radiogroup" aria-label="Accent">
        @for (a of accents; track a.id) {
          <button matRipple class="accent" role="radio"
                  [attr.aria-checked]="store.accent() === a.id"
                  [class.on]="store.accent() === a.id"
                  (click)="store.accent.set(a.id)">
            <span class="dot" [style.background]="a.swatch">
              @if (store.accent() === a.id) { <mat-icon>check</mat-icon> }
            </span>
            <span class="name">{{ a.label }}</span>
          </button>
        }
      </div>
      </div>

      <div class="group">
      <h2 class="label">Background</h2>
      <div class="backgrounds" role="radiogroup" aria-label="Background">
        @for (b of appearances; track b.id) {
          <button matRipple class="bg" role="radio"
                  [attr.aria-checked]="store.appearance() === b.id"
                  [class.on]="store.appearance() === b.id"
                  (click)="store.appearance.set(b.id)">
            <span class="tile" [style.background]="b.swatch">
              <span class="tile-bar" [style.background]="b.ink"></span>
              <span class="tile-line" [style.background]="b.ink"></span>
              <span class="tile-dot" [style.background]="accentSwatch()"></span>
            </span>
            <span class="name">{{ b.label }}</span>
          </button>
        }
      </div>
      </div>
    </ob-shell>
  `,
  styles: `
    .label {
      margin: 0;
      font: var(--mat-sys-title-small);
      color: var(--mat-sys-on-surface-variant);
    }

    .group { display: flex; flex-direction: column; gap: 8px; }

    .accents { display: flex; gap: 8px; }

    .accent {
      flex: 1;
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 8px;
      padding: 12px 4px;
      border: none;
      border-radius: var(--mat-sys-corner-medium);
      background: var(--mat-sys-surface-container-high);
      cursor: pointer;
      font: var(--mat-sys-label-medium);
      color: var(--mat-sys-on-surface);
    }

    .accent.on {
      background: var(--mat-sys-secondary-container);
      color: var(--mat-sys-on-secondary-container);
    }

    .dot {
      width: 40px;
      height: 40px;
      border-radius: 50%;
      display: grid;
      place-items: center;
      color: #fff;
    }

    .dot mat-icon { font-size: 24px; width: 24px; height: 24px; }

    .backgrounds { display: grid; grid-template-columns: repeat(5, 1fr); gap: 8px; }

    .bg {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 8px;
      padding: 12px 8px;
      border: none;
      border-radius: var(--mat-sys-corner-medium);
      background: var(--mat-sys-surface-container-high);
      cursor: pointer;
      font: var(--mat-sys-label-medium);
      color: var(--mat-sys-on-surface);
    }

    .bg.on {
      background: var(--mat-sys-secondary-container);
      color: var(--mat-sys-on-secondary-container);
    }

    /* Selection ring so the chosen background reads at a glance. */
    .bg.on .tile { outline: 2px solid var(--mat-sys-primary); outline-offset: 2px; }

    /* A miniature of the app in that background, not a letterform. */
    .tile {
      position: relative;
      width: 100%;
      aspect-ratio: 1;
      border-radius: var(--mat-sys-corner-small);
      overflow: hidden;
    }

    .tile-bar, .tile-line, .tile-dot { position: absolute; opacity: .85; }

    .tile-bar {
      left: 8px; right: 20px; top: 10px;
      height: 4px;
      border-radius: 2px;
    }

    .tile-line {
      left: 8px; right: 8px; top: 20px;
      height: 3px;
      border-radius: 2px;
      opacity: .4;
    }

    .tile-dot {
      left: 8px; bottom: 8px;
      width: 12px; height: 12px;
      border-radius: 50%;
      opacity: 1;
    }

    @media (max-width: 520px) {
      .backgrounds { grid-template-columns: repeat(3, 1fr); }
    }
  `,
})
export class AppearanceStep {
  protected readonly store = inject(OnboardingStore);
  protected readonly accents = ACCENTS;
  protected readonly appearances = APPEARANCES;

  /** The preview dot shows the accent currently selected. */
  protected accentSwatch(): string {
    return ACCENTS.find((a) => a.id === this.store.accent())!.swatch;
  }
}
