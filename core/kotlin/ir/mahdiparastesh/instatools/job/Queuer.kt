package ir.mahdiparastesh.instatools.job

import ir.mahdiparastesh.instatools.util.Queue
import ir.mahdiparastesh.instatools.util.Utils
import java.lang.IllegalStateException
import java.util.concurrent.CancellationException

/** A structure for handling multiple items as a queue. */
interface Queuer<Item> {

    /** Main queue repository of a job. */
    val queue: Queue<Item>

    /**
     * The number of handled items; useful for tracking the progress.
     * Just set it to `0`.
     */
    var handledItems: Int

    /** Whether the loop should proceed or is cancelled. */
    var proceed: Boolean

    /** Starts looping over the queue. */
    fun start() {
        var q: Item?
        var remaining: Int
        var consecutiveFailures = 0
        try {
            while (proceed) {
                q = null
                remaining = 0
                for (qq in queue) {
                    if (!shouldHandle(qq)) continue
                    if (q == null) q = qq
                    remaining++
                }
                if (q == null) break

                if (handle(q, remaining)) {
                    onHandled(q, true)
                    queue.remove(q)
                    consecutiveFailures = 0
                } else {
                    onHandled(q, false)
                    consecutiveFailures++
                    if (consecutiveFailures > 5)
                        throw FailureException(consecutiveFailures)
                }
                handledItems++
            }
            onFinished(if (proceed) null else CancellationException())
        } catch (fatalError: Exception) {
            onFinished(fatalError)
        }
    }

    fun shouldHandle(q: Item): Boolean

    /**
     * Handles one item at a time.
     * @return true if it was successful
     */
    fun handle(q: Item, remaining: Int): Boolean

    /**
     * Called when finished handling an item.
     * @param success true if the item was handled successfully
     */
    fun onHandled(q: Item, success: Boolean)

    /**
     * Called when the loop is over, whether successfully or with fatal errors.
     * @param fatalError
     */
    fun onFinished(fatalError: Exception?)


    class FailureException(val times: Int) :
        IllegalStateException("$times executive failures detected!"),
        Utils.InstaToolsException
}
