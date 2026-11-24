package com.dicren.autospot.data

import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity(
    tableName = "vehicles",
    indices = [Index(value = ["bluetoothAddress"], unique = true)],
)
data class VehicleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val bluetoothAddress: String,
    val bluetoothName: String,
    val isActive: Boolean = false,
)
