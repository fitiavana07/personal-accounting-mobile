package dev.fitiavana.accounting.features.balances

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import dev.fitiavana.accounting.features.balances.AccountBalance

@Dao
interface AccountBalanceDao {
    @Query("SELECT * FROM account_balances")
    fun getAll(): LiveData<List<AccountBalance>>

    @Query("SELECT * FROM account_balances")
    fun getAllSync(): List<AccountBalance>

    @Query("SELECT * FROM account_balances WHERE accountId = :accountId")
    fun getByAccountId(accountId: String): AccountBalance?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(balance: AccountBalance)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(balances: List<AccountBalance>)

    @Query("DELETE FROM account_balances")
    fun deleteAll()
}
