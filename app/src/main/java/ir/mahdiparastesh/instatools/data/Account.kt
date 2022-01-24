package ir.mahdiparastesh.instatools.data

import androidx.annotation.Nullable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey

@Entity
class Account(
    @PrimaryKey(autoGenerate = false) @ColumnInfo(name = "id") var id: Long,
    @ColumnInfo(name = "user") var user: String,
    @ColumnInfo(name = "name") var name: String,
    @Nullable @ColumnInfo(name = "photo") var photo: String?,
    @Ignore @Transient var cookies: HashMap<String, String>? = null
) {
    @Suppress("unused")
    constructor() : this(0, "", "", null, null)

    class Sort : Comparator<Unfollower> {
        override fun compare(a: Unfollower, b: Unfollower) = a.name.compareTo(b.name)
    }
}
