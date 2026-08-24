import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../theme/app_colors.dart';
import '../theme/app_typography.dart';
import '../theme/theme_controller.dart';
import '../theme/tokens.dart';

/// Temporary. Proves the theme reaches every surface and that switching
/// repaints all of them. Replaced by onboarding once the screens land.
class ThemePreviewScreen extends StatelessWidget {
  const ThemePreviewScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final c = context.colors;
    final controller = context.watch<ThemeController>();

    return Scaffold(
      backgroundColor: c.bg,
      body: SafeArea(
        child: ListView(
          padding: const EdgeInsets.all(Tokens.screenH),
          children: [
            Text('Theme', style: AppTypography.title.copyWith(color: c.textPrimary)),
            const SizedBox(height: Tokens.spaceXs),
            Text(
              'Every value below comes from the theme. Nothing here is hardcoded.',
              style: AppTypography.md.copyWith(color: c.textSecondary),
            ),
            const SizedBox(height: Tokens.spaceXl),

            SegmentedButton<ThemeMode>(
              segments: const [
                ButtonSegment(value: ThemeMode.system, label: Text('System')),
                ButtonSegment(value: ThemeMode.light, label: Text('Light')),
                ButtonSegment(value: ThemeMode.dark, label: Text('Dark')),
              ],
              selected: {controller.mode},
              onSelectionChanged: (s) => controller.set(s.first),
            ),
            const SizedBox(height: Tokens.spaceXl),

            _Swatches(),
            const SizedBox(height: Tokens.spaceXl),

            _Card(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text('Card surface', style: AppTypography.headline.copyWith(color: c.textPrimary)),
                  const SizedBox(height: Tokens.spaceSm),
                  Text(
                    'Hairline border, card shadow, surface colour — all from AppColors.',
                    style: AppTypography.sub.copyWith(color: c.textMuted),
                  ),
                ],
              ),
            ),
            const SizedBox(height: Tokens.spaceLg),

            FilledButton(onPressed: () {}, child: const Text('Primary action')),
            const SizedBox(height: Tokens.spaceSm),
            TextButton(onPressed: () {}, child: const Text('Quiet action')),
          ],
        ),
      ),
    );
  }
}

class _Card extends StatelessWidget {
  const _Card({required this.child});
  final Widget child;

  @override
  Widget build(BuildContext context) {
    final c = context.colors;
    return Container(
      padding: const EdgeInsets.all(Tokens.spaceLg),
      decoration: BoxDecoration(
        color: c.surface,
        borderRadius: BorderRadius.circular(Tokens.rMd),
        border: c.cardBorder,
        boxShadow: c.cardShadow,
      ),
      child: child,
    );
  }
}

class _Swatches extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    final c = context.colors;
    final items = <(String, Color)>[
      ('brand', c.brand),
      ('brandDeep', c.brandDeep),
      ('brandContainer', c.brandContainer),
      ('success', c.success),
      ('warning', c.warning),
      ('danger', c.danger),
      ('surface', c.surface),
      ('elevated', c.elevated),
      ('border', c.border),
    ];
    return Wrap(
      spacing: Tokens.spaceSm,
      runSpacing: Tokens.spaceSm,
      children: [
        for (final (name, color) in items)
          Column(
            children: [
              Container(
                width: 64,
                height: 44,
                decoration: BoxDecoration(
                  color: color,
                  borderRadius: BorderRadius.circular(Tokens.rSm),
                  border: Border.all(color: c.hairline, width: 0.45),
                ),
              ),
              const SizedBox(height: Tokens.spaceXs),
              SizedBox(
                width: 64,
                child: Text(name,
                    style: AppTypography.micro.copyWith(color: c.textMuted),
                    textAlign: TextAlign.center),
              ),
            ],
          ),
      ],
    );
  }
}
