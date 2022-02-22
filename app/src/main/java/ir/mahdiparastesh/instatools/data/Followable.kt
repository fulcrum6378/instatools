package ir.mahdiparastesh.instatools.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
class Followable(
    @PrimaryKey var id: String,
    var user: String,
    var priv: Boolean,
) {
}
