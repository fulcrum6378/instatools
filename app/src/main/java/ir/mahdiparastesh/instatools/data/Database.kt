package ir.mahdiparastesh.instatools.data

import android.content.Context
import androidx.room.*

@androidx.room.Database(
    entities = [Unfollower::class, Favourite::class, Queued::class, Exportable::class],
    version = 4, exportSchema = false
)
abstract class Database : RoomDatabase() {
    abstract fun dao(): DAO

    @Dao
    interface DAO {
        @Query("SELECT * FROM Unfollower")
        fun unfollowers(): List<Unfollower>

        @Insert(onConflict = OnConflictStrategy.REPLACE)
        fun addUnfollower(item: Unfollower)

        @Delete
        fun deleteUnfollower(item: Unfollower)

        @Query("DELETE FROM Unfollower")
        fun deleteUnfollowers(): Int


        @Query("SELECT * FROM Queued")
        fun queueds(): List<Queued>

        @Query("SELECT * FROM Queued WHERE failed = 0")
        fun readyQueueds(): List<Queued>

        @Insert(onConflict = OnConflictStrategy.REPLACE)
        fun addQueued(item: Queued): Long

        @Update
        fun updateQueued(item: Queued)

        @Delete
        fun deleteQueued(item: Queued)

        @Query("DELETE FROM Queued")
        fun deleteQueueds(): Int


        @Query("SELECT * FROM Exportable")
        fun exportables(): List<Exportable>

        @Insert(onConflict = OnConflictStrategy.REPLACE)
        fun addExportable(item: Exportable): Long

        @Delete
        fun deleteExportable(item: Exportable)


        @Query("SELECT * FROM Favourite")
        fun favourites(): List<Favourite>

        @Query("SELECT * FROM Favourite WHERE id = :id LIMIT 1")
        fun favourite(id: String): List<Favourite>

        @Insert(onConflict = OnConflictStrategy.REPLACE)
        fun addFavourite(item: Favourite)

        @Delete
        fun deleteFavourite(item: Favourite)
    }

    companion object {
        fun build(c: Context, user: String, mainThread: Boolean = true) =
            Room.databaseBuilder(c, Database::class.java, "$user.db")
                .fallbackToDestructiveMigration()
                .apply { if (mainThread) allowMainThreadQueries() }
                .build()
    }
}
