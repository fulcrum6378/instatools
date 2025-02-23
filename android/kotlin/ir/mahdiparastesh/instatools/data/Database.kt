package ir.mahdiparastesh.instatools.data

import android.content.Context
import androidx.room.*

@androidx.room.Database(
    entities = [Friend::class, Exportable::class, Favourite::class],
    version = 1, exportSchema = false
)
abstract class Database : RoomDatabase() {
    abstract fun dao(): DAO

    @Dao
    interface DAO {
        @Query("SELECT * FROM Friend")
        suspend fun friends(): List<Friend>

        /*@Query("SELECT * FROM Friend WHERE follows = 1")
        suspend fun followers(): List<Friend>*/

        @Query("SELECT * FROM Friend WHERE followed = 1")
        suspend fun following(): List<Friend>

        @Query("SELECT * FROM Friend WHERE followed = 1 AND follows = 0")
        suspend fun unfollowers(): List<Friend>

        @Insert
        suspend fun addFriends(item: List<Friend>)

        @Update
        suspend fun updateFriend(item: Friend)

        @Delete
        suspend fun deleteFriend(item: Friend)

        @Query("DELETE FROM Friend")
        suspend fun deleteFriends(): Int


        @Query("SELECT * FROM Exportable LIMIT 1")
        suspend fun firstExportable(): Exportable?

        @Insert(onConflict = OnConflictStrategy.REPLACE)
        suspend fun addExportable(item: Exportable): Long

        @Delete
        suspend fun deleteExportable(item: Exportable)


        @Query("SELECT * FROM Favourite")
        suspend fun favourites(): List<Favourite>

        @Query("SELECT * FROM Favourite WHERE id = :id LIMIT 1")
        suspend fun favourite(id: String): Favourite?

        @Insert(onConflict = OnConflictStrategy.REPLACE)
        suspend fun addFavourite(item: Favourite)

        @Update
        suspend fun updateFavourite(item: Favourite)

        @Delete
        suspend fun deleteFavourite(item: Favourite)

        @Query("DELETE FROM Favourite WHERE id = :id")
        suspend fun deleteFavouriteById(id: String)
    }

    companion object {
        fun build(c: Context, user: String) = Room
            .databaseBuilder(c, Database::class.java, "$user.db")
            /*.addMigrations(object : Migration(9, 10) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("DROP TABLE Followable")
                }
            })*/
            .fallbackToDestructiveMigration()
            .build()
    }
}
