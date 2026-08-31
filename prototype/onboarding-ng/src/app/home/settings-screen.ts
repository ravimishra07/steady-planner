import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import { MatRippleModule } from '@angular/material/core';
import { MatSliderModule } from '@angular/material/slider';
import {
  ACCENTS,
  APPEARANCES,
  BLOCKABLE_APPS,
  COACHINGS,
  OnboardingStore,
  startOfToday,
} from '../onboarding/state';
import {
  COMMITMENT_PRESETS,
  Commitment,
  DAY_INITIALS,
  clockLabel,
  minutesFromTimeValue,
  timeValue,
} from '../onboarding/commitments';
import { PACK } from '../onboarding/exam-pack';
import { StudyStore } from '../study/study-store';
import { PACES } from '../study/retention';
import { clearDemo, loadDemo } from '../study/demo-data';

/** Where a row leads, when it leads somewhere. */
type Page =
  | 'root' | 'plan' | 'hours' | 'appearance' | 'revision' | 'focus' | 'fixed' | 'legal' | 'about';

/** Short passages, each in its own sheet — not one wall of policy text. */
const LEGAL = [
  {
    heading: 'What this app stores',
    body: "Your plan, your sittings and your ticks, in this device's own storage. There is no account and no server.",
  },
  {
    heading: 'What it collects',
    body: 'No analytics, no advertising identifiers, no crash reporting, no contacts, no location. It makes no network requests of its own.',
  },
  { heading: 'Who it is shared with', body: 'Nobody. There is no third party to share it with.' },
  {
    heading: 'Deleting it',
    body: 'Delete everything removes all of it at once. Uninstalling does the same.',
  },
  {
    heading: 'Terms of use',
    body: "The syllabus, hour estimates and revision schedule are planning aids, not guarantees. Check your exam's official syllabus and dates with the conducting body.",
  },
];

const BREAKS = [5, 10, 15, 20, 30];

/** Links a real build would point at its own accounts. */
const SOCIALS = [
  { id: 'x', label: 'X' },
  { id: 'instagram', label: 'Instagram' },
  { id: 'youtube', label: 'YouTube' },
  { id: 'github', label: 'GitHub' },
];

/**
 * Settings. Everything onboarding decided, changeable — that is the screen's
 * whole job, and the previous version showed the values without letting a
 * single one of them be edited.
 */
@Component({
  selector: 'app-settings-screen',
  imports: [MatIconModule, MatRippleModule, MatSliderModule, FormsModule, DatePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @switch (page()) {
      @case ('root') {
        <header class="bar"><h1 class="bar-title">Settings</h1></header>

        <div class="scroll">
          <!-- Who this plan is for, before any of the knobs. -->
          <button matRipple class="profile" (click)="page.set('plan')">
            <span class="crest"><mat-icon class="filled">school</mat-icon></span>
            <span class="profile-text">
              <span class="profile-name">{{ pack.displayName }}</span>
              <span class="profile-meta">{{ store.days() }} days left · {{ store.weekdayHours() }}h a day</span>
            </span>
            <span class="profile-stat">
              <span class="profile-value">{{ coverage() }}%</span>
              <span class="profile-label">covered</span>
            </span>
          </button>

          <h2 class="group">Plan</h2>
          <div class="sheet">
            <button matRipple class="row" (click)="page.set('plan')">
              <mat-icon class="lead">event</mat-icon>
              <span class="row-title">Exam &amp; date</span>
              <span class="row-value">{{ store.targetDate() | date: 'd MMM y' }}</span>
              <mat-icon class="chev">chevron_right</mat-icon>
            </button>

            <button matRipple class="row" (click)="page.set('hours')">
              <mat-icon class="lead">schedule</mat-icon>
              <span class="row-title">Hours &amp; breaks</span>
              <span class="row-value">{{ store.weekdayHours() }}h · {{ store.weekendHours() }}h</span>
              <mat-icon class="chev">chevron_right</mat-icon>
            </button>

            <button matRipple class="row" (click)="page.set('fixed')">
              <mat-icon class="lead">event_busy</mat-icon>
              <span class="row-title">Fixed hours</span>
              <span class="row-value">{{ store.commitments().length }} blocks</span>
              <mat-icon class="chev">chevron_right</mat-icon>
            </button>
          </div>

          <h2 class="group">App</h2>
          <div class="sheet">
            <button matRipple class="row" (click)="page.set('appearance')">
              <mat-icon class="lead">palette</mat-icon>
              <span class="row-title">Appearance</span>
              <span class="row-value">{{ appearanceName() }}</span>
              <mat-icon class="chev">chevron_right</mat-icon>
            </button>

            <button matRipple class="row" (click)="page.set('revision')">
              <mat-icon class="lead">history</mat-icon>
              <span class="row-title">Revision schedule</span>
              <span class="row-value">{{ paceName() }}</span>
              <mat-icon class="chev">chevron_right</mat-icon>
            </button>

            <button matRipple class="row" (click)="page.set('focus')">
              <mat-icon class="lead" [class.filled]="store.blockApps()">
                  {{ store.blockApps() ? 'lock' : 'lock_open' }}
                </mat-icon>
              <span class="row-title">Focus &amp; blocking</span>
              <span class="row-value">{{ store.blockApps() ? store.blockedApps().size + ' apps' : 'Off' }}</span>
              <mat-icon class="chev">chevron_right</mat-icon>
            </button>
          </div>

          <h2 class="group">Reminders</h2>
          <div class="sheet">
            @for (n of notifications; track n.id) {
              <button matRipple class="row" (click)="toggleNotification(n.id)">
                <mat-icon class="lead">{{ n.icon }}</mat-icon>
                <span class="row-title">{{ n.label }}</span>
                <span class="switch" [class.on]="notificationOn(n.id)"><span class="knob"></span></span>
              </button>
            }
          </div>

          <h2 class="group">Your data</h2>
          <div class="sheet">
            <button matRipple class="row" (click)="exportData()">
              <mat-icon class="lead">download</mat-icon>
              <span class="row-title">Export my data</span>
              <span class="row-value">{{ storageLabel() }}</span>
              <mat-icon class="chev">chevron_right</mat-icon>
            </button>

            <button matRipple class="row danger" (click)="confirmWipe.set(true)">
              <mat-icon class="lead">delete_forever</mat-icon>
              <span class="row-title">Delete everything</span>
              <mat-icon class="chev">chevron_right</mat-icon>
            </button>
          </div>

          <h2 class="group">About</h2>
          <div class="sheet">
            <button matRipple class="row" (click)="page.set('legal')">
              <mat-icon class="lead">shield</mat-icon>
              <span class="row-title">Privacy &amp; terms</span>
              <mat-icon class="chev">chevron_right</mat-icon>
            </button>

            <button matRipple class="row" (click)="page.set('about')">
              <mat-icon class="lead">info</mat-icon>
              <span class="row-title">Where the numbers come from</span>
              <mat-icon class="chev">chevron_right</mat-icon>
            </button>

            <div class="row">
              <mat-icon class="lead">verified</mat-icon>
              <span class="row-title">Version</span>
              <span class="row-value">{{ version }}</span>
            </div>
          </div>

          <div class="socials">
            @for (s of socials; track s.id) {
              <button matRipple class="social">{{ s.label }}</button>
            }
          </div>

          <h2 class="group">Developer</h2>
          <div class="sheet">
            <button matRipple class="row" (click)="load()">
              <mat-icon class="lead">science</mat-icon>
              <span class="row-title">Load demo history</span>
              <span class="row-value">{{ loaded() ? 'Loaded' : '' }}</span>
            </button>

            <button matRipple class="row" (click)="clear()">
              <mat-icon class="lead">restart_alt</mat-icon>
              <span class="row-title">Reset to a fresh account</span>
            </button>
          </div>

          <p class="foot">Steadyline · built for one exam at a time</p>
        </div>
      }

      @case ('plan') {
        <header class="bar">
          <button matRipple class="icon-btn" (click)="page.set('root')" aria-label="Back">
            <mat-icon>arrow_back</mat-icon>
          </button>
          <h1 class="bar-title small">Exam &amp; date</h1>
        </header>

        <div class="scroll">
          <div class="sheet">
            <div class="row">
              <mat-icon class="lead">school</mat-icon>
              <span class="row-title">Exam</span>
              <span class="row-value">{{ pack.displayName }}</span>
            </div>
            <div class="row">
              <mat-icon class="lead">groups</mat-icon>
              <span class="row-title">Coaching</span>
              <span class="row-value">{{ coachingName() }}</span>
            </div>
            <label class="row">
              <mat-icon class="lead">event</mat-icon>
              <span class="row-title">Exam date</span>
              <span class="edit">
                {{ store.targetDate() | date: 'd MMM y' }}
                <input class="date-input" type="date" [value]="dateValue()"
                       (change)="setDate($any($event.target).value)" />
              </span>
            </label>
          </div>

          <p class="note">{{ store.days() }} days from today.</p>
        </div>
      }

      @case ('hours') {
        <header class="bar">
          <button matRipple class="icon-btn" (click)="page.set('root')" aria-label="Back">
            <mat-icon>arrow_back</mat-icon>
          </button>
          <h1 class="bar-title small">Hours &amp; breaks</h1>
        </header>

        <div class="scroll">
          <div class="sheet pad">
            <div class="field">
              <span class="field-head">
                <span class="row-title">Weekdays</span>
                <span class="row-value">{{ store.weekdayHours() }}h</span>
              </span>
              <mat-slider min="1" max="14" step="0.5" discrete>
                <input matSliderThumb [ngModel]="store.weekdayHours()"
                       (ngModelChange)="store.weekdayHours.set($event)" />
              </mat-slider>
            </div>

            <div class="field">
              <span class="field-head">
                <span class="row-title">Weekends</span>
                <span class="row-value">{{ store.weekendHours() }}h</span>
              </span>
              <mat-slider min="1" max="16" step="0.5" discrete>
                <input matSliderThumb [ngModel]="store.weekendHours()"
                       (ngModelChange)="store.weekendHours.set($event)" />
              </mat-slider>
            </div>
          </div>

          <h2 class="group">Awake between</h2>
          <div class="sheet pad">
            <div class="times">
              <label>
                <span class="field-label">Up at</span>
                <input type="time" [value]="value(store.wakeMinute())"
                       (change)="store.wakeMinute.set(parse($any($event.target).value))" />
              </label>
              <label>
                <span class="field-label">Lights out</span>
                <input type="time" [value]="value(store.sleepMinute())"
                       (change)="store.sleepMinute.set(parse($any($event.target).value))" />
              </label>
            </div>
          </div>

          <h2 class="group">Break between sittings</h2>
          <div class="sheet pad">
            <div class="chips">
              @for (m of breaks; track m) {
                <button matRipple class="chip" [class.on]="store.breakMinutes() === m"
                        (click)="store.breakMinutes.set(m)">{{ m }}m</button>
              }
            </div>
          </div>

          <p class="note">{{ freeLabel() }} free on a weekday after fixed hours.</p>
        </div>
      }

      @case ('appearance') {
        <header class="bar">
          <button matRipple class="icon-btn" (click)="page.set('root')" aria-label="Back">
            <mat-icon>arrow_back</mat-icon>
          </button>
          <h1 class="bar-title small">Appearance</h1>
        </header>

        <div class="scroll">
          <h2 class="group">Theme</h2>
          <div class="sheet">
            @for (a of appearances; track a.id) {
              <button matRipple class="row" (click)="store.appearance.set(a.id)">
                <span class="swatch" [style.background]="a.swatch"></span>
                <span class="row-title">{{ a.label }}</span>
                @if (store.appearance() === a.id) { <mat-icon class="filled tick">check_circle</mat-icon> }
              </button>
            }
          </div>

          <h2 class="group">Accent</h2>
          <div class="sheet pad">
            <div class="dots">
              @for (a of accents; track a.id) {
                <button class="dot" [class.on]="store.accent() === a.id"
                        [style.background]="a.swatch" [attr.aria-label]="a.label"
                        (click)="store.accent.set(a.id)">
                  @if (store.accent() === a.id) { <mat-icon class="filled">check</mat-icon> }
                </button>
              }
            </div>
          </div>
        </div>
      }

      @case ('revision') {
        <header class="bar">
          <button matRipple class="icon-btn" (click)="page.set('root')" aria-label="Back">
            <mat-icon>arrow_back</mat-icon>
          </button>
          <h1 class="bar-title small">Revision schedule</h1>
        </header>

        <div class="scroll">
          <div class="sheet">
            @for (p of paces; track p.id) {
              <button matRipple class="row" (click)="store.revisionPace.set(p.id)">
                <span class="row-title">
                  {{ p.label }}
                  <span class="row-sub">{{ p.hint }}</span>
                </span>
                @if (store.revisionPace() === p.id) {
                  <mat-icon class="filled tick">check_circle</mat-icon>
                }
              </button>
            }
          </div>

          <p class="note">
            A chapter comes back on these gaps, counted from the day you last studied it. Saying a
            sitting went badly pulls the next one closer; saying it went well pushes it out.
          </p>
        </div>
      }

      @case ('focus') {
        <header class="bar">
          <button matRipple class="icon-btn" (click)="page.set('root')" aria-label="Back">
            <mat-icon>arrow_back</mat-icon>
          </button>
          <h1 class="bar-title small">Focus &amp; blocking</h1>
        </header>

        <div class="scroll">
          <div class="sheet">
            <button matRipple class="row" (click)="store.blockApps.set(!store.blockApps())">
              <mat-icon class="lead" [class.filled]="store.blockApps()">
                  {{ store.blockApps() ? 'lock' : 'lock_open' }}
                </mat-icon>
              <span class="row-title">Block apps during a session</span>
              <span class="switch" [class.on]="store.blockApps()"><span class="knob"></span></span>
            </button>
          </div>

          <h2 class="group">Blocked while the timer runs</h2>
          <div class="sheet pad">
            <div class="chips">
              @for (a of apps; track a.id) {
                <button matRipple class="chip" [class.on]="store.blockedApps().has(a.id)"
                        [disabled]="!store.blockApps()" (click)="store.toggleBlockedApp(a.id)">
                  <mat-icon>{{ a.icon }}</mat-icon>
                  {{ a.label }}
                </button>
              }
            </div>
          </div>

          <p class="note">Released the moment a session stops.</p>
        </div>
      }

      @case ('fixed') {
        <header class="bar">
          <button matRipple class="icon-btn" (click)="page.set('root')" aria-label="Back">
            <mat-icon>arrow_back</mat-icon>
          </button>
          <h1 class="bar-title small">Fixed hours</h1>
        </header>

        <div class="scroll">
          @for (c of store.commitments(); track c.id) {
            <div class="sheet pad card">
              <div class="card-head">
                <mat-icon>{{ icon(c) }}</mat-icon>
                <span class="card-name">{{ c.label }}</span>
                <span class="row-value">{{ range(c) }}</span>
                <button matRipple class="icon-btn small" (click)="store.removeCommitment(c.id)"
                        [attr.aria-label]="'Remove ' + c.label">
                  <mat-icon>close</mat-icon>
                </button>
              </div>

              <div class="times">
                <label>
                  <span class="field-label">Starts</span>
                  <input type="time" [value]="value(c.startMinute)"
                         (change)="setStart(c, $any($event.target).value)" />
                </label>
                <label>
                  <span class="field-label">Ends</span>
                  <input type="time" [value]="value(c.startMinute + c.minutes)"
                         (change)="setEnd(c, $any($event.target).value)" />
                </label>
              </div>

              <div class="days">
                @for (d of dayInitials; track $index) {
                  <button matRipple class="day" [class.on]="c.days.includes($index)"
                          (click)="store.toggleCommitmentDay(c.id, $index)">{{ d }}</button>
                }
              </div>
            </div>
          } @empty {
            <p class="note">Nothing fixed — the whole day is yours.</p>
          }

          <h2 class="group">Add</h2>
          <div class="sheet pad">
            <div class="chips">
              @for (p of presets; track p.kind) {
                <button matRipple class="chip" (click)="store.addCommitment(p)">
                  <mat-icon>{{ p.icon }}</mat-icon>
                  {{ p.label }}
                </button>
              }
            </div>
          </div>
        </div>
      }

      @case ('legal') {
        <header class="bar">
          <button matRipple class="icon-btn" (click)="page.set('root')" aria-label="Back">
            <mat-icon>arrow_back</mat-icon>
          </button>
          <h1 class="bar-title small">Privacy &amp; terms</h1>
        </header>

        <div class="scroll">
          @for (s of legal; track s.heading) {
            <h2 class="group">{{ s.heading }}</h2>
            <div class="sheet pad"><p class="prose">{{ s.body }}</p></div>
          }
          <p class="foot">Last updated 31 August 2026</p>
        </div>
      }

      @case ('about') {
        <header class="bar">
          <button matRipple class="icon-btn" (click)="page.set('root')" aria-label="Back">
            <mat-icon>arrow_back</mat-icon>
          </button>
          <h1 class="bar-title small">Where the numbers come from</h1>
        </header>

        <div class="scroll">
          <div class="sheet">
            @for (line of methodology; track line) {
              <div class="row note-row"><span class="prose">{{ line }}</span></div>
            }
          </div>
          <p class="foot">{{ pack.meta.source }}</p>
        </div>
      }
    }

    @if (confirmWipe()) {
      <div class="scrim" (click)="confirmWipe.set(false)"></div>
      <div class="dialog" role="alertdialog" aria-label="Delete everything">
        <mat-icon class="dialog-icon">delete_forever</mat-icon>
        <h3 class="dialog-title">Delete everything?</h3>
        <p class="dialog-body">
          Your plan, {{ study.sessions().length }} logged sittings and every tick go with it.
          This cannot be undone.
        </p>
        <div class="dialog-actions">
          <button matRipple class="text-btn" (click)="confirmWipe.set(false)">Cancel</button>
          <button matRipple class="text-btn danger" (click)="wipe()">Delete</button>
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

    .bar {
      flex: none;
      display: flex;
      align-items: center;
      gap: 4px;
      height: 64px;
      padding: 0 4px 0 16px;
      background: var(--mat-sys-surface);
    }

    .bar-title { margin: 0; font: var(--mat-sys-headline-medium); }
    .bar-title.small { font: var(--mat-sys-title-large); }

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

    .icon-btn.small { width: 36px; height: 36px; color: var(--mat-sys-on-surface-variant); }
    .scroll { flex: 1; min-height: 0; overflow-y: auto; padding: 0 16px 32px; }

    /* Who the plan is for, before any of the knobs. */
    .profile {
      display: flex;
      align-items: center;
      gap: 16px;
      width: 100%;
      padding: 20px;
      border: none;
      border-radius: 28px;
      background: var(--mat-sys-secondary-container);
      color: var(--mat-sys-on-secondary-container);
      text-align: left;
      cursor: pointer;
    }

    .crest {
      display: grid;
      place-items: center;
      width: 52px;
      height: 52px;
      flex: none;
      border-radius: var(--mat-sys-corner-full);
      background: var(--mat-sys-primary);
      color: var(--mat-sys-on-primary);
    }

    .crest mat-icon { font-size: 28px; width: 28px; height: 28px; }
    .profile-text { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 2px; }
    .profile-name { font: var(--mat-sys-title-medium); }
    .profile-meta { font: var(--mat-sys-body-small); opacity: .8; }
    .profile-stat { flex: none; display: flex; flex-direction: column; align-items: flex-end; }
    .profile-value { font: var(--mat-sys-title-large); }
    .profile-label { font: var(--mat-sys-label-small); opacity: .8; }

    /* Section label sits outside its sheet, quiet and small. */
    .group {
      margin: 24px 4px 8px;
      font: var(--mat-sys-label-large);
      color: var(--mat-sys-on-surface-variant);
    }

    /* One sheet per section: rows inside it, hairlines between them. */
    .sheet {
      display: flex;
      flex-direction: column;
      border-radius: var(--mat-sys-corner-large);
      background: var(--mat-sys-surface-container);
      overflow: hidden;
    }

    .sheet.pad { padding: 16px; gap: 16px; }

    /* A row is a title and its value. No explanatory second line. */
    .row {
      width: 100%;
      display: flex;
      align-items: center;
      gap: 16px;
      min-height: 56px;
      padding: 8px 16px;
      border: none;
      background: transparent;
      color: var(--mat-sys-on-surface);
      text-align: left;
      font: inherit;
    }

    button.row, label.row { cursor: pointer; }
    .row + .row { box-shadow: inset 0 1px 0 var(--mat-sys-outline-variant); }
    .row-title { flex: 1; min-width: 0; font: var(--mat-sys-body-large); }
    .row-value { flex: none; font: var(--mat-sys-body-medium); color: var(--mat-sys-on-surface-variant); }
    .row-title { display: flex; flex-direction: column; gap: 2px; }
    .row-sub { font: var(--mat-sys-body-small); color: var(--mat-sys-on-surface-variant); }
    .chev { flex: none; color: var(--mat-sys-on-surface-variant); margin-right: -4px; }
    .row.danger .row-title { color: var(--mat-sys-error); }
    .tick { color: var(--mat-sys-primary); }

    /* M3's leading slot is a 24dp icon or a 40dp avatar. The coloured tile
       was neither — an iOS convention — and its colour carried no meaning,
       since primary and tertiary both resolve purple in this theme. */
    .lead { flex: none; color: var(--mat-sys-on-surface-variant); }
    .row.danger .lead { color: var(--mat-sys-error); }

    /* Controls */
    .field { display: flex; flex-direction: column; gap: 4px; }
    .field-head { display: flex; align-items: baseline; justify-content: space-between; }
    mat-slider { width: 100%; margin-inline: 0; }

    .times { display: flex; gap: 12px; }
    .times label { flex: 1; display: flex; flex-direction: column; gap: 4px; }
    .field-label { font: var(--mat-sys-label-small); color: var(--mat-sys-on-surface-variant); }

    input[type='time'] {
      height: 44px;
      padding: 0 12px;
      border: 1px solid var(--mat-sys-outline);
      border-radius: var(--mat-sys-corner-small);
      background: transparent;
      color: var(--mat-sys-on-surface);
      font: var(--mat-sys-body-medium);
      color-scheme: dark;
    }

    .edit {
      position: relative;
      flex: none;
      display: grid;
      place-items: center;
      height: 32px;
      padding: 0 12px;
      border-radius: var(--mat-sys-corner-full);
      background: var(--mat-sys-surface-container-highest);
      color: var(--mat-sys-on-surface);
      font: var(--mat-sys-label-large);
    }

    .date-input {
      position: absolute;
      inset: 0;
      width: 100%;
      height: 100%;
      border: none;
      background: transparent;
      color: transparent;
      opacity: 0;
      cursor: pointer;
    }

    .chips { display: flex; flex-wrap: wrap; gap: 8px; }

    .chip {
      display: flex;
      align-items: center;
      gap: 6px;
      height: 36px;
      padding: 0 14px;
      border: 1px solid var(--mat-sys-outline-variant);
      border-radius: var(--mat-sys-corner-full);
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

    .chip:disabled { opacity: .4; cursor: default; }
    .chip mat-icon { font-size: 18px; width: 18px; height: 18px; }

    .swatch {
      width: 36px;
      height: 36px;
      flex: none;
      border-radius: 10px;
      box-shadow: inset 0 0 0 1px rgb(255 255 255 / .15);
    }

    .dots { display: flex; gap: 16px; }

    .dot {
      display: grid;
      place-items: center;
      width: 44px;
      height: 44px;
      border: none;
      border-radius: 50%;
      cursor: pointer;
    }

    .dot.on { box-shadow: 0 0 0 3px var(--mat-sys-surface-container), 0 0 0 5px var(--mat-sys-on-surface); }
    .dot mat-icon { color: #fff; font-size: 22px; width: 22px; height: 22px; }

    /* M3 switch */
    .switch {
      position: relative;
      flex: none;
      width: 52px;
      height: 32px;
      border: 2px solid var(--mat-sys-outline);
      border-radius: var(--mat-sys-corner-full);
      background: var(--mat-sys-surface-container-highest);
      transition: background 120ms, border-color 120ms;
    }

    .switch.on { border-color: transparent; background: var(--mat-sys-primary); }

    .knob {
      position: absolute;
      top: 50%;
      left: 6px;
      width: 16px;
      height: 16px;
      border-radius: 50%;
      background: var(--mat-sys-outline);
      transform: translateY(-50%);
      transition: left 120ms, width 120ms, height 120ms, background 120ms;
    }

    .switch.on .knob { left: 24px; width: 24px; height: 24px; background: var(--mat-sys-on-primary); }

    .socials { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 16px; }

    .social {
      height: 36px;
      padding: 0 16px;
      border: 1px solid var(--mat-sys-outline-variant);
      border-radius: var(--mat-sys-corner-full);
      background: transparent;
      color: var(--mat-sys-on-surface-variant);
      font: var(--mat-sys-label-large);
      cursor: pointer;
    }

    /* Fixed-hours cards */
    .card { margin-top: 12px; }
    .card:first-of-type { margin-top: 0; }
    .card-head { display: flex; align-items: center; gap: 12px; }
    .card-head > mat-icon { color: var(--mat-sys-primary); }
    .card-name { flex: 1; font: var(--mat-sys-title-medium); }
    .days { display: flex; gap: 4px; }

    .day {
      flex: 1;
      height: 40px;
      border: 1px solid var(--mat-sys-outline-variant);
      border-radius: var(--mat-sys-corner-full);
      background: transparent;
      color: var(--mat-sys-on-surface-variant);
      font: var(--mat-sys-label-large);
      cursor: pointer;
    }

    .day.on { border-color: transparent; background: var(--mat-sys-secondary-container); color: var(--mat-sys-on-secondary-container); }

    /* Prose only ever lives inside a sheet, in short passages. */
    .prose { margin: 0; font: var(--mat-sys-body-medium); color: var(--mat-sys-on-surface-variant); }
    .note-row { align-items: flex-start; padding: 14px 16px; }
    .note { margin: 12px 4px 0; font: var(--mat-sys-body-small); color: var(--mat-sys-on-surface-variant); }

    .foot {
      margin: 24px 4px 0;
      font: var(--mat-sys-label-small);
      color: var(--mat-sys-on-surface-variant);
      text-align: center;
    }

    /* Confirmation dialog */
    .scrim { position: absolute; inset: 0; z-index: 5; background: rgb(0 0 0 / .4); }

    .dialog {
      position: absolute;
      z-index: 6;
      left: 24px;
      right: 24px;
      top: 50%;
      transform: translateY(-50%);
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 8px;
      padding: 24px;
      border-radius: 28px;
      background: var(--mat-sys-surface-container-high);
      text-align: center;
    }

    .dialog-icon { color: var(--mat-sys-error); }
    .dialog-title { margin: 0; font: var(--mat-sys-headline-small); }
    .dialog-body { margin: 0; font: var(--mat-sys-body-medium); color: var(--mat-sys-on-surface-variant); }
    .dialog-actions { display: flex; justify-content: flex-end; gap: 8px; align-self: stretch; margin-top: 16px; }

    .text-btn {
      height: 40px;
      padding: 0 12px;
      border: none;
      border-radius: var(--mat-sys-corner-full);
      background: transparent;
      color: var(--mat-sys-primary);
      font: var(--mat-sys-label-large);
      cursor: pointer;
    }

    .text-btn.danger { color: var(--mat-sys-error); }
  `,

})
export class SettingsScreen {
  protected readonly store = inject(OnboardingStore);
  protected readonly study = inject(StudyStore);

  protected readonly page = signal<Page>('root');
  protected readonly confirmWipe = signal(false);

  protected readonly pack = PACK;
  protected readonly apps = BLOCKABLE_APPS;
  protected readonly accents = ACCENTS;
  protected readonly appearances = APPEARANCES;
  protected readonly presets = COMMITMENT_PRESETS;
  protected readonly dayInitials = DAY_INITIALS;
  protected readonly breaks = BREAKS;
  protected readonly paces = PACES;

  protected paceName(): string {
    return PACES.find((p) => p.id === this.store.revisionPace())?.label ?? '';
  }
  protected readonly socials = SOCIALS;
  protected readonly version = '0.4.0';
  protected readonly legal = LEGAL;

  protected readonly methodology = [
    ...PACK.meta.methodology,
    'Revision falls due after 3, 10, 30 and 60 days, pulled in or pushed out by how you said the last sitting went.',
    'Still remembered is a decay model over those due dates, not a measurement of your memory.',
  ];

  /** Chapters ticked, over the chapters still in the plan. */
  protected readonly coverage = computed(() => {
    const total = Math.max(1, this.study.rounds().total - this.store.parkedChapters().size);
    return Math.round((this.study.rounds().learned / total) * 100);
  });

  protected appearanceName(): string {
    return APPEARANCES.find((a) => a.id === this.store.appearance())?.label ?? '';
  }

  protected readonly notifications = [
    { id: 'plan', icon: 'sunny', label: 'Morning plan' },
    { id: 'due', icon: 'history', label: 'Revision due' },
    { id: 'idle', icon: 'notifications', label: 'Evening nudge' },
  ];

  private readonly notificationsOn = signal<ReadonlySet<string>>(new Set(['plan', 'due']));

  protected notificationOn(id: string): boolean { return this.notificationsOn().has(id); }

  protected toggleNotification(id: string): void {
    const next = new Set(this.notificationsOn());
    next.has(id) ? next.delete(id) : next.add(id);
    this.notificationsOn.set(next);
  }

  protected readonly loaded = computed(() =>
    this.study.sessions().some((s) => s.id.startsWith('demo-')),
  );

  /* ---- Plan ------------------------------------------------------------ */

  protected dateValue(): string {
    const d = this.store.targetDate();
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
  }

  protected setDate(value: string): void {
    const [y, m, d] = value.split('-').map(Number);
    if (!Number.isFinite(y)) return;
    const picked = new Date(y, m - 1, d);
    if (picked > startOfToday()) this.store.targetDate.set(picked);
  }

  protected freeLabel(): string {
    const minutes = this.store.freeMinutesOn(3);
    const h = Math.floor(minutes / 60);
    const m = minutes % 60;
    return m === 0 ? `${h}h` : `${h}h ${m}m`;
  }

  protected awake(): string {
    return `${clockLabel(this.store.wakeMinute())} to ${clockLabel(this.store.sleepMinute())}`;
  }

  protected coachingName(): string {
    return COACHINGS.find((c) => c.id === this.store.coachingId())?.label ?? 'None';
  }

  protected value(minute: number): string { return timeValue(minute); }
  protected parse(value: string): number { return minutesFromTimeValue(value); }

  protected icon(c: Commitment): string {
    return COMMITMENT_PRESETS.find((p) => p.kind === c.kind)?.icon ?? 'schedule';
  }

  protected range(c: Commitment): string {
    return `${clockLabel(c.startMinute)}–${clockLabel(c.startMinute + c.minutes)}`;
  }

  protected setStart(c: Commitment, value: string): void {
    const start = minutesFromTimeValue(value);
    const end = c.startMinute + c.minutes;
    this.store.updateCommitment(c.id, { startMinute: start, minutes: Math.max(30, end - start) });
  }

  protected setEnd(c: Commitment, value: string): void {
    const end = minutesFromTimeValue(value);
    this.store.updateCommitment(c.id, { minutes: Math.max(30, end - c.startMinute) });
  }

  /* ---- Data ------------------------------------------------------------ */

  protected storageLabel(): string {
    let bytes = 0;
    for (const key of Object.keys(localStorage)) {
      if (key.startsWith('steadyline.')) bytes += localStorage.getItem(key)?.length ?? 0;
    }
    return bytes < 1024 ? `${bytes} B` : `${Math.round(bytes / 1024)} KB`;
  }

  /** Everything the app holds, in one file the user keeps. */
  protected exportData(): void {
    const data: Record<string, unknown> = {};
    for (const key of Object.keys(localStorage)) {
      if (!key.startsWith('steadyline.')) continue;
      try {
        data[key.replace('steadyline.', '')] = JSON.parse(localStorage.getItem(key)!);
      } catch {
        data[key.replace('steadyline.', '')] = localStorage.getItem(key);
      }
    }
    const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `steadyline-${new Date().toISOString().slice(0, 10)}.json`;
    a.click();
    URL.revokeObjectURL(url);
  }

  protected wipe(): void {
    for (const key of Object.keys(localStorage)) {
      if (key.startsWith('steadyline.')) localStorage.removeItem(key);
    }
    location.reload();
  }

  protected load(): void { loadDemo(this.store, this.study); }
  protected clear(): void { clearDemo(this.store, this.study); }
}
