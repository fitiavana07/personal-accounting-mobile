package dev.fitiavana.accounting.data.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import dev.fitiavana.accounting.data.model.Account

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts ORDER BY name ASC")
    fun getAll(): LiveData<List<Account>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(account: Account)

    @Query("SELECT COUNT(*) FROM accounts")
    fun count(): Int
}
