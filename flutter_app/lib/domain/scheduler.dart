/// The arithmetic the whole product rests on. Pure — no Flutter, no I/O.
///
/// Ported from design/data.js; the numbers must match the web build exactly.
library;

/// Extra time the syllabus needs beyond a single read: revision passes and
/// mocks. Applied to the raw hour total.
const double revisionMultiplier = 1.28;

class Cushion {
  const Cushion({
    required this.need,
    required this.have,
    required this.gap,
    required this.coverage,
    required this.extraPerDay,
    required this.topicsToDrop,
    required this.daysToPush,
    required this.bufferDays,
  });

  /// Hours the syllabus needs, revision included.
  final int need;

  /// Hours the calendar actually supplies before the exam.
  final int have;

  /// need - have. Positive means short.
  final int gap;

  /// Percent of the syllabus covered, capped at 100.
  final int coverage;

  /// Extra hours per day that would close the gap.
  final double extraPerDay;

  /// Roughly how many topics to drop instead, at ~14h each.
  final int topicsToDrop;

  /// Or how many days to push the exam back.
  final int daysToPush;

  /// When ahead: whole spare days in hand.
  final int bufferDays;

  bool get isShort => gap > 0;
}

int needHours(double rawHours) => (rawHours * revisionMultiplier).round();

/// Full weeks give five weekdays and two weekend days; the remainder is
/// counted as weekdays, matching the web build.
int availableHours({required int days, required double wd, required double we}) {
  final weeks = days ~/ 7;
  final remainder = days % 7;
  return (weeks * (5 * wd + 2 * we) + remainder * wd).round();
}

Cushion cushion({
  required double rawHours,
  required int days,
  required double wd,
  required double we,
}) {
  final need = needHours(rawHours);
  final have = availableHours(days: days, wd: wd, we: we);
  final gap = need - have;
  return Cushion(
    need: need,
    have: have,
    gap: gap,
    coverage: need == 0 ? 0 : (have / need * 100).round().clamp(0, 100),
    extraPerDay: gap > 0 ? double.parse((gap / days).toStringAsFixed(1)) : 0,
    topicsToDrop: gap > 0 ? (gap / 14).ceil() : 0,
    daysToPush: gap > 0 ? (gap / ((wd + we) / 2)).ceil() : 0,
    bufferDays: gap <= 0 ? (gap.abs() / wd).floor() : 0,
  );
}

/// Splits a parent's hours across its children in half-hour steps so the
/// parts sum back to the parent exactly — 7h over three children is
/// 2.5 + 2.5 + 2, never 2.33 repeating.
List<double> splitHours(double total, int n) {
  if (n <= 0) return const [];
  final base = (total / n * 2).floor() / 2;
  final parts = List<double>.filled(n, base);
  var remaining = ((total - base * n) * 2).round() / 2;
  for (var i = 0; remaining > 0.001 && i < n * 8; i++) {
    parts[i % n] += 0.5;
    remaining -= 0.5;
  }
  return parts;
}
