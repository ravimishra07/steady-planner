// Port of feature/settings/MoreScreen.kt: MoreScreen, PolicyScreen (+ private AccountCard, SettingsValueRow,
// ProSection, ProfileGroup, ProfileRow, SectionLabel, SettingsStatusScreen, BacklogSplitDialog).
// `remember { mutableStateOf() }` locals → nav.remember flows + a nested observe(); dialogs are keyed so a
// re-render keeps them on screen without replaying their enter animation.
import { combine } from '../../platform/flow.js';
import { remember, observe } from '../../platform/nav.js';
import { BackgroundAppearance } from '../../design/colors.js';
import {
  Column, Row, Box, Text, Icon, Surface, IconButton, Button, OutlinedButton, TextButton, ButtonDefaults, BorderStroke,
  AlertDialog, Dialog, CircularProgressIndicator, Slider, Switch, AppListGroup, AppListDivider, AppListRow,
  CircleShape, clickable,
} from '../../design/components.js';
import { ProStatus } from '../../data/ProRepository.js';
import { statusMessage } from './PaywallSheet.js';
import { stringResource, pluralStringResource, ScrollColumn, FillColumn } from './_local.js';

const SPLIT_STEP = 5;

/**
 * MoreScreen({ loading, loadFailed, planExamLabel, examDateLabel, examDateOfficial, finishByLabel, weekdayHours,
 *   weekendHours, fixedBlockCount, coveragePercent, appVersion, background, onRetry, onOpenAppearance, onOpenSettings,
 *   onRedoOnboarding, onOpenPolicy, modifier, liveBacklogSplit, onLiveBacklogSplit, pro, onOpenPaywall,
 *   onRestorePurchase, onDismissProStatus, onDebugForcePro, onLogOut })
 */
export function MoreScreen(p) {
  const showRedoDialog = remember('more:showRedoDialog', false);
  const showLogOutDialog = remember('more:showLogOutDialog', false);
  const loggingOut = remember('more:loggingOut', false);
  const showSplitDialog = remember('more:showSplitDialog', false);
  // BacklogSplitDialog's `remember { mutableIntStateOf(initial) }`: reset each time the dialog opens.
  const splitLive = remember('more:splitLive', 0);
  if (p.loading) {
    return SettingsStatusScreen({ title: stringResource('more_title'), message: stringResource('settings_loading'), loading: true, modifier: p.modifier });
  }
  if (p.loadFailed) {
    return SettingsStatusScreen({
      title: stringResource('more_title'), message: stringResource('settings_load_failed'),
      actionLabel: stringResource('settings_retry'), onAction: p.onRetry, modifier: p.modifier,
    });
  }
  const pro = p.pro;
  const backgroundLabel = {
    [BackgroundAppearance.System.id]: 'settings_theme_system',
    [BackgroundAppearance.Light.id]: 'settings_theme_light',
    [BackgroundAppearance.Dark.id]: 'settings_theme_dark',
    [BackgroundAppearance.Grey.id]: 'settings_background_grey',
    [BackgroundAppearance.Slate.id]: 'settings_background_slate',
  }[p.background.id];

  const content = ScrollColumn({
    modifier: p.modifier,
    css: { padding: '0 var(--space-screen)' },
    scrollKey: 'more-scroll',
    children: [
      Text({ text: stringResource('more_title'), style: 'headlineMedium', color: 'var(--c-text)', css: { padding: 'var(--space-sm) 0 var(--space-lg)' } }),
      AccountCard({
        title: p.planExamLabel ?? stringResource('more_your_plan'),
        subtitle: p.finishByLabel != null && p.weekdayHours != null
          ? stringResource('more_plan_card_subtitle', p.finishByLabel, compactHours(p.weekdayHours))
          : stringResource('more_no_plan'),
        coveragePercent: p.coveragePercent,
        onClick: p.onOpenSettings,
      }),
      SectionLabel(stringResource('more_section_plan_short')),
      ProfileGroup([
        SettingsValueRow({ icon: 'Outlined.Event', title: stringResource('more_finish_by_date'), value: p.finishByLabel ?? '', onClick: () => { showRedoDialog.value = true; } }),
        AppListDivider(),
        SettingsValueRow({
          icon: 'Outlined.Event', title: stringResource('more_exam_date'),
          value: p.examDateLabel != null ? (p.examDateOfficial ? p.examDateLabel : stringResource('more_exam_date_expected', p.examDateLabel)) : '',
          onClick: () => { showRedoDialog.value = true; },
        }),
        AppListDivider(),
        SettingsValueRow({
          icon: 'Outlined.Schedule', title: stringResource('more_hours_breaks'),
          value: p.weekdayHours != null && p.weekendHours != null
            ? stringResource('more_hours_pair', compactHours(p.weekdayHours), compactHours(p.weekendHours)) : '',
          onClick: p.onOpenSettings,
        }),
        AppListDivider(),
        SettingsValueRow({
          icon: 'Outlined.EventBusy', title: stringResource('more_fixed_hours'),
          value: pluralStringResource('more_blocks', p.fixedBlockCount, p.fixedBlockCount),
          onClick: () => { showRedoDialog.value = true; },
        }),
        ...(p.liveBacklogSplit != null ? [
          AppListDivider(),
          SettingsValueRow({
            icon: 'Outlined.School', title: stringResource('more_backlog_split'),
            value: stringResource('more_backlog_split_value', p.liveBacklogSplit, 100 - p.liveBacklogSplit),
            onClick: () => { splitLive.value = p.liveBacklogSplit; showSplitDialog.value = true; },
          }),
        ] : []),
      ]),
      SectionLabel(stringResource('more_section_pro')),
      ProfileGroup(ProSection(pro, p.onOpenPaywall, p.onRestorePurchase, p.onDismissProStatus, p.onDebugForcePro)),
      SectionLabel(stringResource('more_section_app_short')),
      ProfileGroup([
        SettingsValueRow({ icon: 'Outlined.Palette', title: stringResource('more_appearance'), value: stringResource(backgroundLabel), onClick: p.onOpenAppearance }),
      ]),
      SectionLabel(stringResource('more_section_about')),
      ProfileGroup([
        ProfileRow('Outlined.Lock', 'more_privacy', 'more_privacy_sub', () => p.onOpenPolicy('privacy')),
        AppListDivider(),
        ProfileRow('Outlined.Info', 'more_terms', 'more_terms_sub', () => p.onOpenPolicy('terms')),
        AppListDivider(),
        AppListRow({ icon: 'Outlined.Info', title: stringResource('more_about'), subtitle: stringResource('more_about_version', p.appVersion), onClick: () => p.onOpenPolicy('about') }),
      ]),
      OutlinedButton({
        onClick: () => { showLogOutDialog.value = true; },
        class: 'fill-w',
        css: { marginTop: 'var(--space-xxl)', height: 'var(--size-ctaHeight)' },
        border: BorderStroke('var(--stroke-hairline)', 'var(--c-danger)'),
        colors: ButtonDefaults.outlinedButtonColors({ contentColor: 'var(--c-danger)' }),
        children: Text({ text: stringResource('more_log_out'), style: 'labelLarge' }),
      }),
      Text({ text: stringResource('more_footer'), style: 'bodySmall', color: 'var(--c-textMuted)', css: { padding: 'var(--space-xl) 0' } }),
    ],
  });

  const overlays = observe(combine([showRedoDialog, showLogOutDialog, loggingOut, showSplitDialog], (a, b, c, d) => [a, b, c, d]),
    ([redo, logOut, outInProgress, split]) => {
      const out = [];
      if (logOut) {
        out.push(AlertDialog({
          key: 'more:logOut',
          onDismissRequest: () => { showLogOutDialog.value = false; },
          title: Text(stringResource('more_log_out_title')),
          text: Text(stringResource('more_log_out_body')),
          confirmButton: TextButton({
            onClick: () => { showLogOutDialog.value = false; loggingOut.value = true; p.onLogOut(); },
            colors: ButtonDefaults.textButtonColors({ contentColor: 'var(--c-danger)' }),
            children: Text(stringResource('more_log_out')),
          }),
          dismissButton: TextButton({ onClick: () => { showLogOutDialog.value = false; }, children: Text(stringResource('more_redo_confirm_no')) }),
        }));
      }
      if (outInProgress) {
        // Stays up until the app relaunches into onboarding.
        out.push(Dialog({
          key: 'more:loggingOut',
          onDismissRequest: () => {},
          properties: { dismissOnBackPress: false, dismissOnClickOutside: false },
          children: CircularProgressIndicator(),
        }));
      }
      if (split && p.liveBacklogSplit != null) {
        out.push(BacklogSplitDialog({ live: splitLive, onSave: p.onLiveBacklogSplit, onDismiss: () => { showSplitDialog.value = false; } }));
      }
      if (redo) {
        out.push(AlertDialog({
          key: 'more:redo',
          onDismissRequest: () => { showRedoDialog.value = false; },
          title: Text(stringResource('more_redo_confirm_title')),
          text: Text(stringResource('more_redo_confirm_body')),
          confirmButton: TextButton({ onClick: () => { showRedoDialog.value = false; p.onRedoOnboarding(); }, children: Text(stringResource('more_redo_confirm_yes')) }),
          dismissButton: TextButton({ onClick: () => { showRedoDialog.value = false; }, children: Text(stringResource('more_redo_confirm_no')) }),
        }));
      }
      return out;
    }, { style: { display: 'contents' } });
  content.append(overlays);
  return content;
}

/** Settings → Pro: status or buy, restore, and in debug builds a switch that forces Pro. */
function ProSection(pro, onOpenPaywall, onRestorePurchase, onDismissProStatus, onDebugForcePro) {
  const out = [
    AppListRow({
      icon: 'Outlined.WorkspacePremium',
      title: stringResource('more_pro'),
      subtitle: !pro.paywallEnabled ? stringResource('more_pro_unlocked_free')
        : pro.isPro ? stringResource('more_pro_active')
          : stringResource('more_pro_buy', pro.price ?? stringResource('paywall_price_fallback')),
      onClick: onOpenPaywall,
    }),
    AppListDivider(),
    AppListRow({
      icon: 'Outlined.Restore',
      title: stringResource('more_restore'),
      subtitle: stringResource('more_restore_sub'),
      onClick: onRestorePurchase,
      trailing: pro.status === ProStatus.BUSY
        ? () => CircularProgressIndicator({ css: { width: 'var(--size-standardIcon)', height: 'var(--size-standardIcon)' } })
        : null,
    }),
  ];
  const message = statusMessage(pro.status);
  if (message != null) {
    out.push(TextButton({ onClick: onDismissProStatus, class: 'fill-w', children: Text({ text: message, style: 'bodySmall', color: 'var(--c-textSecondary)' }) }));
  }
  if (pro.showDebugToggle) {
    out.push(AppListDivider());
    const row = Row({
      verticalAlignment: 'CenterVertically', class: 'fill-w',
      css: { minHeight: 'var(--size-touchTarget)', padding: 'var(--space-sm) var(--space-lg)' },
    },
    Text({ text: stringResource('more_debug_force_pro'), style: 'bodyMedium', color: 'var(--c-text)', class: 'weight' }),
    Switch({ checked: pro.debugForcePro, onCheckedChange: null }));
    row.setAttribute('data-testid', 'debug-force-pro');
    clickable(row, { role: 'switch', checked: pro.debugForcePro, onClick: () => onDebugForcePro(!pro.debugForcePro) });
    out.push(row);
  }
  return out;
}

/** PolicyScreen({ policyId, appVersion, onBack, modifier }) */
export function PolicyScreen({ policyId, appVersion, onBack, modifier }) {
  const titleRes = policyId === 'privacy' ? 'policy_privacy_title' : policyId === 'terms' ? 'policy_terms_title' : 'policy_about_title';
  const body = policyId === 'privacy' ? stringResource('policy_privacy_body')
    : policyId === 'terms' ? stringResource('policy_terms_body')
      : stringResource('policy_about_body', appVersion);
  return ScrollColumn({
    modifier,
    css: { padding: '0 var(--space-screen)', color: 'var(--c-onSurface)' },
    scrollKey: 'policy-scroll',
    children: [
      Row({ verticalAlignment: 'CenterVertically' },
        IconButton({ onClick: onBack, css: { width: 'var(--size-touchTarget)', height: 'var(--size-touchTarget)' },
          children: Icon({ imageVector: 'AutoMirrored.Filled.ArrowBack', contentDescription: stringResource('settings_back') }) }),
        Text({ text: stringResource(titleRes), style: 'headlineSmall', color: 'var(--c-text)' })),
      Text({ text: body, style: 'bodyLarge', color: 'var(--c-textSecondary)', css: { padding: 'var(--space-lg) 0 var(--space-xxl)' } }),
    ],
  });
}

function AccountCard({ title, subtitle, coveragePercent, onClick }) {
  const avatar = Surface({
    css: { marginRight: 'var(--space-md)', flex: 'none' }, shape: CircleShape, color: 'var(--c-primaryContainer)',
    children: Icon({ imageVector: 'Outlined.School', contentDescription: null, tint: 'var(--c-onPrimaryContainer)', css: { margin: 'var(--space-md)' } }),
  });
  const row = Row({ verticalAlignment: 'CenterVertically', css: { padding: 'var(--space-lg)' }, class: 'fill-w' },
    avatar,
    Column({ class: 'weight' },
      Text({ text: title, style: 'titleMedium' }),
      Text({ text: subtitle, style: 'bodySmall' })),
    coveragePercent != null
      ? Column({ horizontalAlignment: 'End' },
        Text({ text: stringResource('more_coverage_percent', coveragePercent), style: 'headlineSmall' }),
        Text({ text: stringResource('more_covered'), style: 'labelSmall' }))
      : null);
  return Surface({
    onClick, class: 'fill-w', css: { marginBottom: 'var(--space-lg)' },
    shape: 'var(--radius-extraLarge)', color: 'var(--c-selectionContainer)', contentColor: 'var(--c-onSelectionContainer)',
    children: row,
  });
}

function SettingsValueRow({ icon, title, value, onClick }) {
  const row = Row({
    class: 'fill-w', verticalAlignment: 'CenterVertically', horizontalArrangement: { spacedBy: 'var(--space-lg)' },
    css: { padding: 'var(--space-md) var(--space-lg)' },
  },
  Icon({ imageVector: icon, contentDescription: null, size: 'var(--size-standardIcon)', tint: 'var(--c-textSecondary)' }),
  Text({ text: title, style: 'bodyLarge', class: 'weight' }),
  value.trim() ? Text({ text: value, style: 'bodyMedium', color: 'var(--c-textSecondary)' }) : null,
  Icon({ imageVector: 'AutoMirrored.Filled.KeyboardArrowRight', contentDescription: null, tint: 'var(--c-textSecondary)' }));
  return Surface({ onClick, class: 'fill-w', color: 'var(--c-surfaceContainerLow)', children: row });
}

const compactHours = (hours) => (hours % 1 === 0 ? String(Math.trunc(hours)) : String(hours));

function ProfileGroup(children) {
  return AppListGroup({ class: 'fill-w', css: { marginBottom: 'var(--space-lg)', flex: 'none' }, children: Column({ class: 'fill-w' }, ...children) });
}

function ProfileRow(icon, titleRes, subtitleRes, onClick) {
  return AppListRow({ icon, title: stringResource(titleRes), subtitle: stringResource(subtitleRes), onClick });
}

function SectionLabel(text) {
  return Text({ text, style: 'labelLarge', color: 'var(--c-textMuted)', css: { padding: 'var(--space-sm) 0' } });
}

function SettingsStatusScreen({ title, message, modifier, loading = false, actionLabel = null, onAction = null }) {
  return FillColumn({
    modifier,
    css: { padding: `${modifier?.css?.paddingTop ?? '0'} var(--space-screen) ${modifier?.css?.paddingBottom ?? '0'}` },
    children: [
      Text({ text: title, style: 'headlineMedium', color: 'var(--c-text)', css: { padding: 'var(--space-sm) 0 var(--space-lg)' } }),
      Box({ class: 'fill-w', contentAlignment: 'Center' },
        Column({ horizontalAlignment: 'CenterHorizontally', verticalArrangement: { spacedBy: 'var(--space-md)' } },
          loading ? CircularProgressIndicator() : null,
          Text({ text: message, style: 'bodyLarge', color: 'var(--c-textSecondary)' }),
          actionLabel != null && onAction != null ? Button({ onClick: onAction, children: Text(actionLabel) }) : null)),
    ],
  });
}

/** Live vs backlog share of each day (§5.4), in steps of 5. */
function BacklogSplitDialog({ live, onSave, onDismiss }) {
  return AlertDialog({
    key: 'more:split',
    onDismissRequest: onDismiss,
    title: Text(stringResource('more_backlog_split')),
    text: Column({ verticalArrangement: { spacedBy: 'var(--space-sm)' }, class: 'fill-w' },
      Text({ text: stringResource('more_backlog_split_body'), style: 'bodyMedium', color: 'var(--c-textSecondary)' }),
      // Only the value line recomposes while dragging; the Slider keeps its own pointer capture.
      observe(live, (v) => Text({ text: stringResource('more_backlog_split_value', v, 100 - v), style: 'titleMedium', color: 'var(--c-text)' }), { style: { display: 'contents' } }),
      Slider({
        value: live.value,
        onValueChange: (it) => { live.value = Math.min(100, Math.max(0, Math.round(it / SPLIT_STEP) * SPLIT_STEP)); },
        valueRange: [0, 100],
        steps: 100 / SPLIT_STEP - 1,
      })),
    confirmButton: TextButton({ onClick: () => { onSave(live.value); onDismiss(); }, children: Text(stringResource('more_backlog_split_save')) }),
    dismissButton: TextButton({ onClick: onDismiss, children: Text(stringResource('more_redo_confirm_no')) }),
  });
}

