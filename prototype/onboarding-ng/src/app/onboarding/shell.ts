import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';

/**
 * Screen scaffold: top app bar with segmented progress, scrolling body, one
 * filled CTA. Spacing follows the M3 4dp grid — 16dp pane margin, 24dp between
 * blocks, 8dp inside a block. Type comes only from the M3 type scale roles.
 */
@Component({
  selector: 'ob-shell',
  imports: [MatButtonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <header class="bar">
      <span class="slot"></span>

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
  readonly title = input.required<string>();
  readonly ctaLabel = input('Continue');
  readonly continueEnabled = input(true);
  readonly progressIndex = input<number | null>(null);
  readonly segments = input(6);

  readonly continue = output<void>();

  segmentList(): number[] {
    return Array.from({ length: this.segments() }, (_, i) => i);
  }
}
