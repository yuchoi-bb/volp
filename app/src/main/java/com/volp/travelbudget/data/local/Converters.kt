package com.volp.travelbudget.data.local

import androidx.room.TypeConverter
import com.volp.travelbudget.domain.model.ExpenseCategory
import java.time.LocalDate

/**
 * Room이 다루지 못하는 타입을 문자열로 바꿔 준다.
 *
 * 항목별 금액표는 `FOOD:120000;LODGING:300000` 형태로 저장한다. 컬럼을 항목마다 두지 않아
 * 나중에 항목이 늘어도 스키마를 바꾸지 않아도 된다.
 */
class Converters {

    @TypeConverter
    fun fromLocalDate(value: LocalDate?): String? = value?.toString()

    @TypeConverter
    fun toLocalDate(value: String?): LocalDate? = value?.let(LocalDate::parse)

    @TypeConverter
    fun fromBudgetMap(value: Map<ExpenseCategory, Long>?): String =
        value.orEmpty().entries.joinToString(separator = ";") { "${it.key.name}:${it.value}" }

    @TypeConverter
    fun toBudgetMap(value: String?): Map<ExpenseCategory, Long> {
        if (value.isNullOrBlank()) return emptyMap()
        return value.split(';').mapNotNull { entry ->
            val name = entry.substringBefore(':', missingDelimiterValue = "")
            val amount = entry.substringAfter(':', missingDelimiterValue = "").toLongOrNull()
            if (name.isBlank() || amount == null) {
                null
            } else {
                ExpenseCategory.fromName(name) to amount
            }
        }.toMap()
    }
}
