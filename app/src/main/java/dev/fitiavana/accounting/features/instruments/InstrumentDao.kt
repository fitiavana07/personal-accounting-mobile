package dev.fitiavana.accounting.features.instruments

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface InstrumentDao {
    @Query("SELECT * FROM instruments ORDER BY code ASC")
    fun getAll(): LiveData<List<Instrument>>

    @Query("SELECT * FROM instruments ORDER BY code ASC")
    fun getAllSync(): List<Instrument>

    @Query("SELECT * FROM instruments WHERE code = :code")
    fun getByCode(code: String): Instrument?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(instrument: Instrument)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(instruments: List<Instrument>)

    @Update
    fun update(instrument: Instrument)

    @Delete
    fun delete(instrument: Instrument)

    @Query("DELETE FROM instruments")
    fun deleteAll()
}