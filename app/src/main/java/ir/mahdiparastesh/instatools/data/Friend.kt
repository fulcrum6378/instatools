package ir.mahdiparastesh.instatools.data

import android.database.sqlite.SQLiteConstraintException
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
class Friend(
    @PrimaryKey @ColumnInfo(name = "id") var id: String,
    @ColumnInfo(name = "user") var user: String,
    @ColumnInfo(name = "name") var name: String,
    @ColumnInfo(name = "photo") var photo: String,
    @ColumnInfo(name = "private") var private: Boolean,
    @ColumnInfo(name = "follows") var follows: Boolean,
    @ColumnInfo(name = "followed") var followed: Boolean
) {
    @Suppress("unused")
    constructor() : this("", "", "", "", false, false, false)

    companion object {
        fun add(dao: Database.DAO, newer: Friend, certainFollower: Boolean?) {
            try {
                dao.addFriend(newer)
            } catch (e: SQLiteConstraintException) {
                dao.updateFriend(newer.apply {
                    val older = dao.friend(id)
                    when (certainFollower) {
                        true -> follows = older.follows
                        false -> followed = older.followed
                        else -> {}
                    }
                })
            }
        }

        fun find(it: Friend, inList: List<Friend>?): Int? {
            if (inList == null) return null
            for (i in inList.indices) if (inList[i].id == it.id) return i
            return null
        }
    }
}
