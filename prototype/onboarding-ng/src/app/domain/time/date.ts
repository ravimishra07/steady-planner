export function startOfToday(now: Date = new Date()): Date {
  const date = new Date(now);
  date.setHours(0, 0, 0, 0);
  return date;
}
export function addDays(from: Date, days: number): Date {
  const date = new Date(from);
  date.setDate(date.getDate() + days);
  return date;
}
