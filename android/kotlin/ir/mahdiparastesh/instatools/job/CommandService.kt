package ir.mahdiparastesh.instatools.job

import ir.mahdiparastesh.instatools.R
import ir.mahdiparastesh.instatools.api.Api
import ir.mahdiparastesh.instatools.api.GraphQl
import ir.mahdiparastesh.instatools.data.Command
import ir.mahdiparastesh.instatools.data.Pickle
import ir.mahdiparastesh.instatools.util.ForegroundService
import ir.mahdiparastesh.instatools.util.Queue
import ir.mahdiparastesh.instatools.util.Utils
import ir.mahdiparastesh.instatools.view.Notify
import ir.mahdiparastesh.instatools.view.UiTools
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class CommandService : ForegroundService(), Queuer<Command> {
    private val pickle: Pickle by lazy {
        Pickle(c.filesDir, m.acc!!.id, Pickle.Type.COMMAND_LIST, null)
    }

    override val klass: Class<*> = CommandService::class.java
    override val com: ForegroundServiceCompanion get() = Companion
    override val ntfChannel: Notify.Channel = Notify.Channel.COMMANDER
    override val ntfId: Int = Notify.ID_COMMANDER
    override lateinit var ntfTitle: String
    override val queue: Queue<Command> get() = m.commands
    override var handledItems: Int = 0
    override var proceed: Boolean = true

    companion object : ForegroundServiceCompanion()

    override fun onCreate() {
        super.onCreate()
        if (m.acc == null) return

        ntfManager.cancel(Notify.ID_COMMANDER_ERROR)
        ntfTitle = getString(R.string.commanderTitle)
        initialNotification()

        CoroutineScope(Dispatchers.IO).launch {
            // load the commands list
            if (m.commands.isEmpty())
                pickle.restore<List<Command>>()
                    ?.also { m.commands.addAll(it) }

            // start looping
            start()
        }
    }

    override fun shouldHandle(q: Command): Boolean = true

    override fun handle(q: Command, remaining: Int): Boolean {

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
        for (post in posts) {
            Api.json<GraphQl>(Api.Endpoint.QUERY.url, true, post.body(q.media.id()))
            if (posts.size > 1) Thread.sleep(1000L)
        }
        return true
    }

    override fun onHandled(q: Command, success: Boolean) {
    }

    override fun onCancel() {
        proceed = false
    }

    override fun onFinished(fatalError: Exception?) {
        ntfSmallText = null
        updateNotification()

        // save data models
        m.commands.pickle<Command>(pickle)

        if (fatalError !is CancellationException) {
            if (fatalError != null) {
                if (fatalError !is Utils.InstaToolsException) throw fatalError

                // report the fatal error
                eventNotification(Notify.ID_COMMANDER_ERROR) {
                    setContentTitle(getString(R.string.unsave)) // FIXME
                    setContentText(
                        when (fatalError) {
                            is Api.FailureException ->
                                UiTools.apiError(c, fatalError.code)
                            is Queuer.FailureException ->
                                getString(R.string.commanderSomeFailed, fatalError.times)
                            else -> throw IllegalStateException("IMPOSSIBLE?!")
                        }
                    )
                    addAction(0, getString(R.string.tryAgain), pi(c, ACTION_START))
                }
            } else {
                // report if some commands failed
                val failedSum = queue.size
                if (failedSum != 0) eventNotification(Notify.ID_COMMANDER_SOME_FAILED) {
                    setContentTitle(getString(R.string.commanderSomeFailed, failedSum))
                    addAction(0, getString(R.string.tryAgain), pi(c, ACTION_START))
                }
            }
        }

        // end the foreground service via the worker thread
        destroy()
    }
}