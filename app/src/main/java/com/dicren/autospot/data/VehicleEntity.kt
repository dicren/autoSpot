package com.dicren.autospot.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "vehicles",
    indices = [Index(value = ["bluetoothAddress"], unique = true)]
)
data class VehicleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val bluetoothAddress: String,
    val bluetoothName: String,
    val isActive: Boolean = false
)
