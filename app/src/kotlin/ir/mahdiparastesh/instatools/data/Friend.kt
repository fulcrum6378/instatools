package ir.mahdiparastesh.instatools.data

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import ir.mahdiparastesh.instatools.more.Utils.calendar

@Entity
class Friend(
    @PrimaryKey var id: String,
    var user: String,
    var name: String,
    var pict: String,
    var priv: Boolean,
    var follows: Boolean,
    var followed: Boolean,
    var unfollowedMeAt: Long? = null
) {
    @Ignore
    @Transient
    var inFav: Boolean = false

    @Ignore
    @Transient
    var unfollowed: Boolean = false

    @Ignore
    @Transient
    var bestie: Boolean = false

    @Ignore
    @Transient
    var feedFav: Boolean = false

    @Ignore
    @Transient
    var restricted: Boolean = false

    /*@Ignore
    @Transient
    var muted: Boolean = false*/

    fun toFavourite() = Favourite(id, user, name, pict, priv)

    companion object {
        fun find(it: Friend, inList: List<Friend>?): Int? {
            if (inList == null) return null
            for (i in inList.indices) if (inList[i].id == it.id) return i
            return null
        }

        fun find(id: String, inList: List<Friend>?): Int? {
            if (inList == null) return null
            for (i in inList.indices) if (inList[i].id == id) return i
            return null
        }

        fun ArrayList<Friend>.specialSort() {
            sortBy { it.user }
            sortByDescending { (it.unfollowedMeAt ?: 0L).calendar().time }
            sortBy { it.inFav }
        }
    }
}
