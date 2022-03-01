package ir.mahdiparastesh.instatools.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
class Followable(
    @PrimaryKey var id: String,
    var user: String,
    var priv: Boolean,
) {
    companion object {
        fun find(it: Followable, inList: List<Followable>): Int? {
            for (i in inList.indices) if (inList[i].id == it.id) return i
            return null
        }
    }
}
