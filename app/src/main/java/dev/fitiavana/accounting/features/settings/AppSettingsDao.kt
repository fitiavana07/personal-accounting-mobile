package dev.fitiavana.accounting.features.settings

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface AppSettingsDao {
    @Query("SELECT * FROM app_settings WHERE id = 0")
    fun observe(): LiveData<AppSettings?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(settings: AppSettings)
}
