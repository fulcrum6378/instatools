package ir.mahdiparastesh.instatools.api

@Suppress("PropertyName")
interface Rest {
    val status: String

    //class QuickResponse(override val status: String) : Rest

    class LazyList<N>(
        //val auto_load_more_enabled: Boolean,
        val items: Array<N>,
        val more_available: Boolean,
        val next_max_id: String?,
        //val num_results: Float, // in current fetch, not real total
        override val status: String,
    ) : Rest

    class SavedItem(val media: Media)

    class UserInfo(
        val user: User,
        override val status: String
    ) : Rest

    class InboxPage(
        //val viewer: User,
        val inbox: Message.Inbox,
        //val seq_id: String,
        //val snapshot_at_ms: Double, // milliseconds
        //val pending_requests_total: Double,
        //val has_pending_top_requests: Boolean,
        override val status: String
    ) : Rest

    class InboxThread(
        val thread: Message.DmThread,
        override val status: String
    ) : Rest
}
