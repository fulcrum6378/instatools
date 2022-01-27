package ir.mahdiparastesh.instatools.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
class Queued(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val userId: String,
    val userName: String,
    val itemId: String,
    val url: String,
    val mediaType: Byte,
    val added: Long,
    val fromSaved: Boolean = false,
    val failed: Boolean = false
)
