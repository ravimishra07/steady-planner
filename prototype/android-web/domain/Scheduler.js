// Port of domain/Scheduler.kt — hour splitting for the syllabus tree.

/** Splits a parent's hours across children in half-hour steps so the parts sum back exactly. */
export function splitHours(total, count) {
  if (count <= 0) return [];
  const base = Math.floor((total / count) * 2) / 2;
  const parts = new Array(count).fill(base);
  // Java Math.round(double): floor(x + 0.5)
  let remaining = Math.floor((total - base * count) * 2 + 0.5) / 2.0;
  let i = 0;
  while (remaining > 0.001 && i < count * 8) {
    parts[i % count] = parts[i % count] + 0.5;
    remaining -= 0.5;
    i++;
  }
  return parts;
}
