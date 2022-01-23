package ir.mahdiparastesh.instatools.data

import android.annotation.SuppressLint
import android.content.Context
import androidx.room.*
import androidx.room.Database
import ir.mahdiparastesh.instatools.Main
import java.io.File

@Database(
    entities = [Account::class, Unfollower::class],
    version = 1, exportSchema = false
)
abstract class Database : RoomDatabase() {
    abstract fun dao(): DAO

    @Dao
    interface DAO {
        @Query("SELECT * FROM Unfollower")
        fun unfollowers(): List<Unfollower>

        @Insert(onConflict = OnConflictStrategy.REPLACE)
        fun addUnfollower(item: Unfollower)

        @Query("DELETE FROM Unfollower")
        fun deleteUnfollowers(): Int
    }

    @SuppressLint("SdCardPath")
    class DbFile(user: String, which: Triple) : File(
        "/data/data/${Main::class.java.`package`!!.name}/databases/$user.db${which.s}"
    ) {
        companion object {
            fun build(c: Context, user: String, mainThread: Boolean = true) = Room.databaseBuilder(
                c, ir.mahdiparastesh.instatools.data.Database::class.java, "$user.db"
            ).apply { if (mainThread) allowMainThreadQueries() }.build()
        }

        @Suppress("unused")
        enum class Triple(val s: String) {
            MAIN(""), SHARED_MEMORY("-shm"), WRITE_AHEAD_LOG("-wal")
        }
    }
}