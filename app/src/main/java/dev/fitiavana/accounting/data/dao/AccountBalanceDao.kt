package dev.fitiavana.accounting.data.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import dev.fitiavana.accounting.data.model.AccountBalance

@Dao
interface AccountBalanceDao {
    @Query("SELECT * FROM account_balances")
    fun getAll(): LiveData<List<AccountBalance>>

    @Query("SELECT * FROM account_balances WHERE accountId = :accountId")
    fun getByAccountId(accountId: String): AccountBalance?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(balance: AccountBalance)
}
