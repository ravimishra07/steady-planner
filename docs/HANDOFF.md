# Handoff — NEET Planner V1 (2026-10-07)

**Repo:** `~/Desktop/study-planner` · **Branch:** `ravi/prototype-port-ready` (local only, nothing pushed)
**Contract:** [docs/PRODUCTION_V1_PLAN.md](PRODUCTION_V1_PLAN.md) — every item, its priority and acceptance criteria.
**Rules for whoever continues:** follow `ARCHITECTURE.md`. Never use an emulator or adb.

Build command:

```sh
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
cd android && ./gradlew --no-daemon :domain:test testDebugUnitTest lintDebug assembleDebug
```

## Standing instructions from Ravi

- **Do not write new test cases.** Ravi tests on device himself. Existing
  tests may stay and must still compile.
- **Support line / helpline (§10.5) is removed.** Do not build it.
- App ID stays `com.exam.assistant`.
- Analytics: Firebase Analytics + Crashlytics.
- Pro is **₹59 lifetime**, one-time. No subscription, no trial.
- Commit locally only. Ravi pushes.

## Done (committed, build green when committed)

| Commit(s) | Phase | What |
|---|---|---|
| `0e2b992` `5b352a9` `3805bce` `3803c16` | A1 | Onboarding refactor committed. Focus Lock and its risky permissions removed (merged manifest has only `POST_NOTIFICATIONS`). Dead code and 146 unused strings deleted |
| `01653b0` | A2 | `domain/Metrics.kt` is the single source for every number shown. Studied time comes from focused seconds. One revision schedule (`RevisionSchedule.kt`); the outcome is applied once; overdue revisions stay visible. Room 4→5 |
| `c815b5d` | docs | Locked decisions; PYQ draft dataset `docs/research/pyq_weightage_*` |
| `63cd0d8` `1ce7cd3` `c41a164` `611024b` `636cc4a` `5b6e9f5` | B | DEADLINE/STEADY plan modes. Exam date kept separate from finish-by (RemoteConfig `neet_exam_date`, default 2027-05-02, "expected, not announced"). Personal calibration. PYQ priority bands (`tools/build_pyq_priority.py` → `assets/neet_pyq_priority.json`, all 79 chapters). Planner schedules Learn + Practice + Revision (≤40% of day) + mocks in a revision phase. No blocks in the past. `PlanFit` honest feasibility with working fix options. Missed-blocks prompt. Room 5→6 |
| `aefb3c3` | C item 1 | Mode picker in onboarding, editable exam date, Settings → Plan |

At `5b6e9f5`: build, lint and unit tests verified green by the lead (161 tests, 0 failures).

## In progress — uncommitted, unverified (Phase C item 2)

The agent was stopped mid-task. Its edits sit uncommitted in 17 files (+397/−33).

**What it was building:** §3.3 starting point — the Class 11 / Class 12 /
Dropper step and bulk "mark finished chapters".

**Files:**
- `ExamAttempt` / `ExamAttemptEntity`
- `PrepTrackerDatabase` (Room 6→7, `schemas/.../7.json`)
- `RollingPlan`, `RollingPlanRepository`
- onboarding `OnboardingViewModel`, `OnboardingScreen`, `steps/OnboardingParitySteps.kt`
- new `model/FinishedChapters.kt`
- strings
- `libs.versions.toml` and `core/data/build.gradle.kts` (test deps)
- new tests: `MigrationTest.kt`, `StartingPointTest.kt`

**Status:** the UI step and screen wiring were not finished. It is not known
whether it compiles.

**Next step:** run the build.
- If it's close, finish it.
- If not: `git stash` (or `git checkout -- . && git clean -fd android/`) and redo §3.3.

Leave `.claude/launch.json` and `android/gradle.properties` alone; those are
pre-existing local changes.

## Left to build (in order)

**Phase C (remaining)** — plan §3.3, §5.1, §5.2, §5.4, §5.5, §5.6

1. Starting point: finish the item above.
2. Per-chapter stages: Learned · NCERT · Practice/DPP · PYQ · Revised ×N.
   Labels are editable. Coverage = Learned. Migrate old leaf progress. Prompt
   "Mark chapter learned?" after a Learn session.
3. Rename chapters to coaching names; park chapters; keep progress by stable ID
   across syllabus versions.
4. **Coaching backlog tracker (P0)**:
   - per-chapter lecture/DPP counts with bulk tick;
   - batch position via "Taught in class today";
   - backlog by subject;
   - live/backlog split (60/40 default) in the planner;
   - backlog-clear date.
5. "Start fresh from today":
   - 1 missed day → rebalance prompt; 2 or more → start fresh;
   - Insights leads with progress, not hours;
   - neutral streak copy.
6. "What did I study" day view.

**Phase D** — §6, §7.2, §8

- Timer reliable with the screen off, app swiped away and reboot. End sound and
  vibration. Foreground notification with time left.
- Manual time entry; edit or delete past sessions; custom length 5–180 min.
- Engine revision blocks on Today (done in B; verify display).
- Mock/test log (per-subject scores, /720, trend graph).
- Lightweight mistake log feeding revision.

**Phase E** — §10.1, §10.3, §10.4

- Opt-in daily reminder. Ask for `POST_NOTIFICATIONS` in context.
- Glance widget: small (days to exam + coverage); medium (next block + Start
  deep link).
- JSON export/import through the share sheet (free).
- Decide whether to use Android Auto Backup, and make the privacy copy match.

**Phase F** — §11.2, §11.3

- Play Billing:
  - one non-consumable "pro_lifetime" at ₹59;
  - entitlement cached offline;
  - restore on launch and from Settings;
  - acknowledge and handle refunds.
- Paywall on Pro features only. Never during onboarding or Focus.
- Firebase Analytics: events listed in §11.3, plus an opt-out toggle.
- Crashlytics.
- Privacy policy and Data safety text matching the actual behaviour.

**Phase H** — §11.4, §12

- Signed AAB with R8, Baseline Profile, size under 15 MB. The debug APK is
  ~20 MB today; check the release size.
- Confirm Play's current target API (the app is at 35).
- Store listing in English and Hindi.
- Internal → closed (12+ testers, 14 days; confirm the current rule) →
  staged production rollout.

## Ravi only

- Device testing:
  - upgrade from the old Firebase build (Room 4→7 migrations never run on a
    device);
  - every new screen from B and C (Today fit banner, missed prompt, onboarding
    choices, mode picker);
  - timer with the screen off on an aggressive-OEM phone;
  - offline use.
- Play Console app, the `pro_lifetime` ₹59 product, license testers.
- Upload keystore plus 2 backups.
- `google-services.json` for Analytics/Crashlytics.
- Hosting the privacy policy.
- 10 student interviews (`docs/research/neet-questions.md` §3–§7), before the
  Phase C/D UI is frozen.

## Research inputs (summary)

- **Play reviews (~33K):**
  - students punish paywalled setup, lost data, timers that break, ads, and
    outdated syllabi;
  - they reward free / no ads, a preloaded syllabus and revision reminders;
  - competitors: TrackIt 500K+ (₹99/mo, ₹399/yr, ₹699 lifetime), MemoNeet 1M+.
- **Reddit (~1,600 posts):**
  - backlog is counted in coaching **lectures/DPPs**;
  - the plan → fail → guilt → quit cycle;
  - revision forgetting;
  - lectures watched without practice.
- **NEET 2027:** date not announced; exam moves to CBT.
- **PYQ data:** sources disagree by 2 or more questions on 99 of 392
  chapter-year comparisons. Show only bands (High/Med/Low), never exact counts.
