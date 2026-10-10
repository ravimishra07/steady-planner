// shell.js — port of the :app module's UI entry: SteadylineApp + MainActivity (+ StartupViewModel), Routes.kt,
// SteadylineBottomBar.kt (Tab) and SteadylineNavHost.kt. index.html loads this module.
//
// Web stand-ins:
//  - The phone: a 412×915dp frame (full screen under 500px wide) with a fake 24dp status bar and 24dp gesture bar.
//    The app draws edge-to-edge behind both (enableEdgeToEdge); status-bar icons follow bg luminance.
//  - System splash: #0A0A0F with the adaptive launcher icon until StartupViewModel is ready.
//  - Intents (notification / widget taps): window.__app.openRequest('focus'|'today'|'paywall', blockId?)
//    does what MainActivity.handleOpenRequest does.
//  - BuildConfig.VERSION_NAME = '0.1.2' (android/app/build.gradle.kts), BuildConfig.DEBUG = true (debug build).
//  - Feature screens are lazy `import('../features/<f>/<File>.js')`; a missing module shows a placeholder
//    with the route name. File names follow the Kotlin file that declares the route composable
//    (HomeRoute lives in HomeScreen.kt → features/home/HomeScreen.js).
//
// Exports: Route, Tab, StartupState, StartupViewModel, SteadylineBottomBar, SteadylineNavHost, main()
// Debug:   window.__app = { nav, container, startup, openRequest }
import { h } from '../platform/h.js';
import { MutableStateFlow, combine } from '../platform/flow.js';
import {
  createNavController, NavHost, observe, installSystemBack, captureScope, LaunchedEffect,
} from '../platform/nav.js';
import { drawable } from '../platform/icons.js';
import { applyTheme, AccentPalette, BackgroundAppearance, appColorsFlow } from '../design/colors.js';
import {
  NavigationBar, NavigationBarItem, Icon, Text, Column,
} from '../design/components.js';
import { container as appContainer } from '../data/container.js';
import { ConfigKeys, neetExamDate, neetExamDateOfficial } from '../data/RemoteConfig.js';
import { FocusSession, FocusStatus, FocusBlockRef } from '../domain/FocusSession.js';
import { BlockTag } from '../domain/TodaySchedule.js';
import { toEpochDay, ofEpochDay, today as localToday, nowMs } from '../domain/time.js';
import { formatAndroid } from '../data/common.js';
import { PaywallHost, preloadPaywallSheet } from './paywall.js';

export const BuildConfig = Object.freeze({ VERSION_NAME: '0.1.2', DEBUG: true });

// app/src/main/res/values/strings.xml (the strings the shell uses)
const R = {
  string: {
    app_name: 'NEET Planner',
    tab_today: 'Today', tab_syllabus: 'Syllabus', tab_focus: 'Focus', tab_progress: 'Progress', tab_settings: 'Settings',
    focus_revision_title: 'Revision · %1$s',
  },
};

// =============================================================================================
// Routes.kt
// =============================================================================================
const NO_DAY = -(2 ** 63); // Long.MIN_VALUE (exactly representable as a double)
export const Route = Object.freeze({
  Onboarding: { path: 'onboarding' },
  Home: { path: 'home' },
  Syllabus: { path: 'syllabus' },
  Organise: { path: 'organise' },
  Focus: { path: 'focus' },
  Progress: { path: 'progress' },
  Settings: { path: 'settings' },
  Appearance: { path: 'settings/appearance' },
  SettingsDetail: { path: 'settings/detail' },
  Policy: { path: 'policy/{policyId}' },
  /** "What did I study" for one day; the argument is the date's epoch day. */
  DayLog: { ARG: 'epochDay', path: 'day/{epochDay}', pathFor: (date) => `day/${toEpochDay(date)}` },
  /** Log an offline session, or edit one when ARG_SESSION is set. */
  LogSession: {
    ARG_SESSION: 'sessionId', ARG_DAY: 'epochDay',
    path: 'log-session?sessionId={sessionId}&epochDay={epochDay}',
    newOn: (date) => `log-session?epochDay=${date != null ? toEpochDay(date) : NO_DAY}`,
    edit: (sessionId) => `log-session?sessionId=${encodeURIComponent(sessionId)}`,
    NO_DAY,
  },
  /** The mock and test log. */
  Tests: { path: 'tests' },
  /** Add a mock, or edit one when ARG is set. */
  MockForm: { ARG: 'mockId', path: 'mock-form?mockId={mockId}', new: 'mock-form', edit: (mockId) => `mock-form?mockId=${encodeURIComponent(mockId)}` },
});

// =============================================================================================
// SteadylineBottomBar.kt
// =============================================================================================
/** The bottom bar. Order here is order on screen. */
const TabEntries = {
  Home: { name: 'Home', route: Route.Home, labelRes: 'tab_today', selectedIcon: 'Filled.Home', unselectedIcon: 'Outlined.Home' },
  Syllabus: { name: 'Syllabus', route: Route.Syllabus, labelRes: 'tab_syllabus', selectedIcon: 'AutoMirrored.Filled.MenuBook', unselectedIcon: 'AutoMirrored.Outlined.MenuBook' },
  Focus: { name: 'Focus', route: Route.Focus, labelRes: 'tab_focus', selectedIcon: 'Filled.Timer', unselectedIcon: 'Outlined.Timer' },
  Progress: { name: 'Progress', route: Route.Progress, labelRes: 'tab_progress', selectedIcon: 'Filled.BarChart', unselectedIcon: 'Outlined.BarChart' },
  Settings: { name: 'Settings', route: Route.Settings, labelRes: 'tab_settings', selectedIcon: 'Filled.Settings', unselectedIcon: 'Outlined.Settings' },
};
export const Tab = Object.freeze({ ...TabEntries, entries: Object.freeze(Object.values(TabEntries)) });

/** SteadylineBottomBar({ selected: Tab, onSelect(tab) }) */
export function SteadylineBottomBar({ selected, onSelect }) {
  return NavigationBar({
    containerColor: 'var(--c-tabBg)',
    contentColor: 'var(--c-text)',
    children: Tab.entries.map((tab) => {
      const isSelected = tab === selected;
      return NavigationBarItem({
        selected: isSelected,
        onClick: () => onSelect(tab),
        icon: Icon({ imageVector: isSelected ? tab.selectedIcon : tab.unselectedIcon, contentDescription: R.string[tab.labelRes] }),
        label: Text({ text: R.string[tab.labelRes], style: 'labelMedium' }),
        colors: {
          selectedIconColor: 'var(--c-tabSelected)',
          selectedTextColor: 'var(--c-tabSelected)',
          unselectedIconColor: 'var(--c-tabUnselected)',
          unselectedTextColor: 'var(--c-tabUnselected)',
          indicatorColor: 'var(--c-selectionContainer)',
        },
      });
    }),
  });
}

// =============================================================================================
// StartupViewModel.kt
// =============================================================================================
export const StartupState = (o = {}) => Object.freeze({
  ready: false, background: BackgroundAppearance.Default, palette: AccentPalette.Default, hasPlan: false, ...o,
});
const STARTUP_TIMEOUT_MS = 5_000;

export class StartupViewModel {
  constructor(settings, attemptRepository, remoteConfig) {
    this.settings = settings;
    this._state = new MutableStateFlow(StartupState());
    this.state = this._state.asStateFlow();
    const work = (async () => {
      const [background, palette, hasPlan] = await Promise.all([
        settings.backgroundAppearanceOnce(), settings.accentPaletteOnce(), attemptRepository.hasAttemptFast(),
      ]);
      return StartupState({
        ready: true,
        background: BackgroundAppearance.fromId(background ?? remoteConfig.string(ConfigKeys.DEFAULT_BACKGROUND_THEME, BackgroundAppearance.Default.id)),
        palette: AccentPalette.fromId(palette ?? remoteConfig.string(ConfigKeys.DEFAULT_ACCENT_THEME, AccentPalette.Default.id)),
        hasPlan,
      });
    })();
    const timeout = new Promise((_, rej) => setTimeout(() => rej(new Error('timeout')), STARTUP_TIMEOUT_MS));
    // A damaged preference file must never trap the student on the splash forever.
    Promise.race([work, timeout]).then((s) => { this._state.value = s; }, () => { this._state.value = StartupState({ ready: true }); });
  }
  /** Persists the choice; the theme flow pushes the new value back into state. */
  setBackground(background) { this.settings.setBackgroundAppearance(background.id); }
  /** Live updates once the app is running, e.g. from Settings. */
  observeTheme() {
    this.settings.backgroundAppearance.subscribe((value) => {
      if (value != null) this._state.value = StartupState({ ...this._state.value, background: BackgroundAppearance.fromId(value) });
    });
    this.settings.accentPalette.subscribe((value) => {
      if (value != null) this._state.value = StartupState({ ...this._state.value, palette: AccentPalette.fromId(value) });
    });
  }
  setPalette(palette) { this.settings.setAccentPalette(palette.id); }
}

// =============================================================================================
// SteadylineNavHost.kt
// =============================================================================================
const GRAPH = [
  { route: Route.Onboarding.path },
  { route: Route.Home.path },
  { route: Route.Syllabus.path },
  { route: Route.Organise.path },
  { route: Route.Focus.path },
  { route: Route.Progress.path },
  { route: Route.Settings.path },
  { route: Route.Appearance.path },
  { route: Route.SettingsDetail.path },
  { route: Route.DayLog.path, arguments: { epochDay: { type: 'long' } } },
  { route: Route.Tests.path },
  { route: Route.MockForm.path, arguments: { mockId: { type: 'string', nullable: true, defaultValue: null } } },
  { route: Route.LogSession.path, arguments: { sessionId: { type: 'string', nullable: true, defaultValue: null }, epochDay: { type: 'long', defaultValue: NO_DAY } } },
  { route: Route.Policy.path, arguments: { policyId: { type: 'string' } } },
];

/** Lazy feature import → props → node; placeholder when the module/export is not there yet. */
function feature(file, exportName, routeLabel, props) {
  const run = captureScope();
  return import(`../features/${file}`).then(
    (m) => {
      const fn = m[exportName];
      if (typeof fn !== 'function') return placeholder(routeLabel, `${file} has no export ${exportName}`);
      return run(() => fn(props));
    },
    (e) => {
      if (!/Failed to fetch|Cannot find module|error loading dynamically imported module|Importing a module script failed/i.test(String(e?.message))) console.error(e);
      return placeholder(routeLabel, `features/${file} not ported yet`);
    },
  );
}
function placeholder(route, detail) {
  return Column({ class: 'fill', horizontalAlignment: 'CenterHorizontally', verticalArrangement: 'Center', css: { padding: 'var(--space-screen)', gap: 'var(--space-sm)', background: 'var(--c-bg)' } },
    Text({ text: route, style: 'titleLarge', color: 'var(--c-text)' }),
    Text({ text: detail, style: 'bodySmall', color: 'var(--c-textMuted)', textAlign: 'Center' }));
}

/**
 * SteadylineNavHost({ container, startInOnboarding, background, onBackground, accentPalette, onAccentPalette, themeFlow })
 * themeFlow: StateFlow<{background, palette}> — destinations taking background/accentPalette recompose on change.
 */
export function SteadylineNavHost({ container, startInOnboarding, onBackground, onAccentPalette, themeFlow }) {
  const navController = createNavController({ graph: GRAPH, startDestination: startInOnboarding ? Route.Onboarding.path : Route.Home.path });
  installSystemBack(navController);
  const theme = () => themeFlow.value;

  const root = h('div', { class: 'scaffold' });
  const contentPadding = (withBar) => ({ css: { paddingTop: 'var(--status-bar-height)', paddingBottom: withBar ? '0px' : 'var(--gesture-bar-height)' } });
  const currentTab = () => Tab.entries.find((t) => t.route.path === navController.currentRoute()) || null;
  const mod = () => contentPadding(!!currentTab());

  // ---- Destinations -------------------------------------------------------------------------
  const destinations = {
    [Route.Onboarding.path]: () => {
      const startAtPlan = container.onboardingStartAtPlan.value;
      LaunchedEffect([], () => { container.onboardingStartAtPlan.value = false; });
      return feature('onboarding/OnboardingScreen.js', 'OnboardingRoute', Route.Onboarding.path, {
        planStore: container.planStore,
        examPackRepository: container.examPackRepository,
        attemptRepository: container.attemptRepository,
        availabilityRepository: container.availabilityRepository,
        studyPreferenceRepository: container.studyPreferenceRepository,
        targetSyllabusRepository: container.targetSyllabusRepository,
        topicProgressRepository: container.topicProgressRepository,
        planRepository: container.planRepository,
        rollingPlanRepository: container.rollingPlanRepository,
        dispatchers: container.dispatchers,
        examDate: neetExamDate(container.remoteConfig),
        examDateOfficial: neetExamDateOfficial(container.remoteConfig),
        startAtPlan,
        background: theme().background,
        onBackground,
        accentPalette: theme().palette,
        onAccentPalette,
        analytics: container.analytics,
        onFinished: (openOrganise) => {
          navController.navigate(openOrganise ? Route.Organise.path : Route.Home.path, { popUpTo: Route.Onboarding.path, inclusive: true });
        },
        // Kotlin passes no modifier: onboarding handles its own insets.
      });
    },
    [Route.Home.path]: () => feature('home/HomeScreen.js', 'HomeRoute', Route.Home.path, {
      examPackRepository: container.examPackRepository,
      attemptRepository: container.attemptRepository,
      availabilityRepository: container.availabilityRepository,
      planRepository: container.planRepository,
      studySessionRepository: container.studySessionRepository,
      topicProgressRepository: container.topicProgressRepository,
      studyPreferenceRepository: container.studyPreferenceRepository,
      revisionRepository: container.revisionRepository,
      rollingPlanRepository: container.rollingPlanRepository,
      targetSyllabusRepository: container.targetSyllabusRepository,
      chapterStageRepository: container.chapterStageRepository,
      onSetupPlan: () => navController.navigate(Route.Onboarding.path),
      onAdjustPlan: () => { container.onboardingStartAtPlan.value = true; navController.navigate(Route.Onboarding.path); },
      onEditPlan: () => navController.navigate(Route.Organise.path),
      onOpenDay: (date) => navController.navigate(Route.DayLog.pathFor(date)),
      onStartFocus: async (session) => {
        const current = await container.focusController.load();
        container.focusController.start(FocusSession({
          status: FocusStatus.RUNNING,
          durationSec: session.durationMinutes * 60,
          remainingSec: session.durationMinutes * 60,
          endsAtMs: session.runningEndsAtMs ?? (nowMs() + session.durationMinutes * 60_000),
          completedToday: current.completedToday,
          block: FocusBlockRef({
            id: session.id,
            title: session.title,
            subtitle: session.isRevision ? formatAndroid(R.string.focus_revision_title, session.sectionName) : session.sectionName,
            tag: session.isRevision ? BlockTag.REVISE : BlockTag.READ,
            sessionId: session.id,
            nodeKey: session.nodeKey,
            isRevision: session.isRevision,
          }),
        }));
        navController.navigate(Route.Focus.path);
      },
      pendingSyllabusPick: container.pendingSyllabusPick,
      onConsumedSyllabusPick: () => { container.pendingSyllabusPick.value = null; },
      modifier: mod(),
    }),
    [Route.Syllabus.path]: () => feature('syllabus/SyllabusScreen.js', 'SyllabusRoute', Route.Syllabus.path, {
      examPackRepository: container.examPackRepository,
      topicProgressRepository: container.topicProgressRepository,
      attemptRepository: container.attemptRepository,
      targetSyllabusRepository: container.targetSyllabusRepository,
      rollingPlanRepository: container.rollingPlanRepository,
      chapterStageRepository: container.chapterStageRepository,
      stageLabelStore: container.stageLabelStore,
      proRepository: container.proRepository,
      onOpenPaywall: () => container.openPaywall(),
      onStartTopic: (pick) => {
        container.pendingSyllabusPick.value = pick;
        navController.navigate(Route.Home.path, { popUpTo: Route.Home.path, inclusive: true, launchSingleTop: true });
      },
      onOrganise: () => navController.navigate(Route.Organise.path),
      modifier: mod(),
    }),
    [Route.Organise.path]: () => feature('syllabus/OrganiseScreen.js', 'OrganiseRoute', Route.Organise.path, {
      examPackRepository: container.examPackRepository,
      attemptRepository: container.attemptRepository,
      targetSyllabusRepository: container.targetSyllabusRepository,
      planRepository: container.planRepository,
      studyPreferenceRepository: container.studyPreferenceRepository,
      rollingPlanRepository: container.rollingPlanRepository,
      topicProgressRepository: container.topicProgressRepository,
      onBack: () => navController.popBackStack(),
      modifier: mod(),
    }),
    [Route.Focus.path]: () => feature('focus/FocusScreen.js', 'FocusRoute', Route.Focus.path, {
      focusController: container.focusController,
      settingsStore: container.settings,
      examPackRepository: container.examPackRepository,
      attemptRepository: container.attemptRepository,
      planRepository: container.planRepository,
      studySessionRepository: container.studySessionRepository,
      rollingPlanRepository: container.rollingPlanRepository,
      studyPreferenceRepository: container.studyPreferenceRepository,
      topicProgressRepository: container.topicProgressRepository,
      onClose: () => navController.navigate(Route.Home.path, { popUpTo: Route.Home.path, inclusive: true, launchSingleTop: true }),
      modifier: mod(),
    }),
    [Route.Progress.path]: () => feature('progress/ProgressScreen.js', 'ProgressRoute', Route.Progress.path, {
      examPackRepository: container.examPackRepository,
      attemptRepository: container.attemptRepository,
      planRepository: container.planRepository,
      studySessionRepository: container.studySessionRepository,
      topicProgressRepository: container.topicProgressRepository,
      targetSyllabusRepository: container.targetSyllabusRepository,
      rollingPlanRepository: container.rollingPlanRepository,
      studyPreferenceRepository: container.studyPreferenceRepository,
      revisionRepository: container.revisionRepository,
      availabilityRepository: container.availabilityRepository,
      chapterStageRepository: container.chapterStageRepository,
      mockTestRepository: container.mockTestRepository,
      proRepository: container.proRepository,
      onOpenPaywall: () => container.openPaywall(),
      onOpenSettings: () => navController.navigate(Route.Settings.path),
      onGoToday: () => navController.navigate(Route.Home.path, { launchSingleTop: true }),
      onOpenDay: (date) => navController.navigate(Route.DayLog.pathFor(date)),
      onLogStudy: () => navController.navigate(Route.LogSession.newOn(null)),
      onOpenTests: () => navController.navigate(Route.Tests.path),
      modifier: mod(),
    }),
    [Route.Settings.path]: () => feature('settings/MoreRoute.js', 'MoreRoute', Route.Settings.path, {
      planStore: container.planStore,
      examPackRepository: container.examPackRepository,
      attemptRepository: container.attemptRepository,
      topicProgressRepository: container.topicProgressRepository,
      targetSyllabusRepository: container.targetSyllabusRepository,
      rollingPlanRepository: container.rollingPlanRepository,
      chapterStageRepository: container.chapterStageRepository,
      examDateOfficial: neetExamDateOfficial(container.remoteConfig),
      appVersion: BuildConfig.VERSION_NAME,
      background: theme().background,
      onOpenAppearance: () => navController.navigate(Route.Appearance.path),
      onOpenSettings: () => navController.navigate(Route.SettingsDetail.path),
      onRedoOnboarding: () => navController.navigate(Route.Onboarding.path),
      onOpenPolicy: (id) => navController.navigate(`policy/${id}`),
      proRepository: container.proRepository,
      isDebugBuild: BuildConfig.DEBUG,
      onOpenPaywall: () => container.openPaywall(),
      onLogOut: () => container.appScope.launch(() => container.logOut()),
      modifier: mod(),
    }),
    [Route.Appearance.path]: () => feature('settings/AppearanceScreen.js', 'AppearanceScreen', Route.Appearance.path, {
      background: theme().background,
      onBackground,
      accentPalette: theme().palette,
      onAccentPalette,
      onBack: () => navController.popBackStack(),
      modifier: mod(),
    }),
    [Route.SettingsDetail.path]: () => feature('settings/SettingsDetailScreen.js', 'SettingsDetailRoute', Route.SettingsDetail.path, {
      planStore: container.planStore,
      settingsStore: container.settings,
      focusStore: container.focusStore,
      syllabusStore: container.syllabusStore,
      studySessionStore: container.studySessionStore,
      examPackRepository: container.examPackRepository,
      attemptRepository: container.attemptRepository,
      studyPreferenceRepository: container.studyPreferenceRepository,
      rollingPlanRepository: container.rollingPlanRepository,
      configExamDate: neetExamDate(container.remoteConfig),
      examDateOfficial: neetExamDateOfficial(container.remoteConfig),
      onBack: () => navController.popBackStack(),
      onCleared: () => {
        // Clearing all data wiped the session; drop its alarm and notification too.
        container.appScope.launch(() => container.focusController.reconcile());
        navController.navigate(Route.Onboarding.path, { popUpTo: 0, inclusive: true });
      },
      onReminderChanged: () => container.rearmDailyReminder(),
      backupRepository: container.backupRepository,
      onImported: () => container.appScope.launch(() => container.onBackupImported()),
      modifier: mod(),
    }),
    [Route.DayLog.path]: (entry) => feature('progress/DayLogScreen.js', 'DayLogRoute', Route.DayLog.path, {
      date: entry.arguments.epochDay != null ? ofEpochDay(entry.arguments.epochDay) : localToday(),
      examPackRepository: container.examPackRepository,
      attemptRepository: container.attemptRepository,
      studySessionRepository: container.studySessionRepository,
      planRepository: container.planRepository,
      onBack: () => navController.popBackStack(),
      onLogStudy: (date) => navController.navigate(Route.LogSession.newOn(date)),
      onEditSession: (id) => navController.navigate(Route.LogSession.edit(id)),
      modifier: mod(),
    }),
    [Route.Tests.path]: () => feature('progress/TestsScreen.js', 'TestsRoute', Route.Tests.path, {
      attemptRepository: container.attemptRepository,
      mockTestRepository: container.mockTestRepository,
      proRepository: container.proRepository,
      onOpenPaywall: () => container.openPaywall(),
      onBack: () => navController.popBackStack(),
      onAddMock: () => navController.navigate(Route.MockForm.new),
      onEditMock: (id) => navController.navigate(Route.MockForm.edit(id)),
      modifier: mod(),
    }),
    [Route.MockForm.path]: (entry) => feature('progress/MockFormScreen.js', 'MockFormRoute', Route.MockForm.path, {
      mockId: entry.arguments.mockId ?? null,
      examPackRepository: container.examPackRepository,
      attemptRepository: container.attemptRepository,
      mockTestRepository: container.mockTestRepository,
      proRepository: container.proRepository,
      onOpenPaywall: () => container.openPaywall(),
      onDone: () => navController.popBackStack(),
      modifier: mod(),
    }),
    [Route.LogSession.path]: (entry) => {
      const day = entry.arguments.epochDay ?? NO_DAY;
      return feature('progress/LogSessionScreen.js', 'LogSessionRoute', Route.LogSession.path, {
        sessionId: entry.arguments.sessionId ?? null,
        date: day === NO_DAY ? null : ofEpochDay(day),
        examPackRepository: container.examPackRepository,
        attemptRepository: container.attemptRepository,
        studySessionRepository: container.studySessionRepository,
        onDone: () => navController.popBackStack(),
        modifier: mod(),
      });
    },
    [Route.Policy.path]: (entry) => feature('settings/MoreScreen.js', 'PolicyScreen', Route.Policy.path, {
      policyId: entry.arguments.policyId ?? '',
      appVersion: BuildConfig.VERSION_NAME,
      onBack: () => navController.popBackStack(),
      modifier: mod(),
    }),
  };

  // ---- Scaffold: NavHost + bottom bar ------------------------------------------------------
  const host = NavHost({ navController, destinations, class: 'nav-area' });
  const barSlot = h('div', { class: 'bottom-bar-slot' });
  root.append(host, barSlot);

  const onSelect = (target) => navController.navigate(target.route.path, {
    popUpTo: Route.Home.path, saveState: true, launchSingleTop: true, restoreState: true,
  });
  let lastTab;
  navController.currentBackStackEntry.subscribe(() => {
    const tab = currentTab();
    if (tab === lastTab) return;
    lastTab = tab;
    barSlot.replaceChildren(...(tab ? [SteadylineBottomBar({ selected: tab, onSelect })] : []));
  });

  // Recompose destinations that read background / accentPalette when the theme changes.
  let lastTheme = themeFlow.value;
  themeFlow.subscribe((t) => {
    if (t === lastTheme) return;
    lastTheme = t;
    const r = navController.currentRoute();
    if (r === Route.Onboarding.path || r === Route.Settings.path || r === Route.Appearance.path) host.recompose();
  });

  // ---- One-shot requests (notification / widget / reminder taps) ----------------------------
  container.openFocusRequest.subscribe((open) => {
    if (!open) return;
    container.openFocusRequest.value = false;
    queueMicrotask(() => navController.navigate(Route.Focus.path, { launchSingleTop: true }));
  });
  container.openTodayRequest.subscribe((open) => {
    if (!open) return;
    container.openTodayRequest.value = false;
    queueMicrotask(() => {
      // Still in setup there is no Today to show yet.
      if (navController.currentRoute() !== Route.Onboarding.path) {
        navController.navigate(Route.Home.path, { popUpTo: Route.Home.path, inclusive: true, launchSingleTop: true });
      }
    });
  });

  // ---- Paywall: never over onboarding or a Focus session (a request there is dropped, not queued) ----
  const paywallState = combine([container.paywallRequest, navController.currentBackStackEntry], (requested, entry) => {
    const currentPath = entry?.route ?? null;
    const blocked = currentPath == null || currentPath === Route.Onboarding.path || currentPath === Route.Focus.path;
    return { requested, blocked, currentPath };
  });
  paywallState.subscribe(({ requested, blocked, currentPath }) => {
    if (requested && blocked && currentPath != null) queueMicrotask(() => { container.paywallRequest.value = false; });
  });
  const paywallHost = observe(paywallState.map((s) => s.requested && !s.blocked).distinctUntilChanged(), (show) => (show
    ? PaywallHost({ proRepository: container.proRepository, analytics: container.analytics, onDismiss: () => { container.paywallRequest.value = false; } })
    : null), { style: { display: 'contents' } });
  root.append(paywallHost);

  root.navController = navController;
  return root;
}

// =============================================================================================
// Device frame, splash, MainActivity
// =============================================================================================
function luminance(hex) {
  const m = /^#([0-9a-f]{6})/i.exec(hex || '');
  if (!m) return 0;
  const n = parseInt(m[1], 16);
  const lin = (c) => { c /= 255; return c <= 0.04045 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4; };
  return 0.2126 * lin((n >> 16) & 255) + 0.7152 * lin((n >> 8) & 255) + 0.0722 * lin(n & 255);
}

function StatusBar() {
  const time = h('span', { class: 'sb-time' });
  const tick = () => {
    const d = new Date();
    time.textContent = `${d.getHours() % 12 || 12}:${String(d.getMinutes()).padStart(2, '0')}`;
  };
  tick();
  setInterval(tick, 15_000);
  const icons = h('span', { class: 'sb-icons', html:
    '<svg viewBox="0 0 24 24" width="16" height="16"><path fill="currentColor" d="M12 21 0.5 9.5Q3 7 5.9 5.75 8.8 4.5 12 4.5t6.1 1.25Q21 7 23.5 9.5Z"/></svg>' +
    '<svg viewBox="0 0 24 24" width="16" height="16"><path fill="currentColor" d="M2 22 22 2v20Z"/></svg>' +
    '<svg viewBox="0 0 24 24" width="16" height="16"><path fill="currentColor" d="M8 22q-.425 0-.712-.288Q7 21.425 7 21V5q0-.425.288-.713Q7.575 4 8 4h2V2h4v2h2q.425 0 .712.287Q17 4.575 17 5v16q0 .425-.288.712Q16.425 22 16 22Z"/></svg>' });
  return h('div', { class: 'status-bar', 'aria-hidden': 'true' }, time, icons);
}
function GestureBar() {
  return h('div', { class: 'gesture-bar', 'aria-hidden': 'true' }, h('div', { class: 'gesture-handle' }));
}
function Splash() {
  return h('div', { class: 'splash' }, h('div', { class: 'splash-icon' }, drawable('ic_launcher', '240px')));
}

/** MainActivity.onCreate + setContent. */
export function main(mount = document.getElementById('app')) {
  const container = appContainer;
  // SteadylineApp.onCreate
  container.runMigrationInBackground();

  const device = h('div', { class: 'device' });
  const screen = h('div', { class: 'screen', 'data-app-root': '' });
  const appLayer = h('div', { class: 'app-layer' });
  const overlay = h('div', { class: 'overlay-root', 'data-overlay-root': '' });
  const statusBar = StatusBar();
  const gestureBar = GestureBar();
  screen.append(appLayer, overlay, statusBar, gestureBar);
  device.append(screen);
  mount.replaceChildren(device);
  document.title = R.string.app_name;

  const startup = new StartupViewModel(container.settings, container.attemptRepository, container.remoteConfig);
  startup.observeTheme();
  preloadPaywallSheet();

  // Theme (SteadylineTheme + the SideEffect that tints the system bar icons).
  const themeFlow = startup.state.map((s) => ({ background: s.background, palette: s.palette }))
    .distinctUntilChanged((a, b) => a.background === b.background && a.palette === b.palette);
  themeFlow.subscribe(({ background, palette }) => {
    const colors = applyTheme(background, palette, screen);
    document.documentElement.style.setProperty('--frame-bg', colors.bg);
  });
  appColorsFlow.subscribe((colors) => {
    const lightBars = luminance(colors.bg) > 0.5;
    screen.dataset.lightBars = lightBars ? 'true' : 'false';
  });

  // Splash until ready, then the nav host (decided once, from stored prefs).
  appLayer.append(Splash());
  let navRoot = null;
  startup.state.subscribe((state) => {
    if (!state.ready || navRoot) return;
    navRoot = SteadylineNavHost({
      container,
      startInOnboarding: !state.hasPlan,
      background: state.background,
      onBackground: (b) => startup.setBackground(b),
      accentPalette: state.palette,
      onAccentPalette: (p) => startup.setPalette(p),
      themeFlow,
    });
    appLayer.replaceChildren(navRoot);
    window.__app.nav = navRoot.navController;
  });

  /** MainActivity.handleOpenRequest: what a Focus notification / reminder / widget tap does. */
  const openRequest = (open, blockId) => {
    if (open === 'focus') {
      if (!blockId) container.openFocusRequest.value = true;
      else container.startPlannedBlock(blockId);
    } else if (open === 'today') container.openTodayRequest.value = true;
    else if (open === 'paywall') container.openPaywall();
  };
  window.__app = { container, startup, openRequest, nav: null };
}

main();
