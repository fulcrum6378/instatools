package ir.mahdiparastesh.instatools.data

import androidx.annotation.Nullable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey

@Entity
class Account(
    @PrimaryKey(autoGenerate = false) @ColumnInfo(name = "id") var id: Long,
    @Nullable @ColumnInfo(name = "user") var user: String? = null,
    @Nullable @ColumnInfo(name = "name") var name: String? = null,
    @Nullable @ColumnInfo(name = "photo") var photo: String? = null,
    @Nullable @ColumnInfo(name = "folder") var folder: String? = null
) {
    @Suppress("unused")
    constructor() : this(0, "", "", null, null)

    class Sort : Comparator<Account> {
        override fun compare(a: Account, b: Account) =
            (a.name ?: "").compareTo(b.name ?: "")
    }
}
