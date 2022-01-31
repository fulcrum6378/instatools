package ir.mahdiparastesh.instatools.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
class Favourite(
    @PrimaryKey var id: Long,
    val user: String,
    val name: String,
    val photo: String?,
    val isPrivate: Boolean
)
