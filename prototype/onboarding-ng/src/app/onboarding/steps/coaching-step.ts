import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { MatRippleModule } from '@angular/material/core';
import { Shell } from '../shell';
import { COACHINGS, OnboardingStore } from '../state';

@Component({
  selector: 'ob-coaching-step',
  imports: [Shell, MatIconModule, MatRippleModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <ob-shell
      title="Which coaching?"
      [progressIndex]="store.progressIndex()"
      [segments]="store.progressSegments"
      [continueEnabled]="store.coachingId() !== ''"
      (continue)="store.next()">

      <div class="list" role="radiogroup" aria-label="Study setup">
        @for (c of primaryChoices; track c.id) {
          <button matRipple class="row" role="radio"
                  [attr.aria-checked]="choiceSelected(c.id)"
                  [class.on]="choiceSelected(c.id)"
                  (click)="choosePrimary(c.id)">
            <mat-icon class="lead">{{ c.icon }}</mat-icon>
            <span class="text">
              <span class="name">{{ c.label }}</span>
              <span class="mode">{{ c.mode }}</span>
            </span>
            @if (choiceSelected(c.id)) {
              <mat-icon class="mark">check_circle</mat-icon>
            }
          </button>
        }
      </div>

      @if (isCoaching()) {
        <div class="detail">
          <h2>Institute <small>Optional detail, used to label fixed classes</small></h2>
          <div class="list compact" role="radiogroup" aria-label="Institute">
            @for (c of institutes; track c.id) {
              <button matRipple class="row" role="radio" [attr.aria-checked]="store.coachingId() === c.id"
                      [class.on]="store.coachingId() === c.id" (click)="store.coachingId.set(c.id)">
                <mat-icon class="lead">{{ c.icon }}</mat-icon>
                <span class="text"><span class="name">{{ c.label }}</span><span class="mode">{{ c.mode }}</span></span>
                @if (store.coachingId() === c.id) { <mat-icon class="mark">check_circle</mat-icon> }
              </button>
            }
          </div>
        </div>
      }
    </ob-shell>
  `,
  styles: `
    .list { display: flex; flex-direction: column; gap: 8px; }
    .detail { display: flex; flex-direction: column; gap: 8px; }
    .detail h2 { margin: 0; font: var(--mat-sys-title-small); color: var(--mat-sys-on-surface-variant); }
    .detail h2 small { display: block; font: var(--mat-sys-body-small); }
    .compact .row { min-height: 64px; }

    /* M3 two-line list item: 72dp min height, 16dp padding, 16dp icon gap. */
    .row {
      display: flex;
      align-items: center;
      gap: 16px;
      min-height: 72px;
      padding: 8px 16px;
      border: none;
      border-radius: var(--mat-sys-corner-large);
      background: var(--mat-sys-surface-container-high);
      color: var(--mat-sys-on-surface);
      text-align: left;
      cursor: pointer;
    }

    .row.on {
      background: var(--mat-sys-secondary-container);
      color: var(--mat-sys-on-secondary-container);
    }

    .text { display: flex; flex-direction: column; flex: 1; }
    .name { font: var(--mat-sys-body-large); }
    .mode { font: var(--mat-sys-body-medium); color: var(--mat-sys-on-surface-variant); }
    .row.on .mode { color: var(--mat-sys-on-secondary-container); }

    .lead { color: var(--mat-sys-on-surface-variant); }
    .row.on .lead, .mark { color: var(--mat-sys-on-secondary-container); }
  `,
})
export class CoachingStep {
  protected readonly store = inject(OnboardingStore);
  protected readonly coachings = COACHINGS;
  protected readonly primaryChoices = [
    { id: 'self', label: 'Self-study', mode: 'I set my own pace', icon: 'person' },
    { id: 'school', label: 'School or college', mode: 'Classes set the pace', icon: 'account_balance' },
    { id: 'coaching', label: 'Coaching', mode: 'Online or classroom', icon: 'school' },
  ];
  protected readonly institutes = COACHINGS.filter((c) => !['self', 'school'].includes(c.id));

  protected isCoaching(): boolean {
    return this.store.coachingId() !== '' && !['self', 'school'].includes(this.store.coachingId());
  }

  protected choiceSelected(id: string): boolean {
    return id === 'coaching' ? this.isCoaching() : this.store.coachingId() === id;
  }

  protected choosePrimary(id: string): void {
    this.store.coachingId.set(id === 'coaching' ? 'allen' : id);
  }
}
