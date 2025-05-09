package ir.mahdiparastesh.instatools.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * We may later integrate these data with Instagram's own favorites feature,
 * but currently it's too difficult because it isn't accessible through the web API!
 */
@Serializable
class Favourite(
    var id: String,
    var user: String,
    var name: String,
    var photo: String?,
    @Transient var tempDeleted: Boolean = false
) {

    override fun hashCode(): Int = id.hashCode()
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as Favourite
        return id == other.id
    }
}
