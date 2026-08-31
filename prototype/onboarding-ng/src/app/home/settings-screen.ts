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
import { clearDemo, loadDemo } from '../study/demo-data';

/** Where a row leads, when it leads somewhere. */
type Page = 'root' | 'fixed' | 'legal' | 'about';

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
          <!-- Plan -->
          <h2 class="group">Plan</h2>

          <div class="item">
            <mat-icon class="lead">school</mat-icon>
            <span class="text">
              <span class="title">Exam</span>
              <span class="sub">{{ pack.displayName }}</span>
            </span>
            <span class="trail">{{ store.days() }} days</span>
          </div>

          <label class="item">
            <mat-icon class="lead">event</mat-icon>
            <span class="text">
              <span class="title">Exam date</span>
              <span class="sub">{{ store.targetDate() | date: 'EEEE, d MMMM y' }}</span>
            </span>
            <span class="edit">
              Change
              <input class="date-input" type="date" [value]="dateValue()"
                     (change)="setDate($any($event.target).value)" />
            </span>
          </label>

          <div class="item stacked">
            <span class="row-head">
              <mat-icon class="lead">schedule</mat-icon>
              <span class="text">
                <span class="title">Study hours</span>
                <span class="sub">{{ store.weekdayHours() }}h weekdays · {{ store.weekendHours() }}h weekends</span>
              </span>
            </span>

            <div class="slider">
              <span class="slider-label">Weekdays</span>
              <mat-slider min="1" max="14" step="0.5" discrete>
                <input matSliderThumb [ngModel]="store.weekdayHours()"
                       (ngModelChange)="store.weekdayHours.set($event)" />
              </mat-slider>
            </div>

            <div class="slider">
              <span class="slider-label">Weekends</span>
              <mat-slider min="1" max="16" step="0.5" discrete>
                <input matSliderThumb [ngModel]="store.weekendHours()"
                       (ngModelChange)="store.weekendHours.set($event)" />
              </mat-slider>
            </div>
          </div>

          <button matRipple class="item" (click)="page.set('fixed')">
            <mat-icon class="lead">event_busy</mat-icon>
            <span class="text">
              <span class="title">Fixed hours</span>
              <span class="sub">{{ store.commitments().length }} blocks · {{ freeLabel() }} free on a weekday</span>
            </span>
            <mat-icon class="chevron">chevron_right</mat-icon>
          </button>

          <div class="item stacked">
            <span class="row-head">
              <mat-icon class="lead">bedtime</mat-icon>
              <span class="text">
                <span class="title">Awake between</span>
                <span class="sub">{{ awake() }}</span>
              </span>
            </span>
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

          <div class="item stacked">
            <span class="row-head">
              <mat-icon class="lead">coffee</mat-icon>
              <span class="text">
                <span class="title">Break between sittings</span>
                <span class="sub">{{ store.breakMinutes() }} minutes</span>
              </span>
            </span>
            <div class="chips">
              @for (m of breaks; track m) {
                <button matRipple class="chip" [class.on]="store.breakMinutes() === m"
                        (click)="store.breakMinutes.set(m)">{{ m }}m</button>
              }
            </div>
          </div>

          <div class="item">
            <mat-icon class="lead">groups</mat-icon>
            <span class="text">
              <span class="title">Coaching</span>
              <span class="sub">{{ coachingName() }}</span>
            </span>
          </div>

          <!-- Appearance -->
          <h2 class="group">Appearance</h2>

          <div class="item stacked">
            <span class="row-head">
              <mat-icon class="lead">contrast</mat-icon>
              <span class="text"><span class="title">Theme</span></span>
            </span>
            <div class="chips">
              @for (a of appearances; track a.id) {
                <button matRipple class="chip" [class.on]="store.appearance() === a.id"
                        (click)="store.appearance.set(a.id)">
                  <span class="swatch" [style.background]="a.swatch"></span>
                  {{ a.label }}
                </button>
              }
            </div>
          </div>

          <div class="item stacked">
            <span class="row-head">
              <mat-icon class="lead">palette</mat-icon>
              <span class="text"><span class="title">Accent</span></span>
            </span>
            <div class="dots">
              @for (a of accents; track a.id) {
                <button class="dot" [class.on]="store.accent() === a.id"
                        [style.background]="a.swatch"
                        [attr.aria-label]="a.label"
                        (click)="store.accent.set(a.id)">
                  @if (store.accent() === a.id) { <mat-icon class="filled">check</mat-icon> }
                </button>
              }
            </div>
          </div>

          <!-- Focus -->
          <h2 class="group">Focus</h2>

          <button matRipple class="item" (click)="store.blockApps.set(!store.blockApps())">
            <mat-icon class="lead" [class.filled]="store.blockApps()">
              {{ store.blockApps() ? 'lock' : 'lock_open' }}
            </mat-icon>
            <span class="text">
              <span class="title">Block apps during a session</span>
              <span class="sub">{{ store.blockApps() ? 'Released when the timer stops' : 'Off' }}</span>
            </span>
            <span class="switch" [class.on]="store.blockApps()"><span class="knob"></span></span>
          </button>

          @if (store.blockApps()) {
            <div class="item stacked">
              <div class="chips">
                @for (a of apps; track a.id) {
                  <button matRipple class="chip" [class.on]="store.blockedApps().has(a.id)"
                          (click)="store.toggleBlockedApp(a.id)">
                    <mat-icon>{{ a.icon }}</mat-icon>
                    {{ a.label }}
                  </button>
                }
              </div>
            </div>
          }

          <!-- Notifications -->
          <h2 class="group">Reminders</h2>

          @for (n of notifications; track n.id) {
            <button matRipple class="item" (click)="toggleNotification(n.id)">
              <mat-icon class="lead">{{ n.icon }}</mat-icon>
              <span class="text">
                <span class="title">{{ n.label }}</span>
                <span class="sub">{{ n.sub }}</span>
              </span>
              <span class="switch" [class.on]="notificationOn(n.id)"><span class="knob"></span></span>
            </button>
          }

          <!-- Data -->
          <h2 class="group">Your data</h2>

          <div class="item">
            <mat-icon class="lead">smartphone</mat-icon>
            <span class="text">
              <span class="title">Stored on this device</span>
              <span class="sub">{{ study.sessions().length }} sittings · {{ storageLabel() }}</span>
            </span>
          </div>

          <button matRipple class="item" (click)="exportData()">
            <mat-icon class="lead">download</mat-icon>
            <span class="text">
              <span class="title">Export my data</span>
              <span class="sub">One JSON file, everything the app holds</span>
            </span>
            <mat-icon class="chevron">chevron_right</mat-icon>
          </button>

          <button matRipple class="item danger" (click)="confirmWipe.set(true)">
            <mat-icon class="lead">delete_forever</mat-icon>
            <span class="text">
              <span class="title">Delete everything</span>
              <span class="sub">Cannot be undone</span>
            </span>
          </button>

          <!-- Legal -->
          <h2 class="group">Privacy &amp; terms</h2>

          <button matRipple class="item" (click)="page.set('legal')">
            <mat-icon class="lead">shield</mat-icon>
            <span class="text">
              <span class="title">Privacy policy</span>
              <span class="sub">What is collected, and what isn't</span>
            </span>
            <mat-icon class="chevron">chevron_right</mat-icon>
          </button>

          <button matRipple class="item" (click)="page.set('legal')">
            <mat-icon class="lead">gavel</mat-icon>
            <span class="text"><span class="title">Terms of use</span></span>
            <mat-icon class="chevron">chevron_right</mat-icon>
          </button>

          <!-- About -->
          <h2 class="group">About</h2>

          <button matRipple class="item" (click)="page.set('about')">
            <mat-icon class="lead">info</mat-icon>
            <span class="text">
              <span class="title">Where the numbers come from</span>
              <span class="sub">Sources and estimates</span>
            </span>
            <mat-icon class="chevron">chevron_right</mat-icon>
          </button>

          <div class="item">
            <mat-icon class="lead">verified</mat-icon>
            <span class="text">
              <span class="title">Version</span>
              <span class="sub">{{ version }} · prototype</span>
            </span>
          </div>

          <div class="socials">
            @for (s of socials; track s.id) {
              <button matRipple class="social">
                {{ s.label }}
                <mat-icon>open_in_new</mat-icon>
              </button>
            }
          </div>

          <!-- Developer -->
          <h2 class="group">Developer</h2>

          <button matRipple class="item" (click)="load()">
            <mat-icon class="lead">science</mat-icon>
            <span class="text">
              <span class="title">Load demo history</span>
              <span class="sub">{{ loaded() ? 'Loaded — tap to regenerate' : 'Three weeks of fabricated data' }}</span>
            </span>
          </button>

          <button matRipple class="item" (click)="clear()">
            <mat-icon class="lead">restart_alt</mat-icon>
            <span class="text">
              <span class="title">Reset to a fresh account</span>
              <span class="sub">Clears study history, keeps the plan</span>
            </span>
          </button>

          <p class="foot">Steadyline · built for one exam at a time</p>
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
            <div class="card">
              <div class="card-head">
                <mat-icon>{{ icon(c) }}</mat-icon>
                <span class="card-name">{{ c.label }}</span>
                <span class="card-span">{{ range(c) }}</span>
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
            <p class="empty">Nothing fixed — the whole day is yours.</p>
          }

          <h2 class="group">Add</h2>
          <div class="chips">
            @for (p of presets; track p.kind) {
              <button matRipple class="chip" (click)="store.addCommitment(p)">
                <mat-icon>{{ p.icon }}</mat-icon>
                {{ p.label }}
              </button>
            }
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

        <div class="scroll prose">
          <h3>What this app stores</h3>
          <p>
            Everything — your plan, your sittings, your ticks — is kept in this device's own
            storage. There is no account, no server, and nothing is uploaded.
          </p>

          <h3>What it collects</h3>
          <p>
            No analytics, no advertising identifiers, no crash reporting, no contacts, no
            location. The app makes no network requests of its own.
          </p>

          <h3>Who it is shared with</h3>
          <p>Nobody. There is no third party to share it with.</p>

          <h3>Deleting it</h3>
          <p>
            "Delete everything" in Settings removes all of it immediately. Uninstalling the app
            does the same.
          </p>

          <h3>Terms of use</h3>
          <p>
            The syllabus, hour estimates and revision schedule are planning aids, not guarantees
            of a result. Chapter hours are estimates, not measurements. Always check your exam's
            official syllabus and dates with the conducting body.
          </p>

          <p class="stamp">Last updated 31 August 2026</p>
        </div>
      }

      @case ('about') {
        <header class="bar">
          <button matRipple class="icon-btn" (click)="page.set('root')" aria-label="Back">
            <mat-icon>arrow_back</mat-icon>
          </button>
          <h1 class="bar-title small">Where the numbers come from</h1>
        </header>

        <div class="scroll prose">
          <ul>
            @for (line of pack.meta.methodology; track line) { <li>{{ line }}</li> }
            <li>
              Revision falls due after 3, 10, 30 and 60 days, pulled in or pushed out by how you
              said the last sitting went.
            </li>
            <li>
              "Still remembered" is a decay model over those due dates, not a measurement of your
              memory.
            </li>
          </ul>
          <p class="stamp">{{ pack.meta.source }}</p>
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

    /* M3 top app bar: 64dp, title-large, back in the 48dp leading slot. */
    /* A hairline under the bar: without it, a row scrolling past the title
       reads as text clipped by the header. */
    .bar {
      flex: none;
      display: flex;
      align-items: center;
      gap: 4px;
      height: 64px;
      padding: 0 4px 0 16px;
      border-bottom: 1px solid var(--mat-sys-outline-variant);
      background: var(--mat-sys-surface);
    }
    .bar-title { margin: 0; font: var(--mat-sys-headline-small); }
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

    .scroll { flex: 1; min-height: 0; overflow-y: auto; padding-bottom: 32px; }

    /* Subheader, not a card title: this is a list, M3 style. */
    .group {
      margin: 20px 0 4px;
      padding: 0 16px;
      font: var(--mat-sys-title-small);
      color: var(--mat-sys-primary);
    }

    .group:first-child { margin-top: 8px; }

    /* One list item shape for every row on the screen. */
    .item {
      width: 100%;
      display: flex;
      align-items: center;
      gap: 16px;
      min-height: 56px;
      padding: 12px 16px;
      border: none;
      background: transparent;
      color: var(--mat-sys-on-surface);
      text-align: left;
      font: inherit;
    }

    button.item, label.item { cursor: pointer; }
    /* Stacked controls line up with the row's text, not with its icon —
       otherwise every group has two competing left edges. */
    .item.stacked { flex-direction: column; align-items: stretch; gap: 12px; }
    .item.stacked > :not(.row-head) { padding-left: 40px; }
    .item.danger .title, .item.danger .lead { color: var(--mat-sys-error); }

    .row-head { display: flex; align-items: center; gap: 16px; }
    .lead { flex: none; color: var(--mat-sys-on-surface-variant); }
    .text { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 2px; }
    .title { font: var(--mat-sys-body-large); }
    .sub { font: var(--mat-sys-body-small); color: var(--mat-sys-on-surface-variant); }
    .trail { flex: none; font: var(--mat-sys-label-large); color: var(--mat-sys-on-surface-variant); }
    .chevron { flex: none; color: var(--mat-sys-on-surface-variant); }

    /* Controls */
    .slider { display: flex; flex-direction: column; gap: 2px; }
    .slider-label { font: var(--mat-sys-label-medium); color: var(--mat-sys-on-surface-variant); }
    mat-slider { width: 100%; margin-inline: 0; }

    .times { display: flex; gap: 8px; }
    .times label { flex: 1; display: flex; flex-direction: column; gap: 4px; }
    .field-label { font: var(--mat-sys-label-small); color: var(--mat-sys-on-surface-variant); }

    input[type='time'], .date-input {
      height: 40px;
      padding: 0 12px;
      border: 1px solid var(--mat-sys-outline);
      border-radius: var(--mat-sys-corner-small);
      background: transparent;
      color: var(--mat-sys-on-surface);
      font: var(--mat-sys-body-medium);
      color-scheme: dark;
    }

    /* A labelled target, with the native picker sitting invisibly over it. */
    .edit {
      position: relative;
      flex: none;
      display: grid;
      place-items: center;
      height: 32px;
      padding: 0 12px;
      border-radius: var(--mat-sys-corner-full);
      background: var(--mat-sys-secondary-container);
      color: var(--mat-sys-on-secondary-container);
      font: var(--mat-sys-label-large);
    }

    .date-input {
      position: absolute;
      inset: 0;
      width: 100%;
      height: 100%;
      padding: 0;
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
      height: 32px;
      padding: 0 12px;
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

    .chip mat-icon { font-size: 18px; width: 18px; height: 18px; }
    .swatch { width: 14px; height: 14px; border-radius: 50%; box-shadow: inset 0 0 0 1px rgb(255 255 255 / .2); }

    .dots { display: flex; gap: 12px; }

    .dot {
      display: grid;
      place-items: center;
      width: 40px;
      height: 40px;
      border: none;
      border-radius: 50%;
      cursor: pointer;
    }

    .dot.on { box-shadow: 0 0 0 2px var(--mat-sys-surface), 0 0 0 4px var(--mat-sys-on-surface); }
    .dot mat-icon { color: #fff; font-size: 20px; width: 20px; height: 20px; }

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

    .socials { display: flex; flex-wrap: wrap; gap: 8px; padding: 8px 16px 0; }

    /* Named, not glyphed: Material Symbols has no brand marks, and a hash
       sign is not a recognisable X. */
    .social {
      display: flex;
      align-items: center;
      gap: 6px;
      height: 32px;
      padding: 0 12px;
      border: 1px solid var(--mat-sys-outline-variant);
      border-radius: var(--mat-sys-corner-full);
      background: transparent;
      color: var(--mat-sys-on-surface-variant);
      font: var(--mat-sys-label-large);
      cursor: pointer;
    }

    .social mat-icon { font-size: 16px; width: 16px; height: 16px; }

    .foot {
      margin: 32px 16px 0;
      font: var(--mat-sys-label-small);
      color: var(--mat-sys-on-surface-variant);
      text-align: center;
    }

    /* Fixed-hours editor */
    .card {
      display: flex;
      flex-direction: column;
      gap: 12px;
      margin: 8px 16px;
      padding: 12px 16px 16px;
      border-radius: var(--mat-sys-corner-large);
      background: var(--mat-sys-surface-container);
    }

    .card-head { display: flex; align-items: center; gap: 8px; }
    .card-head > mat-icon { color: var(--mat-sys-primary); }
    .card-name { flex: 1; font: var(--mat-sys-title-medium); }
    .card-span { font: var(--mat-sys-label-medium); color: var(--mat-sys-on-surface-variant); }
    .days { display: flex; gap: 4px; }

    .day {
      flex: 1;
      height: 36px;
      border: 1px solid var(--mat-sys-outline-variant);
      border-radius: var(--mat-sys-corner-full);
      background: transparent;
      color: var(--mat-sys-on-surface-variant);
      font: var(--mat-sys-label-large);
      cursor: pointer;
    }

    .day.on { border-color: transparent; background: var(--mat-sys-secondary-container); color: var(--mat-sys-on-secondary-container); }
    .empty { margin: 16px; font: var(--mat-sys-body-medium); color: var(--mat-sys-on-surface-variant); }
    .card + .group, .empty + .group { margin-top: 24px; }
    .scroll > .chips { padding: 0 16px; }

    /* Prose pages */
    .prose { padding: 0 16px 32px; }
    .prose h3 { margin: 24px 0 4px; font: var(--mat-sys-title-small); color: var(--mat-sys-on-surface); }
    .prose p { margin: 0; font: var(--mat-sys-body-medium); color: var(--mat-sys-on-surface-variant); }

    .prose ul {
      margin: 8px 0 0;
      padding-left: 20px;
      display: flex;
      flex-direction: column;
      gap: 12px;
      font: var(--mat-sys-body-medium);
      color: var(--mat-sys-on-surface-variant);
    }

    .stamp { margin-top: 24px !important; font: var(--mat-sys-label-small) !important; }

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
  protected readonly socials = SOCIALS;
  protected readonly version = '0.4.0';

  protected readonly notifications = [
    { id: 'plan', icon: 'sunny', label: "Morning plan", sub: 'What today holds, at 7am' },
    { id: 'due', icon: 'history', label: 'Revision due', sub: 'When chapters fall due' },
    { id: 'idle', icon: 'notifications', label: 'Nudge if nothing logged', sub: 'Once, in the evening' },
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
