import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import 'theme/app_theme.dart';
import 'theme/theme_controller.dart';
import 'screens/theme_preview.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  final theme = ThemeController();
  await theme.load(); // read the saved choice before first paint
  runApp(
    ChangeNotifierProvider.value(value: theme, child: const SteadylineApp()),
  );
}

class SteadylineApp extends StatelessWidget {
  const SteadylineApp({super.key});

  @override
  Widget build(BuildContext context) {
    final mode = context.watch<ThemeController>().mode;
    return MaterialApp(
      title: 'Steadyline',
      debugShowCheckedModeBanner: false,
      theme: AppTheme.light(),
      darkTheme: AppTheme.dark(),
      themeMode: mode,
      home: const ThemePreviewScreen(),
    );
  }
}
