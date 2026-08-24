import 'package:flutter/material.dart';
import 'tokens.dart';

/// The type ramp, by name. Screens use these — never a raw `fontSize:`.
///
/// Font family is deliberately null: that resolves to the platform face
/// (Roboto on Android, SF on iOS), matching the web build. Bundling a face
/// later is a one-line change here and nowhere else.
@immutable
class AppTypography {
  const AppTypography._();

  static const String? family = null;

  /// Digits that do not jog as values change — hours, prices, timers.
  static const tabular = [FontFeature.tabularFigures()];

  static const _r = FontWeight.w400;
  static const _m = FontWeight.w500;
  static const _sb = FontWeight.w600;
  static const _b = FontWeight.w700;

  static TextStyle _s(double size, FontWeight w, double height) => TextStyle(
        fontFamily: family,
        fontSize: size,
        fontWeight: w,
        height: height,
      );

  // display + titles
  static final display = _s(Tokens.fsDisplay, _b, Tokens.lhTight).copyWith(letterSpacing: -1.6, fontFeatures: tabular);
  static final countdown = _s(Tokens.fsCountdown, _b, Tokens.lhTight).copyWith(letterSpacing: -1.5, fontFeatures: tabular);
  static final mega = _s(Tokens.fsMega, _b, Tokens.lhTight).copyWith(letterSpacing: -1.4, fontFeatures: tabular);
  static final hero = _s(Tokens.fsHero, _b, Tokens.lhTight).copyWith(letterSpacing: -1.0);
  static final title = _s(Tokens.fsTitle, _b, Tokens.lhTight).copyWith(letterSpacing: -0.6);
  static final xxl = _s(Tokens.fsXxl, _sb, Tokens.lhSnug).copyWith(letterSpacing: -0.4);
  static final xl = _s(Tokens.fsXl, _sb, Tokens.lhSnug).copyWith(letterSpacing: -0.2);

  // body
  static final subtitle = _s(Tokens.fsSubtitle, _sb, Tokens.lhSnug);
  static final headline = _s(Tokens.fsHeadline, _sb, Tokens.lhSnug);
  static final lg = _s(Tokens.fsLg, _m, Tokens.lhNormal);
  static final lgRegular = _s(Tokens.fsLg, _r, Tokens.lhRelaxed);
  static final callout = _s(Tokens.fsCallout, _m, Tokens.lhSnug);
  static final md = _s(Tokens.fsMd, _r, Tokens.lhRelaxed);
  static final sub = _s(Tokens.fsSub, _r, Tokens.lhNormal);
  static final sm = _s(Tokens.fsSm, _r, Tokens.lhNormal);

  // small caps-ish labels — eyebrows, section headers, tags
  static final eyebrow = _s(Tokens.fsXs, _sb, Tokens.lhNormal).copyWith(letterSpacing: 0.9);
  static final tabLabel = _s(Tokens.fsXxs, _m, Tokens.lhTight);
  static final micro = _s(Tokens.fs2xs, _sb, Tokens.lhTight).copyWith(letterSpacing: 0.8);

  /// Numbers that must line up in a column.
  static TextStyle numeric(TextStyle base) => base.copyWith(fontFeatures: tabular);

  static TextTheme textTheme(Color primary, Color secondary) => TextTheme(
        displayLarge: display.copyWith(color: primary),
        displayMedium: hero.copyWith(color: primary),
        headlineLarge: title.copyWith(color: primary),
        headlineMedium: xxl.copyWith(color: primary),
        headlineSmall: xl.copyWith(color: primary),
        titleLarge: subtitle.copyWith(color: primary),
        titleMedium: headline.copyWith(color: primary),
        titleSmall: callout.copyWith(color: primary),
        bodyLarge: lgRegular.copyWith(color: primary),
        bodyMedium: md.copyWith(color: secondary),
        bodySmall: sub.copyWith(color: secondary),
        labelLarge: callout.copyWith(color: primary),
        labelMedium: tabLabel.copyWith(color: secondary),
        labelSmall: micro.copyWith(color: secondary),
      );
}
