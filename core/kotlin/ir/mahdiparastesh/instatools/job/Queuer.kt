package ir.mahdiparastesh.instatools.job

import ir.mahdiparastesh.instatools.util.Utils
import java.lang.IllegalStateException
import java.util.concurrent.CopyOnWriteArrayList

interface Queuer<Item> {
    val queue: CopyOnWriteArrayList<Item>

    /** Just set it to 0. */
    var q: Int

    /** Counts remaining items in the queue. */
    fun remaining(): Int = queue.size - q

    /** Starts handling the queue. */
    fun start() {
        var consecutiveFailures = 0
        try {
            q = 0
            while (remaining() > 0)
                if (handle(queue[q])) {
                    onSuccess(queue[q])
                    queue.removeAt(q)
                    consecutiveFailures = 0
                } else {
                    onFailure(queue[q])
                    q++
                    consecutiveFailures++
                    if (consecutiveFailures > 5)
                        throw FailureException(consecutiveFailures)
                }
            onFinished()
        } catch (e: Exception) {
            onFatalError(e)
        }
    }

    /**
     * Handles one item at a time.
     * @return true if it was successful
     */
    fun handle(q: Item): Boolean

    fun onSuccess(q: Item)

    fun onFailure(q: Item)

    /** Called when all the queue is finished. */
    fun onFinished()

    fun onFatalError(e: Exception)

    class FailureException(val times: Int) :
        IllegalStateException("$times executive failures detected!"),
        Utils.InstaToolsException
}
