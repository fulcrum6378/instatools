package ir.mahdiparastesh.instatools.data

import android.content.Context
import androidx.room.*

@androidx.room.Database(
    entities = [Favourite::class],
    version = 1, exportSchema = false
)
abstract class Database : RoomDatabase() {
    abstract fun dao(): DAO

    @Dao
    interface DAO {

        @Query("SELECT * FROM Favourite")
        fun favourites(): List<Favourite>

        /*@Query("SELECT COUNT(*) FROM Favourite")
        fun countFavourites(): Int*/

        @Query("SELECT * FROM Favourite WHERE id = :id LIMIT 1")
        fun favourite(id: String): Favourite?

        @Insert(onConflict = OnConflictStrategy.REPLACE)
        fun addFavourite(item: Favourite)

        @Update
        fun updateFavourite(item: Favourite)

        @Delete
        fun deleteFavourite(item: Favourite)
    }

    companion object {
        fun build(c: Context, user: String) = Room
            .databaseBuilder(c, Database::class.java, "$user.db")
            .fallbackToDestructiveMigration()
            .build()
    }
}
