package com.pantrypulse.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        UserEntity::class,
        HouseholdEntity::class,
        MembershipEntity::class,
        LocationEntity::class,
        ProductEntity::class,
        ShoppingItemEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun householdDao(): HouseholdDao
    abstract fun locationDao(): LocationDao
    abstract fun productDao(): ProductDao
    abstract fun shoppingDao(): ShoppingDao

    companion object {
        const val NAME = "pantrypulse.db"

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, NAME).build()

        fun inMemory(context: Context): AppDatabase =
            Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()
    }
}
