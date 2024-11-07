package ir.mahdiparastesh.instatools.data

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@androidx.room.Database(
    entities = [Friend::class, Queued::class, Exportable::class, Favourite::class],
    version = 10, exportSchema = false
)
abstract class Database : RoomDatabase() {
    abstract fun dao(): DAO

    @Dao
    interface DAO {
        @Query("SELECT * FROM Friend")
        fun friends(): List<Friend>

        /*@Query("SELECT * FROM Friend WHERE follows = 1")
        fun followers(): List<Friend>*/

        @Query("SELECT * FROM Friend WHERE followed = 1")
        fun following(): List<Friend>

        @Query("SELECT * FROM Friend WHERE followed = 1 AND follows = 0")
        fun unfollowers(): List<Friend>

        /*@Query("SELECT * FROM Friend WHERE id LIKE :id LIMIT 1")
        suspend fun friend(id: String): Friend*/

        /*@Insert
        suspend fun addFriend(item: Friend)*/

        @Insert
        suspend fun addFriends(item: List<Friend>)

        @Update
        suspend fun updateFriend(item: Friend)

        @Delete
        suspend fun deleteFriend(item: Friend)

        @Query("DELETE FROM Friend")
        suspend fun deleteFriends(): Int


        @Query("SELECT * FROM Queued")
        suspend fun queueds(): List<Queued>

        @Query("SELECT * FROM Queued WHERE status = 0")
        fun readyQueueds(): List<Queued>

        @Insert(onConflict = OnConflictStrategy.REPLACE)
        fun addQueued(item: Queued): Long

        @Insert(onConflict = OnConflictStrategy.REPLACE)
        suspend fun addQueueds(item: List<Queued>)

        @Update
        suspend fun updateQueued(item: Queued)

        @Delete
        suspend fun deleteQueued(item: Queued)

        @Query("DELETE FROM Queued")
        fun deleteQueueds(): Int


        @Query("SELECT * FROM Exportable")
        suspend fun exportables(): List<Exportable>

        @Insert(onConflict = OnConflictStrategy.REPLACE)
        suspend fun addExportable(item: Exportable): Long

        @Delete
        suspend fun deleteExportable(item: Exportable)

        /*@Query("DELETE FROM Exportable")
        fun deleteExportables()*/


        @Query("SELECT * FROM Favourite")
        fun favourites(): List<Favourite>

        @Query("SELECT * FROM Favourite WHERE id = :id LIMIT 1")
        fun favourite(id: String): Favourite

        @Query("SELECT * FROM Favourite WHERE user = :user LIMIT 1")
        suspend fun favouriteByUser(user: String): Favourite

        @Insert(onConflict = OnConflictStrategy.REPLACE)
        suspend fun addFavourite(item: Favourite)

        @Update
        suspend fun updateFavourite(item: Favourite)

        @Delete
        fun deleteFavourite(item: Favourite)

        @Query("DELETE FROM Favourite WHERE id = :id")
        suspend fun deleteFavouriteById(id: String)
    }

    companion object {
        fun build(c: Context, user: String) = Room
            .databaseBuilder(c, Database::class.java, "$user.db")
            .addMigrations(object : Migration(9, 10) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("DROP TABLE Followable")
                }
            })
            .fallbackToDestructiveMigration()
            .build()
    }
}
