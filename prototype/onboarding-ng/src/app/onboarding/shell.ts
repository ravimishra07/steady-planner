import { ChangeDetectionStrategy, Component, inject, input, output } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { Router } from '@angular/router';
import { OnboardingStore } from './state';

/**
 * Screen scaffold: top app bar with segmented progress, scrolling body, one
 * filled CTA. Spacing follows the M3 4dp grid — 16dp pane margin, 24dp between
 * blocks, 8dp inside a block. Type comes only from the M3 type scale roles.
 */
@Component({
  selector: 'ob-shell',
  imports: [MatButtonModule, MatIconModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <header class="bar">
      <button class="nav-button" type="button" (click)="goBack()"
              [attr.aria-label]="store.canGoBack() ? 'Back' : 'Exit setup'">
        <mat-icon>{{ store.canGoBack() ? 'arrow_back' : 'close' }}</mat-icon>
      </button>

      @if (progressIndex() !== null) {
        <div class="progress" role="progressbar"
             [attr.aria-valuenow]="progressIndex()! + 1"
             [attr.aria-valuemax]="segments()">
          @for (seg of segmentList(); track seg) {
            <span class="seg" [class.on]="seg <= progressIndex()!"></span>
          }
        </div>
      } @else {
        <span class="grow"></span>
      }

      <span class="slot"></span>
    </header>

    <section class="body">
      <h1 class="title">{{ title() }}</h1>
      <ng-content />
    </section>

    <footer class="cta">
      <button matButton="filled" [disabled]="!continueEnabled()" (click)="continue.emit()">
        {{ ctaLabel() }}
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

    /* M3 top app bar: 64dp tall, 4dp horizontal inset for the icon slot. */
    .bar {
      display: flex;
      align-items: center;
      gap: 8px;
      height: 64px;
      flex: none;
      padding: 0 4px;
    }

    .slot { width: 48px; flex: none; }
    .nav-button {
      width: 48px;
      height: 48px;
      display: grid;
      place-items: center;
      flex: none;
      border: 0;
      border-radius: var(--mat-sys-corner-full);
      background: transparent;
      color: var(--mat-sys-on-surface);
      cursor: pointer;
    }
    .nav-button:hover { background: var(--mat-sys-surface-container-high); }
    .grow { flex: 1; }

    .progress { display: flex; gap: 4px; flex: 1; }

    .seg {
      flex: 1;
      height: 4px;
      border-radius: 2px;
      background: var(--mat-sys-surface-container-highest);
    }

    .seg.on { background: var(--mat-sys-primary); }

    /* Pane margin 16dp; blocks separated by 24dp. */
    .body {
      flex: 1;
      overflow-y: auto;
      overflow-x: hidden;
      padding: 0 16px 8px;
      display: flex;
      flex-direction: column;
      gap: 24px;
    }

    .title {
      margin: 0;
      font: var(--mat-sys-headline-large);
      color: var(--mat-sys-on-surface);
    }

    /* The CTA sits on its own band with a hairline, so a long list reads as
       scrolling under a boundary rather than being sliced off. */
    .cta {
      flex: none;
      padding: 12px 16px 24px;
      border-top: 1px solid var(--mat-sys-outline-variant);
      background: var(--mat-sys-surface);
    }

    /* M3 medium button: 56dp tall, title-medium label, 24dp side padding,
       full corner radius. Angular Material defaults to the 40dp small label,
       so the label role is set here. */
    .cta button {
      width: 100%;
      height: 56px;
      font: var(--mat-sys-title-medium);
      letter-spacing: normal;
    }
  `,
})
export class Shell {
  protected readonly store = inject(OnboardingStore);
  private readonly router = inject(Router);
  readonly title = input.required<string>();
  readonly ctaLabel = input('Continue');
  readonly continueEnabled = input(true);
  readonly progressIndex = input<number | null>(null);
  readonly segments = input(6);

  readonly continue = output<void>();

  protected goBack(): void {
    if (this.store.canGoBack()) this.store.back();
    else void this.router.navigateByUrl('/today');
  }

  segmentList(): number[] {
    return Array.from({ length: this.segments() }, (_, i) => i);
  }
}
