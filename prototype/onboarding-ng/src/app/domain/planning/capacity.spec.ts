import { availableHours, cushion, splitHours } from './capacity';

describe('planning capacity parity', () => {
  it('matches Android for SSC CGL at 4h and 7h over 118 days', () => {
    expect(cushion(634, 118, 4, 7)).toEqual({
      need: 812,
      have: 568,
      gap: 244,
      coverage: 70,
      extraPerDay: 2.1,
      topicsToDrop: 18,
      daysToPush: 45,
      bufferDays: 0,
      isShort: true,
    });
  });

  it('counts leftover days as weekdays', () => {
    const week = availableHours(7, 4, 7);
    const plusOne = availableHours(8, 4, 7);
    expect(week).toBe(34);
    expect(plusOne - week).toBe(4);
  });

  it('splits hours into half-hour parts without losing the total', () => {
    expect(splitHours(7, 3)).toEqual([2.5, 2.5, 2]);
    for (const total of [1, 2, 5, 7, 9, 14, 22, 26, 40]) {
      for (let count = 1; count <= 9; count++) {
        expect(splitHours(total, count).reduce((sum, value) => sum + value, 0)).toBe(total);
      }
    }
    expect(splitHours(7, 0)).toEqual([]);
  });
});
