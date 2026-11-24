package com.dicren.autospot.data

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
)
