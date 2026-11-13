package com.pantrypulse.app.data.db

import androidx.room.TypeConverter
import com.pantrypulse.app.domain.Category
import com.pantrypulse.app.domain.HouseholdType
import com.pantrypulse.app.domain.ProductStatus
import java.time.LocalDate

class Converters {
    @TypeConverter fun fromDate(value: LocalDate?): Long? = value?.toEpochDay()
    @TypeConverter fun toDate(value: Long?): LocalDate? = value?.let(LocalDate::ofEpochDay)

    @TypeConverter fun fromCategory(value: Category): String = value.name
    @TypeConverter fun toCategory(value: String): Category =
        Category.entries.firstOrNull { it.name == value } ?: Category.OTHER

    @TypeConverter fun fromStatus(value: ProductStatus): String = value.name
    @TypeConverter fun toStatus(value: String): ProductStatus =
        ProductStatus.entries.firstOrNull { it.name == value } ?: ProductStatus.ACTIVE

    @TypeConverter fun fromHouseholdType(value: HouseholdType): String = value.name
    @TypeConverter fun toHouseholdType(value: String): HouseholdType =
        HouseholdType.entries.firstOrNull { it.name == value } ?: HouseholdType.PERSONAL
}
