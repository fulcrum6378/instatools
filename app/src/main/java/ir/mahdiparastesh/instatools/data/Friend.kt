package ir.mahdiparastesh.instatools.data

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Entity
import androidx.room.PrimaryKey
import ir.mahdiparastesh.instatools.frag.PageUnf

@Entity
class Friend(
    @PrimaryKey var id: String,
    var user: String,
    var name: String,
    var photo: String,
    var private: Boolean,
    var follows: Boolean,
    var followed: Boolean,
    var unfollowedMeAt: Long? = null
) {
    @Suppress("unused")
    constructor() : this("", "", "", "", false, false, false)

    companion object {
        @AvoidUiThread
        fun add(
            dao: Database.DAO, thread: PageUnf.Inquiry, newer: Friend, inFollowersList: Boolean
        ) {
            try {
                dao.addFriend(newer)
                thread.newFriends.add(newer)
            } catch (e: SQLiteConstraintException) {
                newer.apply {
                    val older = thread.oldFriends.find { it.id == id } ?: dao.friend(id)
                    if (inFollowersList) {
                        followed = older.followed
                        unfollowedMeAt = null
                    } else {
                        val followsNow = thread.newFriends.find { it.id == id } != null
                        follows = followsNow
                        unfollowedMeAt = if (followsNow) null else older.unfollowedMeAt
                    }

                    dao.updateFriend(this)
                    val index = find(this, thread.newFriends)
                    if (index != null) thread.newFriends[index] = this
                    else thread.newFriends.add(this)
                }
            }
        }

        fun find(it: Friend, inList: List<Friend>?): Int? {
            if (inList == null) return null
            for (i in inList.indices) if (inList[i].id == it.id) return i
            return null
        }
    }

    annotation class AvoidUiThread
}
