package dev.fitiavana.accounting.features.alerts

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface AlertDao {

    @Query("SELECT * FROM gain_loss_alerts ORDER BY createdAt")
    fun getAll(): LiveData<List<GainLossAlert>>

    @Query("SELECT * FROM gain_loss_alerts ORDER BY createdAt")
    fun getAllSync(): List<GainLossAlert>

    @Query("SELECT * FROM gain_loss_alerts WHERE id = :id")
    fun getSync(id: String): GainLossAlert?

    /** Replacing is safe: an edited alert keeps its id and its state is re-checked against its signature. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(alert: GainLossAlert)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(alerts: List<GainLossAlert>)

    @Query("DELETE FROM gain_loss_alerts WHERE id = :id")
    fun delete(id: String)

    @Query("DELETE FROM gain_loss_alerts")
    fun deleteAll()

    @Query("SELECT * FROM alert_states")
    fun getAllStatesSync(): List<AlertState>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertStates(states: List<AlertState>)
}
