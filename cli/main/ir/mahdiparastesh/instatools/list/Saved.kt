package ir.mahdiparastesh.instatools.list

import ir.mahdiparastesh.instatools.api.Api
import ir.mahdiparastesh.instatools.api.Media
import ir.mahdiparastesh.instatools.api.Rest
import ir.mahdiparastesh.instatools.util.Lister.LazyLister

class Saved : LazyLister<Media>() {

    override fun fetch() {
        super.fetch()
        val lazyList = Api.json<Rest.LazyList<Rest.SavedItem>>(
            Api.Endpoint.SAVED.url + (cursor?.let { "?max_id=$it" } ?: "")
        )
        var caption: String
        for (i in lazyList.items) {
            caption = i.media.caption?.text?.replace("\n", " ")?.let { ": $it" } ?: ""
            println("$index. ${i.media.link()} - @${i.media.owner().username}$caption")
            add(i.media)
        }
        if (lazyList.more_available) {
            cursor = lazyList.next_max_id
            println("Enter `s` again to load more posts...")
        } else endOfList()
    }
}
