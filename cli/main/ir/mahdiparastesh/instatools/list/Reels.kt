package ir.mahdiparastesh.instatools.list

import ir.mahdiparastesh.instatools.api.Media
import ir.mahdiparastesh.instatools.util.LazyLister
import ir.mahdiparastesh.instatools.util.Profile

class Reels(override val p: Profile) : LazyLister<Media>(), Profile.Section {
    override val numberOfClauses: Int = 1  // TODO

    override fun fetch() {
        p.requireUserId()
        // TODO
    }

    override fun fetch(reset: Boolean) {
        fetchSome(reset)
    }

    override fun download(
        a: Array<String>, offsetOfClauses: Int, opt: HashMap<String, String?>?
    ) {
        // TODO
    }
}
