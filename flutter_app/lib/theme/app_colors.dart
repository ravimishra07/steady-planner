import 'package:flutter/material.dart';
import 'tokens.dart';

/// Every colour Material's [ColorScheme] does not model.
///
/// Screens read these through `context.colors` and never touch [Tokens]
/// directly, so switching theme repaints everything.
@immutable
class AppColors extends ThemeExtension<AppColors> {
  const AppColors({
    required this.bg,
    required this.bgDeep,
    required this.surface,
    required this.surfaceTinted,
    required this.surfaceCard,
    required this.surfaceControl,
    required this.surfaceInk,
    required this.elevated,
    required this.surface3,
    required this.border,
    required this.borderSubtle,
    required this.hairline,
    required this.hairlineSoft,
    required this.glassTint,
    required this.glassStroke,
    required this.ctaBorder,
    required this.textPrimary,
    required this.textSecondary,
    required this.textMuted,
    required this.textDisabled,
    required this.tabBg,
    required this.tabUnselected,
    required this.brandContainer,
    required this.successContainer,
    required this.dangerContainer,
    required this.dangerSoft,
    required this.dangerStripe,
    required this.infoTint,
    required this.warningTint,
    required this.warningRow,
    required this.onSuccess,
    required this.cardShadow,
  });

  // surfaces
  final Color bg, bgDeep, surface, surfaceTinted, surfaceCard, surfaceControl, surfaceInk;
  final Color elevated, surface3;

  // lines
  final Color border, borderSubtle, hairline, hairlineSoft, glassTint, glassStroke, ctaBorder;

  // text
  final Color textPrimary, textSecondary, textMuted, textDisabled;

  // tabs
  final Color tabBg, tabUnselected;

  // semantic containers
  final Color brandContainer, successContainer, dangerContainer, dangerSoft, dangerStripe;
  final Color infoTint, warningTint, warningRow, onSuccess;

  final List<BoxShadow> cardShadow;

  // Shared hues — identical in both themes, so they read straight from Tokens.
  Color get brand => Tokens.brand;
  Color get brandSoft => Tokens.brandSoft;
  Color get brandDeep => Tokens.brandDeep;
  Color get success => Tokens.success;
  Color get successStrong => Tokens.successStrong;
  Color get warning => Tokens.warning;
  Color get danger => Tokens.danger;
  Color get info => Tokens.info;
  Color get accentCyan => Tokens.accentCyan;
  Color get onBrand => Tokens.onBrand;
  Color get tabSelected => Tokens.tabSelected;

  /// The 0.45px hairline every card carries.
  BoxBorder get cardBorder => Border.all(color: hairline, width: 0.45);

  static const dark = AppColors(
    bg: DarkTokens.bg,
    bgDeep: DarkTokens.bgDeep,
    surface: DarkTokens.surface,
    surfaceTinted: DarkTokens.surfaceTinted,
    surfaceCard: DarkTokens.surfaceCard,
    surfaceControl: DarkTokens.surfaceControl,
    surfaceInk: DarkTokens.surfaceInk,
    elevated: DarkTokens.elevated,
    surface3: DarkTokens.surface3,
    border: DarkTokens.border,
    borderSubtle: DarkTokens.borderSubtle,
    hairline: DarkTokens.hairline,
    hairlineSoft: DarkTokens.hairlineSoft,
    glassTint: DarkTokens.glassTint,
    glassStroke: DarkTokens.glassStroke,
    ctaBorder: DarkTokens.ctaBorder,
    textPrimary: DarkTokens.text,
    textSecondary: DarkTokens.textSecondary,
    textMuted: DarkTokens.textMuted,
    textDisabled: DarkTokens.textDisabled,
    tabBg: DarkTokens.tabBg,
    tabUnselected: DarkTokens.tabUnselected,
    brandContainer: DarkTokens.brandContainer,
    successContainer: DarkTokens.successContainer,
    dangerContainer: DarkTokens.dangerContainer,
    dangerSoft: DarkTokens.dangerSoft,
    dangerStripe: DarkTokens.dangerStripe,
    infoTint: DarkTokens.infoTint,
    warningTint: DarkTokens.warningTint,
    warningRow: DarkTokens.warningRow,
    onSuccess: DarkTokens.onSuccess,
    cardShadow: [
      BoxShadow(color: Color(0x2E000000), blurRadius: 6, offset: Offset(0, 3)),
    ],
  );

  static const light = AppColors(
    bg: LightTokens.bg,
    bgDeep: LightTokens.bgDeep,
    surface: LightTokens.surface,
    surfaceTinted: LightTokens.surfaceTinted,
    surfaceCard: LightTokens.surfaceCard,
    surfaceControl: LightTokens.surfaceControl,
    surfaceInk: LightTokens.surfaceInk,
    elevated: LightTokens.elevated,
    surface3: LightTokens.surface3,
    border: LightTokens.border,
    borderSubtle: LightTokens.borderSubtle,
    hairline: LightTokens.hairline,
    hairlineSoft: LightTokens.hairlineSoft,
    glassTint: LightTokens.glassTint,
    glassStroke: LightTokens.glassStroke,
    ctaBorder: LightTokens.ctaBorder,
    textPrimary: LightTokens.text,
    textSecondary: LightTokens.textSecondary,
    textMuted: LightTokens.textMuted,
    textDisabled: LightTokens.textDisabled,
    tabBg: LightTokens.tabBg,
    tabUnselected: LightTokens.tabUnselected,
    brandContainer: LightTokens.brandContainer,
    successContainer: LightTokens.successContainer,
    dangerContainer: LightTokens.dangerContainer,
    dangerSoft: LightTokens.dangerSoft,
    dangerStripe: LightTokens.dangerStripe,
    infoTint: LightTokens.infoTint,
    warningTint: LightTokens.warningTint,
    warningRow: LightTokens.warningRow,
    onSuccess: LightTokens.onSuccess,
    cardShadow: [
      BoxShadow(color: Color(0x14111827), blurRadius: 8, offset: Offset(0, 2)),
    ],
  );

  @override
  AppColors copyWith() => this;

  @override
  AppColors lerp(ThemeExtension<AppColors>? other, double t) {
    // Themes swap rather than blend; a half-lerped palette is never wanted.
    if (other is! AppColors) return this;
    return t < 0.5 ? this : other;
  }
}

extension AppColorsX on BuildContext {
  AppColors get colors => Theme.of(this).extension<AppColors>()!;
}
