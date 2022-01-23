package ir.mahdiparastesh.instatools.data

import androidx.annotation.Nullable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
class Account(
    @PrimaryKey(autoGenerate = false) @ColumnInfo(name = "id") val id: Long,
    @ColumnInfo(name = "user") val user: String,
    @ColumnInfo(name = "name") val name: String,
    @Nullable @ColumnInfo(name = "photo") val photo: String?,
) {
    class Sort : Comparator<Unfollower> {
        override fun compare(a: Unfollower, b: Unfollower) = a.name.compareTo(b.name)
    }
}
