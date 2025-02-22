package ir.mahdiparastesh.instatools.list

import ir.mahdiparastesh.instatools.Context.api
import ir.mahdiparastesh.instatools.api.Api
import ir.mahdiparastesh.instatools.api.Message
import ir.mahdiparastesh.instatools.api.Rest
import ir.mahdiparastesh.instatools.util.Lister.LazyLister

class Direct : LazyLister<Message.DmThread>() {

    override fun fetch() {
        super.fetch()
        api.call<Rest.InboxPage>(
            Api.Endpoint.INBOX.url.format(cursor ?: ""), Rest.InboxPage::class,
        ).also { page ->
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
}
