package ir.mahdiparastesh.instatools.list

import ir.mahdiparastesh.instatools.Context.api
import ir.mahdiparastesh.instatools.api.Api
import ir.mahdiparastesh.instatools.api.Dm
import ir.mahdiparastesh.instatools.api.Rest
import ir.mahdiparastesh.instatools.util.Lister.LazyLister

class Direct : LazyLister<Dm.DmThread>() {

    override fun fetch() {
        super.fetch()
        val page = api.call<Rest.InboxPage>(Api.Endpoint.INBOX.url.format(cursor ?: ""))
        for (thread in page.inbox.threads) {
            println("$index. ${thread.title()}")
            add(thread)
        }
        if (page.inbox.has_older) {
            cursor = page.inbox.oldest_cursor
            println("Enter `m` again to load more conversations...")
        } else endOfList()
    }
}
