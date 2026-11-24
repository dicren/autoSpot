package com.dicren.autospot.data

import android.database.SQLException
import kotlinx.coroutines.flow.Flow

sealed interface AddVehicleResult {
    data class Added(
        val id: Long,
    ) : AddVehicleResult

    data class Duplicate(
        val existingId: Long,
    ) : AddVehicleResult
}

interface VehicleRepository {
    val vehicles: Flow<List<VehicleEntity>>
    val activeVehicle: Flow<VehicleEntity?>

    /** Inserts the vehicle and makes it the active one, atomically. */
    suspend fun addVehicle(
        name: String,
        bluetoothAddress: String,
        bluetoothName: String,
    ): AddVehicleResult

    /** Returns false when another vehicle already uses that Bluetooth address. */
    suspend fun updateVehicle(vehicle: VehicleEntity): Boolean

    /** Deletes the vehicle and activates another one if it was the active vehicle. */
    suspend fun deleteVehicle(id: Long)

    suspend fun activateVehicle(id: Long)

    suspend fun getActiveVehicleOnce(): VehicleEntity?

    suspend fun getVehicleByIdOnce(id: Long): VehicleEntity?

    suspend fun getVehicleByBluetoothAddressOnce(address: String): VehicleEntity?

    suspend fun getAllVehiclesOnce(): List<VehicleEntity>
}

class RoomVehicleRepository(
    private val dao: VehicleDao,
) : VehicleRepository {
    override val vehicles: Flow<List<VehicleEntity>> = dao.getAllVehicles()
    override val activeVehicle: Flow<VehicleEntity?> = dao.getActiveVehicle()

    override suspend fun addVehicle(
        name: String,
        bluetoothAddress: String,
        bluetoothName: String,
    ): AddVehicleResult {
        val id =
            dao.insertVehicleAndActivate(
                VehicleEntity(
                    name = name.trim(),
                    bluetoothAddress = bluetoothAddress,
                    bluetoothName = bluetoothName,
                    isActive = false,
                ),
            )
        if (id != -1L) return AddVehicleResult.Added(id)

        // The insert was ignored because the unique index already owns this address.
        val existing =
            dao.getVehicleByBluetoothAddress(bluetoothAddress)
                ?: error("Insert was ignored but no vehicle owns $bluetoothAddress")
        return AddVehicleResult.Duplicate(existing.id)
    }

    override suspend fun updateVehicle(vehicle: VehicleEntity): Boolean {
        val owner = dao.getVehicleByBluetoothAddress(vehicle.bluetoothAddress)
        if (owner != null && owner.id != vehicle.id) return false

        return try {
            dao.updateVehicle(vehicle)
            true
        } catch (e: SQLException) {
            // The unique index is the real guard; a concurrent update can still lose the race.
            // Both SQLite drivers surface it as android.database.SQLException, which also covers
            // the platform's SQLiteConstraintException.
            false
        }
    }

    override suspend fun deleteVehicle(id: Long) = dao.deleteVehicleAndKeepOneActive(id)

    override suspend fun activateVehicle(id: Long) = dao.activateOnly(id)

    override suspend fun getActiveVehicleOnce(): VehicleEntity? = dao.getActiveVehicleOnce()

    override suspend fun getVehicleByIdOnce(id: Long): VehicleEntity? = dao.getVehicleById(id)

    override suspend fun getVehicleByBluetoothAddressOnce(address: String): VehicleEntity? =
        dao.getVehicleByBluetoothAddress(address)

    override suspend fun getAllVehiclesOnce(): List<VehicleEntity> = dao.getAllVehiclesOnce()
}
