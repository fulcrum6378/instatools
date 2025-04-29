package ir.mahdiparastesh.instatools.job

import ir.mahdiparastesh.instatools.R
import ir.mahdiparastesh.instatools.api.Api
import ir.mahdiparastesh.instatools.api.GraphQl
import ir.mahdiparastesh.instatools.api.GraphQlQuery
import ir.mahdiparastesh.instatools.base.ForegroundService
import ir.mahdiparastesh.instatools.data.Command
import ir.mahdiparastesh.instatools.frag.PageSvd
import ir.mahdiparastesh.instatools.frag.PageTag
import ir.mahdiparastesh.instatools.frag.PageVwr
import ir.mahdiparastesh.instatools.util.Utils
import ir.mahdiparastesh.instatools.view.Notify
import ir.mahdiparastesh.instatools.view.UiTools
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.CancellationException

class CommandService : ForegroundService(), Queuer<Command> {

    override val com: ForegroundServiceCompanion<Command> get() = Companion
    override val ntfChannel: Notify.Channel = Notify.Channel.COMMANDER
    override val ntfId: Int = Notify.ID_COMMANDER
    override lateinit var ntfTitle: String
    override var ntfText: String? = null
    override var ntfSmallText: String? = null
    override val ntfActions: Array<Pair<Int, String>> = arrayOf(R.string.stop to ACTION_STOP)
    override var handledItems: Int = 0

    @Volatile
    override var proceed: Boolean = true

    companion object : ForegroundServiceCompanion<Command>()

    override fun onCreate() {
        super.onCreate()
        if (c.acc == null) return

        ntfManager.cancel(Notify.ID_COMMANDER_ERROR)
        ntfTitle = getString(R.string.commanderTitle)
        initialNotification()

        CoroutineScope(Dispatchers.IO).launch { start() }
    }

    override fun iterator(): Iterator<Command> = c.commands.iterator<Command>()

    override fun shouldSkipForNow(q: Command): Boolean = false

    override fun handle(q: Command, remaining: Int): Boolean {

        // check if it is even necessary?
        if (!q.needsHandling()) {
            c.commands.remove<Command>(q)
            return true
        }

        processingItem = q

        // update the notification
        ntfSmallText = getString(
            R.string.commanderSubtitle,
            q.media.owner().username,
            handledItems, remaining + handledItems
        )
        updateNotification(Pair(handledItems, remaining + handledItems))

        // wait if necessary
        if (handledItems > 0) Thread.sleep(5000L)

        // call the Instagram API
        val posts = q.graphQl()
        var index = 0
        for (post in posts) {
            if (!proceed) throw CancellationException()

            // call the API
            Api.json<GraphQl>(Api.Endpoint.QUERY.url, true, post.body(q.media.id()))
            q.postHandling(post)

            // inform the UIs
            val arg1 = Command.message(post)
            if (post != GraphQlQuery.UNSAVE) PageSvd.handler
                ?.obtainMessage(HANDLE_ITEM_UPDATED, arg1, 0, q.media.uid)?.sendToTarget()
            PageVwr.handler
                ?.obtainMessage(HANDLE_ITEM_UPDATED, arg1, 0, q.media.uid)?.sendToTarget()
            PageTag.handler
                ?.obtainMessage(HANDLE_ITEM_UPDATED, arg1, 0, q.media.uid)?.sendToTarget()

            if (posts.size > 1 && index != posts.size - 1) {
                c.commands.save<Command>()
                Thread.sleep(1000L)
            }
            index++
        }
        return true
    }

    override fun onHandled(q: Command, success: Boolean) {
        processingItem = null
        c.commands.remove<Command>(q)
    }

    override fun onCancel() {
        proceed = false
    }

    override fun onFinished(fatalError: Exception?) {
        ntfSmallText = null
        updateNotification()

        if (proceed) {
            if (fatalError != null) {
                if (fatalError !is Utils.InstaToolsException) throw fatalError

                // report the fatal error
                notifyFailure(Notify.ID_COMMANDER_ERROR, null) {
                    setContentTitle(
                        getString(
                            when {
                                processingItem?.unsave == true -> R.string.unsave
                                processingItem?.like == true -> R.string.like
                                processingItem?.unlike == true -> R.string.unlike
                                else -> R.string.commanderChannel
                            }
                        )
                    )
                    setContentText(
                        when (fatalError) {
                            is Api.FailureException ->
                                UiTools.apiError(c, fatalError.code)
                            is Queuer.FailureException -> resources.getQuantityString(
                                R.plurals.commanderSomeFailed,
                                fatalError.times, fatalError.times
                            )
                            else -> throw IllegalStateException("IMPOSSIBLE?!")
                        }
                    )
                }
            } else {
                // report if some commands failed
                val failedSum = c.commands.size<Command>()
                if (failedSum != 0) notifyFailure(Notify.ID_COMMANDER_SOME_FAILED, null) {
                    setContentTitle(
                        resources.getQuantityString(
                            R.plurals.commanderSomeFailed, failedSum, failedSum
                        )
                    )
                }
            }
        }

        // end the foreground service via the worker thread
        destroy()
    }
}