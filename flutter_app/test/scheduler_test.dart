import 'package:flutter_test/flutter_test.dart';
import 'package:steadyline/domain/scheduler.dart';

void main() {
  group('cushion', () {
    test('matches the web build for SSC CGL at 4h/7h over 118 days', () {
      // 634 raw syllabus hours is the SSC CGL Tier-1 total.
      final c = cushion(rawHours: 634, days: 118, wd: 4, we: 7);
      expect(c.need, 812);
      expect(c.have, 568);
      expect(c.gap, 244);
      expect(c.coverage, 70);
      expect(c.isShort, isTrue);
      expect(c.extraPerDay, 2.1);
      expect(c.topicsToDrop, 18);
      expect(c.daysToPush, 45);
    });

    test('reports a buffer when the calendar supplies more than the syllabus needs', () {
      final c = cushion(rawHours: 634, days: 200, wd: 8, we: 8);
      expect(c.isShort, isFalse);
      expect(c.gap, lessThan(0));
      expect(c.extraPerDay, 0);
      expect(c.bufferDays, greaterThan(0));
    });

    test('remainder days count as weekdays', () {
      // 7 days = one full week; 8 days adds a single weekday.
      final week = availableHours(days: 7, wd: 4, we: 7);
      final plusOne = availableHours(days: 8, wd: 4, we: 7);
      expect(week, 5 * 4 + 2 * 7);
      expect(plusOne - week, 4);
    });
  });

  group('splitHours', () {
    test('7h across three children is 2.5 + 2.5 + 2', () {
      expect(splitHours(7, 3), [2.5, 2.5, 2.0]);
    });

    test('parts always sum back to the parent', () {
      for (final total in [1.0, 2.0, 5.0, 7.0, 9.0, 14.0, 22.0, 26.0, 40.0]) {
        for (var n = 1; n <= 9; n++) {
          final parts = splitHours(total, n);
          expect(parts.length, n);
          expect(parts.reduce((a, b) => a + b), closeTo(total, 0.001),
              reason: 'total $total over $n children');
        }
      }
    });

    test('no child is given zero hours when the parent has enough to share', () {
      expect(splitHours(9, 3).every((h) => h > 0), isTrue);
    });

    test('n of zero is empty rather than a crash', () {
      expect(splitHours(7, 0), isEmpty);
    });
  });
}
