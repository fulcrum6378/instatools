package ir.mahdiparastesh.instatools.data

import android.content.Context
import androidx.room.*

@androidx.room.Database(
    entities = [Exportable::class, Favourite::class],
    version = 1, exportSchema = false
)
abstract class Database : RoomDatabase() {
    abstract fun dao(): DAO

    @Dao
    interface DAO {

        @Query("SELECT * FROM Exportable LIMIT 1")
        fun firstExportable(): Exportable?

        @Insert(onConflict = OnConflictStrategy.REPLACE)
        fun addExportable(item: Exportable): Long

        @Delete
        fun deleteExportable(item: Exportable)


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
            /*.addMigrations(object : Migration(9, 10) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("DROP TABLE Followable")
                }
            })*/
            .fallbackToDestructiveMigration()
            .build()
    }
}
