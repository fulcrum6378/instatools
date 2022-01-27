package ir.mahdiparastesh.instatools.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
class Unfollower(
    @PrimaryKey val id: Long,
    val user: String,
    val name: String,
    val photo: String?,
    val followedBy: Long,
) {
    companion object {
        fun find(it: Unfollower, inList: List<Unfollower>): Int? {
            for (i in inList.indices) if (inList[i].id == it.id) return i
            return null
        }
    }
}
