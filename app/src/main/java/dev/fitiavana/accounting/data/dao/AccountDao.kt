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

    @Query("SELECT * FROM accounts ORDER BY name ASC")
    fun getAllSync(): List<Account>

    @Query("SELECT * FROM accounts WHERE id = :id")
    fun getById(id: String): Account?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(account: Account)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(accounts: List<Account>)

    @Update
    fun update(account: Account)

    @Delete
    fun delete(account: Account)

    @Query("DELETE FROM accounts")
    fun deleteAll()

    @Query("SELECT COUNT(*) > 0 FROM accounts WHERE instrumentCode = :instrumentCode")
    fun hasAccountsWithInstrument(instrumentCode: String): Boolean

    @Query("SELECT COUNT(*) > 0 FROM accounts WHERE intermediaryInstrumentCode = :instrumentCode")
    fun hasAccountsWithIntermediaryInstrument(instrumentCode: String): Boolean
}
