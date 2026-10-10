// Port of core/data/db/AvailabilityEntity.kt. Tables `weekly_availability`, `availability_override`.
import { dataClass } from '../../domain/kt.js';
import { DayOfWeek, toEpochDay } from '../../domain/time.js';
import { WeeklyAvailability, AvailabilityOverride } from '../../domain/Availability.js';
import { epochDayToDate } from './Converters.js';

export const WeeklyAvailabilityEntity = dataClass({
  id: undefined, attemptId: undefined, dayOfWeek: undefined, startMinuteOfDay: undefined, endMinuteOfDay: undefined,
}, {
  toDomain() {
    return WeeklyAvailability({
      id: this.id,
      attemptId: this.attemptId,
      dayOfWeek: DayOfWeek.valueOf(this.dayOfWeek),
      startMinuteOfDay: this.startMinuteOfDay,
      endMinuteOfDay: this.endMinuteOfDay,
    });
  },
});
WeeklyAvailabilityEntity.tableName = 'weekly_availability';
WeeklyAvailabilityEntity.primaryKeys = ['id'];

export const weeklyToEntity = (w) => WeeklyAvailabilityEntity({
  id: w.id, attemptId: w.attemptId, dayOfWeek: w.dayOfWeek.name,
  startMinuteOfDay: w.startMinuteOfDay, endMinuteOfDay: w.endMinuteOfDay,
});

export const AvailabilityOverrideEntity = dataClass({
  id: undefined, attemptId: undefined, dateEpochDay: undefined, startMinuteOfDay: undefined, endMinuteOfDay: undefined,
}, {
  toDomain() {
    return AvailabilityOverride({
      id: this.id,
      attemptId: this.attemptId,
      date: epochDayToDate(this.dateEpochDay),
      startMinuteOfDay: this.startMinuteOfDay,
      endMinuteOfDay: this.endMinuteOfDay,
    });
  },
});
AvailabilityOverrideEntity.tableName = 'availability_override';
AvailabilityOverrideEntity.primaryKeys = ['id'];

export const overrideToEntity = (o) => AvailabilityOverrideEntity({
  id: o.id, attemptId: o.attemptId, dateEpochDay: toEpochDay(o.date),
  startMinuteOfDay: o.startMinuteOfDay, endMinuteOfDay: o.endMinuteOfDay,
});

/** Kotlin overloads toEntity on WeeklyAvailability / AvailabilityOverride; dispatch on shape. */
export const toEntity = (x) => ('dayOfWeek' in x ? weeklyToEntity(x) : overrideToEntity(x));

export class AvailabilityDao {
  constructor(weekly, overrides) { this.weekly = weekly; this.overrides = overrides; }
  upsertWeekly(entities) { this.weekly.upsertAll(entities); }
  weeklyFor(attemptId) { return this.weekly.rows((r) => r.attemptId === attemptId); }
  deleteWeeklyForAttempt(attemptId) { this.weekly.deleteWhere((r) => r.attemptId === attemptId); }
  upsertOverride(entity) { this.overrides.upsert(entity); }
  overridesFor(attemptId) { return this.overrides.rows((r) => r.attemptId === attemptId); }
  deleteOverridesForAttempt(attemptId) { this.overrides.deleteWhere((r) => r.attemptId === attemptId); }
}
