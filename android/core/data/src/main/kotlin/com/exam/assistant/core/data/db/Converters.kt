package com.exam.assistant.core.data.db

import androidx.room.TypeConverter
import java.time.LocalDate

/**
 * Room entities use primitive types only (epoch day / epoch millis / enum
 * name strings) — LocalDate/DayOfWeek/enums are domain-layer concepts and
 * never cross into Room annotations directly. These converters are the only
 * place that boundary is crossed.
 */
class Converters {
    @TypeConverter
    fun fromEpochDay(epochDay: Long?): LocalDate? = epochDay?.let { LocalDate.ofEpochDay(it) }

    @TypeConverter
    fun toEpochDay(date: LocalDate?): Long? = date?.toEpochDay()
}
