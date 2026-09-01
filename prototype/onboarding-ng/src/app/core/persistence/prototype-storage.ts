export interface KeyValueStorage {
  readonly length: number;
  key(index: number): string | null;
  getItem(key: string): string | null;
  setItem(key: string, value: string): void;
  removeItem(key: string): void;
}
export interface PrototypeExport {
  schemaVersion: number;
  exportedAt: string;
  data: Record<string, unknown>;
}

const PREFIX = 'steadyline.';
export const PROTOTYPE_SCHEMA_VERSION = 1;

/** Browser-specific persistence behind one replaceable boundary. */
export class PrototypeStorage {
  constructor(private readonly backing: KeyValueStorage) {}

  read(key: string): string | null { return this.backing.getItem(PREFIX + key); }
  write(key: string, value: string): void { this.backing.setItem(PREFIX + key, value); }

  keys(): string[] {
    const keys: string[] = [];
    for (let index = 0; index < this.backing.length; index++) {
      const key = this.backing.key(index);
      if (key?.startsWith(PREFIX)) keys.push(key.slice(PREFIX.length));
    }
    return keys.sort();
  }

  sizeBytes(): number {
    return this.keys().reduce((bytes, key) => bytes + (this.read(key)?.length ?? 0), 0);
  }

  export(now: Date = new Date()): PrototypeExport {
    const data: Record<string, unknown> = {};
    for (const key of this.keys()) {
      const raw = this.read(key);
      if (raw === null) continue;
      try { data[key] = JSON.parse(raw); }
      catch { data[key] = raw; }
    }
    return { schemaVersion: PROTOTYPE_SCHEMA_VERSION, exportedAt: now.toISOString(), data };
  }

  clear(): void {
    for (const key of this.keys()) this.backing.removeItem(PREFIX + key);
  }
}

/** Resolve lazily so pure domain tests can import modules without a DOM. */
export function browserPrototypeStorage(): PrototypeStorage {
  return new PrototypeStorage(globalThis.localStorage);
}
