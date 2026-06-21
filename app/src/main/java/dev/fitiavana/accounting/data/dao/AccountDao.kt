package dev.fitiavana.accounting.data.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import dev.fitiavana.accounting.data.model.Account

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts ORDER BY name ASC")
    fun getAll(): LiveData<List<Account>>

    @Query("SELECT * FROM accounts WHERE id = :id")
    fun getById(id: String): Account?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(account: Account)

    @Update
    fun update(account: Account)

    @Delete
    fun delete(account: Account)

    @Query("SELECT COUNT(*) FROM accounts")
    fun count(): Int

    @Query("SELECT * FROM accounts ORDER BY name ASC")
    fun getAllSync(): List<Account>

    @Query("SELECT COUNT(*) > 0 FROM accounts WHERE instrumentCode = :instrumentCode")
    fun hasAccountsWithInstrument(instrumentCode: String): Boolean
}
