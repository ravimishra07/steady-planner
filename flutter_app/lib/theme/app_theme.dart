import 'package:flutter/material.dart';
import 'app_colors.dart';
import 'app_typography.dart';
import 'tokens.dart';

/// Builds [ThemeData] for both themes out of [AppColors] and [AppTypography].
/// Nothing here invents a value.
class AppTheme {
  const AppTheme._();

  static ThemeData dark() => _build(AppColors.dark, Brightness.dark);
  static ThemeData light() => _build(AppColors.light, Brightness.light);

  static ThemeData _build(AppColors c, Brightness brightness) {
    final scheme = ColorScheme(
      brightness: brightness,
      primary: c.brandDeep,
      onPrimary: c.onBrand,
      primaryContainer: c.brandContainer,
      onPrimaryContainer: c.brandSoft,
      secondary: c.accentCyan,
      onSecondary: c.onBrand,
      error: c.danger,
      onError: c.onBrand,
      errorContainer: c.dangerContainer,
      onErrorContainer: c.dangerSoft,
      surface: c.surface,
      onSurface: c.textPrimary,
      onSurfaceVariant: c.textSecondary,
      outline: c.border,
      outlineVariant: c.hairline,
    );

    return ThemeData(
      useMaterial3: true,
      brightness: brightness,
      colorScheme: scheme,
      scaffoldBackgroundColor: c.bg,
      canvasColor: c.bg,
      splashFactory: InkSparkle.splashFactory,
      fontFamily: AppTypography.family,
      textTheme: AppTypography.textTheme(c.textPrimary, c.textSecondary),
      extensions: [c],
      dividerTheme: DividerThemeData(color: c.hairline, thickness: 0.45, space: 0.45),
      filledButtonTheme: FilledButtonThemeData(
        style: FilledButton.styleFrom(
          backgroundColor: c.brandDeep,
          foregroundColor: c.onBrand,
          disabledBackgroundColor: c.surfaceControl,
          disabledForegroundColor: c.textDisabled,
          minimumSize: const Size.fromHeight(54),
          textStyle: AppTypography.lg.copyWith(fontWeight: FontWeight.w600),
          shape: const StadiumBorder(),
        ),
      ),
      textButtonTheme: TextButtonThemeData(
        style: TextButton.styleFrom(
          foregroundColor: c.textMuted,
          minimumSize: const Size.fromHeight(48),
          textStyle: AppTypography.callout,
        ),
      ),
      sliderTheme: SliderThemeData(
        activeTrackColor: c.brandDeep,
        inactiveTrackColor: c.elevated,
        thumbColor: c.brand,
        trackHeight: 5,
        overlayShape: const RoundSliderOverlayShape(overlayRadius: 20),
      ),
      inputDecorationTheme: InputDecorationTheme(
        filled: true,
        fillColor: c.surfaceControl,
        hintStyle: AppTypography.lgRegular.copyWith(color: c.textDisabled),
        contentPadding: const EdgeInsets.symmetric(
          horizontal: Tokens.spaceMd,
          vertical: Tokens.spaceMd,
        ),
        border: OutlineInputBorder(
          borderRadius: BorderRadius.circular(Tokens.rSm),
          borderSide: BorderSide(color: c.border),
        ),
        enabledBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(Tokens.rSm),
          borderSide: BorderSide(color: c.border),
        ),
        focusedBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(Tokens.rSm),
          borderSide: BorderSide(color: c.brandDeep, width: 1.5),
        ),
      ),
    );
  }
}
