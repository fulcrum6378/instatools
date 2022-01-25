package ir.mahdiparastesh.instatools.data

import androidx.annotation.Nullable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
class Unfollower(
    @PrimaryKey(autoGenerate = false) @ColumnInfo(name = "id") val id: Long,
    @ColumnInfo(name = "user") val user: String,
    @ColumnInfo(name = "name") val name: String,
    @Nullable @ColumnInfo(name = "photo") val photo: String?,
    @ColumnInfo(name = "followedBy") val followedBy: Long,
) {
    companion object {
        fun find(it: Unfollower, inList: List<Unfollower>): Int? {
            for (i in inList.indices) if (inList[i].id == it.id) return i
            return null
        }
    }

    class Sort : Comparator<Unfollower> {
        override fun compare(a: Unfollower, b: Unfollower) = (a.followedBy - b.followedBy).toInt()
        // TODO: FUCK THIS
    }
}
