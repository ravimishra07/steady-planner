import { PrototypeStorage } from './prototype-storage';

class MemoryStorage implements Storage {
  private readonly values = new Map<string, string>();
  get length(): number { return this.values.size; }
  clear(): void { this.values.clear(); }
  getItem(key: string): string | null { return this.values.get(key) ?? null; }
  key(index: number): string | null { return [...this.values.keys()][index] ?? null; }
  removeItem(key: string): void { this.values.delete(key); }
  setItem(key: string, value: string): void { this.values.set(key, value); }
}

describe('PrototypeStorage', () => {
  it('namespaces, exports, sizes, and clears only Steadyline data', () => {
    const backing = new MemoryStorage();
    backing.setItem('unrelated', 'keep');
    const storage = new PrototypeStorage(backing);
    storage.write('hours', JSON.stringify(4));
    storage.write('broken', 'raw');

    expect(storage.keys()).toEqual(['broken', 'hours']);
    expect(storage.sizeBytes()).toBe(4);
    expect(storage.export(new Date('2026-09-01T00:00:00.000Z'))).toEqual({
      schemaVersion: 1,
      exportedAt: '2026-09-01T00:00:00.000Z',
      data: { broken: 'raw', hours: 4 },
    });

    storage.clear();
    expect(storage.keys()).toEqual([]);
    expect(backing.getItem('unrelated')).toBe('keep');
  });
});
