package ir.mahdiparastesh.instatools.data

import android.content.Context
import androidx.room.*

@androidx.room.Database(
    entities = [Unfollower::class, Queued::class],
    version = 1, exportSchema = false
)
abstract class PersonalDb : RoomDatabase() {
    abstract fun dao(): DAO

    @Dao
    interface DAO {
        @Query("SELECT * FROM Unfollower")
        fun unfollowers(): List<Unfollower>

        @Insert(onConflict = OnConflictStrategy.REPLACE)
        fun addUnfollower(item: Unfollower)

        @Query("DELETE FROM Unfollower")
        fun deleteUnfollowers(): Int


        @Query("SELECT * FROM Queued")
        fun queueds(): List<Queued>

        @Insert(onConflict = OnConflictStrategy.REPLACE)
        fun addQueued(item: Queued)

        @Query("DELETE FROM Queued")
        fun deleteQueueds(): Int
    }

    companion object {
        fun build(c: Context, user: String, mainThread: Boolean = true) =
            Room.databaseBuilder(c, PersonalDb::class.java, "$user.db")
                .apply { if (mainThread) allowMainThreadQueries() }.build()
    }
}
