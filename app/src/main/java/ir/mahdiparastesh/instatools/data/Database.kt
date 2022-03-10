package ir.mahdiparastesh.instatools.data

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@androidx.room.Database(
    entities = [
        Friend::class, Queued::class, Exportable::class, Favourite::class, Followable::class
    ], version = 7, exportSchema = false
)
abstract class Database : RoomDatabase() {
    abstract fun dao(): DAO

    @Dao
    interface DAO {
        @Query("SELECT * FROM Friend")
        fun friends(): List<Friend>

        @Query("SELECT * FROM Friend WHERE follows = 1")
        fun followers(): List<Friend>

        @Query("SELECT * FROM Friend WHERE followed = 1")
        fun following(): List<Friend>

        @Query("SELECT * FROM Friend WHERE followed = 1 AND follows = 0")
        fun unfollowers(): List<Friend>

        @Query("SELECT * FROM Friend WHERE id LIKE :id LIMIT 1")
        fun friend(id: String): Friend

        @Insert
        fun addFriend(item: Friend)

        @Update
        fun updateFriend(item: Friend)

        @Delete
        fun deleteFriend(item: Friend)

        @Query("DELETE FROM Friend")
        fun deleteFriends(): Int


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

        @Query("SELECT * FROM Favourite WHERE user = :user LIMIT 1")
        fun favouriteByUser(user: String): List<Favourite>

        @Insert(onConflict = OnConflictStrategy.REPLACE)
        fun addFavourite(item: Favourite)

        @Delete
        fun deleteFavourite(item: Favourite)


        @Query("SELECT * FROM Followable")
        fun followables(): List<Followable>

        @Query("SELECT * FROM Followable LIMIT 1")
        fun aFollowable(): List<Followable>

        @Insert(onConflict = OnConflictStrategy.REPLACE)
        fun addFollowable(item: Followable): Long

        @Insert(onConflict = OnConflictStrategy.REPLACE)
        fun addFollowables(items: List<Followable>): List<Long>

        @Delete
        fun deleteFollowable(item: Followable)

        @Query("DELETE FROM Followable")
        fun deleteFollowables()
    }

    companion object {
        fun build(c: Context, user: String) = Room
            .databaseBuilder(c, Database::class.java, "$user.db")
            //.fallbackToDestructiveMigration()
            .addMigrations(object : Migration(6, 7) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE Friend RENAME COLUMN photo TO pict")
                    db.execSQL("ALTER TABLE Friend ADD COLUMN priv INTEGER NOT NULL DEFAULT 0")
                }
            }).build()
    }
}
