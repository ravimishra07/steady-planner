import { Commitment } from '../onboarding/commitments';
import { PACK } from '../onboarding/exam-pack';
import { freeWindows, layOutDay } from './scheduler';

const fixed: Commitment[] = [
  { id: 'lunch', label: 'Lunch', kind: 'meal', startMinute: 13 * 60, minutes: 60, days: [1] },
  { id: 'class', label: 'Class', kind: 'coaching', startMinute: 16 * 60, minutes: 120, days: [1] },
];

describe('desktop planner scheduling rules', () => {
  it('builds free windows around fixed commitments and the morning buffer', () => {
    expect(freeWindows(fixed, 1, 6 * 60, 22 * 60)).toEqual([
      { startMinute: 6 * 60 + 45, minutes: 375 },
      { startMinute: 14 * 60, minutes: 120 },
      { startMinute: 18 * 60, minutes: 240 },
    ]);
  });

  it('packs ordered candidates with breaks and preserves fixed blocks', () => {
    const chapter = PACK.subjects[0].sections[0].chapters[0];
    const windows = freeWindows(fixed, 1, 6 * 60, 22 * 60);
    const blocks = layOutDay(
      windows,
      fixed,
      1,
      [
        { task: 'Learn', chapter, minutes: 45 },
        { task: 'Practice', chapter, minutes: 60 },
        { task: 'Revise', chapter, minutes: 30 },
      ],
      180,
      'Coaching',
      15,
    );

    expect(blocks.filter((block) => block.kind === 'fixed')).toHaveLength(2);
    expect(blocks.filter((block) => block.kind === 'study')).toHaveLength(3);
    expect(blocks.some((block) => block.kind === 'break')).toBe(true);
    expect(blocks).toEqual([...blocks].sort((a, b) => a.startMinute - b.startMinute));
  });
});
