import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { FocusStore, FocusTarget } from './focus-store';

const target: FocusTarget = {
  chapterId: 'physics.units',
  title: 'Units and Measurements',
  context: 'Physics',
  task: 'Learn',
  minutes: 10,
};

describe('FocusStore elapsed time', () => {
  beforeEach(() => {
    localStorage.clear();
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-09-01T09:00:00.000Z'));
    TestBed.configureTestingModule({});
  });

  afterEach(() => {
    TestBed.inject(FocusStore).discard();
    TestBed.resetTestingModule();
    vi.useRealTimers();
    localStorage.clear();
  });

  it('counts only active segments across pause and resume', () => {
    const focus = TestBed.inject(FocusStore);
    focus.start(target, 10);

    vi.advanceTimersByTime(2 * 60 * 1000);
    focus.pause();
    expect(focus.spentMinutes()).toBe(2);

    vi.advanceTimersByTime(60 * 1000);
    expect(focus.spentMinutes()).toBe(2);

    focus.resume();
    vi.advanceTimersByTime(3 * 60 * 1000);
    focus.stop();

    expect(focus.spentMinutes()).toBe(5);
  });
});
