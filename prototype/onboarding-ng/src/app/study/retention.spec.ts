import { dueDate, nextInterval, overdueDays, retentionState, setPace, strength } from './retention';

describe('retention rules', () => {
  beforeEach(() => setPace('standard'));

  it('uses the selected interval schedule and recall factor', () => {
    expect(nextInterval(0, 'okay')).toBe(3);
    expect(nextInterval(1, 'shaky')).toBe(5);
    expect(nextInterval(2, 'solid')).toBe(48);
  });

  it('caps the interval against the remaining runway', () => {
    expect(nextInterval(3, 'solid', 30)).toBe(10);
    expect(nextInterval(0, 'shaky', 2)).toBe(1);
  });

  it('derives due dates and human retention states deterministically', () => {
    const learned = new Date(2026, 8, 1);
    expect(dueDate(learned, 0, 'okay')).toEqual(new Date(2026, 8, 4));
    expect(overdueDays('2026-09-04', new Date(2026, 8, 4))).toBe(0);
    expect(retentionState('2026-09-01', '2026-09-04', new Date(2026, 8, 4))).toBe('due');
    expect(retentionState('2026-09-01', '2026-09-04', new Date(2026, 8, 5))).toBe('slipping');
    expect(strength('2026-09-01', '2026-09-04', new Date(2026, 8, 7))).toBe(0);
    expect(retentionState('2026-09-01', '2026-09-04', new Date(2026, 8, 7))).toBe('lost');
  });
});
