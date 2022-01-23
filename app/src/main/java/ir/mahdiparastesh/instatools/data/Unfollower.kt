package ir.mahdiparastesh.instatools.data

data class Unfollower(
    val id: Long,
    val user: String,
    val name: String,
    val photo: String?,
    val followedBy: Long,
) {
    class Sort :Comparator<Unfollower> {
        override fun compare(a: Unfollower, b: Unfollower) = (a.followedBy - b.followedBy).toInt()
    }
}
