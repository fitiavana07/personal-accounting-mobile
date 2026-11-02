package dev.fitiavana.accounting.features.chainmonitors

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ChainMonitorDao {

    @Query("SELECT * FROM chain_monitors ORDER BY createdAt")
    fun getAll(): LiveData<List<ChainMonitor>>

    @Query("SELECT * FROM chain_monitors ORDER BY createdAt")
    fun getAllSync(): List<ChainMonitor>

    @Query("SELECT * FROM chain_monitors WHERE id = :id")
    fun getSync(id: String): ChainMonitor?

    /** Replacing is safe: an edited monitor keeps its id and its state is re-checked against its signature. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(monitor: ChainMonitor)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(monitors: List<ChainMonitor>)

    @Query("DELETE FROM chain_monitors WHERE id = :id")
    fun delete(id: String)

    @Query("DELETE FROM chain_monitors")
    fun deleteAll()

    @Query("SELECT * FROM chain_monitor_states")
    fun getAllStatesSync(): List<MonitorState>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertStates(states: List<MonitorState>)
}
