package dev.fitiavana.accounting.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface TestNoteDao {
    @Insert
    fun insert(note: TestNote)

    @Query("SELECT * FROM TestNote")
    fun getAll(): List<TestNote>
}
