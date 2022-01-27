package ir.mahdiparastesh.instatools.data

import android.content.Context
import androidx.room.*

@androidx.room.Database(
    entities = [Account::class], version = 1, exportSchema = false
)
abstract class GlobalDb : RoomDatabase() {
    abstract fun dao(): DAO

    @Dao
    interface DAO {
        @Query("SELECT * FROM Account")
        fun accounts(): List<Account>

        @Query("SELECT * FROM Account WHERE id = :id LIMIT 1")
        fun account(id: Long): Account

        @Insert(onConflict = OnConflictStrategy.REPLACE)
        fun addAccount(item: Account)

        @Update
        fun updateAccount(item: Account)

        @Delete
        fun deleteAccount(item: Account)
    }

    companion object {
        const val file = "global"

        fun build(c: Context, mainThread: Boolean = true) =
            Room.databaseBuilder(c, GlobalDb::class.java, "$file.db")
                .apply { if (mainThread) allowMainThreadQueries() }.build()
    }
}
