package com.dicren.autospot.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ParkingLocationEntity::class, VehicleEntity::class],
    version = 4,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun parkingLocationDao(): ParkingLocationDao
    abstract fun vehicleDao(): VehicleDao

    companion object {
        const val NAME = "parking_database"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    NAME
                )
                    // Pre-v3 schemas are unknown, so drop them. From v3 onward every version
                    // bump must ship an explicit Migration; schemas live in app/schemas/.
                    .addMigrations(MIGRATION_3_4)
                    .fallbackToDestructiveMigrationFrom(dropAllTables = true, 1, 2)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
