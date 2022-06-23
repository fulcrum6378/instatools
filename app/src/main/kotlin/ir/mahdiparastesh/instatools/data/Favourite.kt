package ir.mahdiparastesh.instatools.data

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey

@Entity
class Favourite(
    @PrimaryKey var id: String,
    var user: String,
    var name: String,
    var photo: String?,
    var isPrivate: Boolean,
    @Ignore @Transient var tempDeleted: Boolean = false
) {
    @Suppress("unused")
    constructor() : this("", "", "", "", false)
}
