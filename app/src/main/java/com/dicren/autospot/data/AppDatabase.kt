package com.dicren.autospot.data

import android.content.Context
import androidx.room3.Database
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver

@Database(
    entities = [ParkingLocationEntity::class, VehicleEntity::class],
    version = 5,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun parkingLocationDao(): ParkingLocationDao

    abstract fun vehicleDao(): VehicleDao

    companion object {
        const val DATABASE_NAME = "parking_database"

        @Volatile
        private var instance: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                val database =
                    Room
                        .databaseBuilder(
                            context.applicationContext,
                            AppDatabase::class.java,
                            DATABASE_NAME,
                        )
                        // Bundled driver: same SQLite build on every device, independent of the
                        // platform's SQLite version.
                        .setDriver(BundledSQLiteDriver())
                        // Pre-v3 schemas are unknown, so drop them. From v3 onward every version
                        // bump must ship an explicit Migration; schemas live in app/schemas/.
                        .addMigrations(MIGRATION_3_4, MIGRATION_4_5)
                        .fallbackToDestructiveMigrationFrom(true, 1, 2)
                        .build()
                instance = database
                database
            }
    }
}
