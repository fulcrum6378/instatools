package ir.mahdiparastesh.instatools.util

import ir.mahdiparastesh.instatools.job.SimpleJobs
import ir.mahdiparastesh.instatools.list.Highlights
import ir.mahdiparastesh.instatools.list.Posts
import ir.mahdiparastesh.instatools.list.Stories
import ir.mahdiparastesh.instatools.list.Tagged

class Profile(var userName: String) {
    var userId: String? = null

    val posts: Posts by lazy { Posts(this) }
    val tagged: Tagged by lazy { Tagged(this) }
    val story: Stories by lazy { Stories(this) }
    val highlights: Highlights by lazy { Highlights(this) }

    fun requireUserId() {
        if (userId != null) return
        userId = SimpleJobs.profileInfo(userName).id!!
        if (System.getenv("debug") == "1")
            println("Found the user ID: $userId")
    }

    interface Section {
        val p: Profile

        /** Number of clauses identifying a certain downloadable item, excluding the username itself */
        val numberOfClauses: Int

        fun fetch(reset: Boolean)

        fun download(a: Array<String>, offsetOfClauses: Int, opt: HashMap<String, String?>?)
    }
}
