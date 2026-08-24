import 'package:flutter_test/flutter_test.dart';
import 'package:steadyline/theme/app_colors.dart';
import 'package:steadyline/theme/app_theme.dart';

void main() {
  test('both themes carry the AppColors extension', () {
    expect(AppTheme.dark().extension<AppColors>(), isNotNull);
    expect(AppTheme.light().extension<AppColors>(), isNotNull);
  });

  test('light and dark actually differ where it matters', () {
    final d = AppTheme.dark().extension<AppColors>()!;
    final l = AppTheme.light().extension<AppColors>()!;
    expect(d.bg, isNot(l.bg));
    expect(d.surface, isNot(l.surface));
    expect(d.textPrimary, isNot(l.textPrimary));
  });

  test('brand hues are shared, not re-picked per theme', () {
    final d = AppTheme.dark().extension<AppColors>()!;
    final l = AppTheme.light().extension<AppColors>()!;
    expect(d.brand, l.brand);
    expect(d.success, l.success);
    expect(d.danger, l.danger);
  });

  test('scaffold background follows the theme', () {
    expect(AppTheme.dark().scaffoldBackgroundColor,
        AppTheme.dark().extension<AppColors>()!.bg);
    expect(AppTheme.light().scaffoldBackgroundColor,
        AppTheme.light().extension<AppColors>()!.bg);
  });
}
