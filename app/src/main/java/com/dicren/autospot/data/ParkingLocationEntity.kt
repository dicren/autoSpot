package com.dicren.autospot.data

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity(
    tableName = "parking_locations",
    foreignKeys = [
        ForeignKey(
            entity = VehicleEntity::class,
            parentColumns = ["id"],
            childColumns = ["vehicleId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("vehicleId")],
)
data class ParkingLocationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val vehicleId: Long,
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long,
    /** Street line resolved from [latitude]/[longitude], e.g. "Calle Mayor 12". */
    val street: String? = null,
    /** Town or city resolved from [latitude]/[longitude], e.g. "Getafe". */
    val locality: String? = null,
    /**
     * True once a reverse lookup finished, even when it found nothing: unresolved rows are
     * retried, resolved ones are not. Old rows and spots saved with no network start as false.
     */
    @ColumnInfo(defaultValue = "0")
    val addressResolved: Boolean = false,
)
