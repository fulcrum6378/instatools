package ir.mahdiparastesh.instatools.job

import ir.mahdiparastesh.instatools.util.Utils
import java.lang.IllegalStateException
import java.util.concurrent.CopyOnWriteArrayList

/** A structure for handling multiple items as a queue. */
interface Queuer<Item> where Item : Queuer.Item {

    /** Main queue repository of a job. */
    val queue: CopyOnWriteArrayList<Item>

    /**
     * The number of handled items; useful for tracking the progress.
     * Just set it to `0`.
     */
    var handledItems: Int

    /** Starts looping over the queue. */
    fun start() {
        var q: Item?
        var remaining: Int
        var consecutiveFailures = 0
        try {
            while (true) {
                q = null
                remaining = 0
                for (qq in queue) {
                    if (!qq.ready()) continue
                    if (q == null) q = qq
                    remaining++
                }
                if (q == null) break

                if (handle(q, remaining)) {
                    onHandled(q, true)
                    queue.removeIf { it.id == q!!.id }
                    consecutiveFailures = 0
                } else {
                    q.status = 1
                    onHandled(q, false)
                    consecutiveFailures++
                    if (consecutiveFailures > 5)
                        throw FailureException(consecutiveFailures)
                }
                handledItems++
            }
            onFinished(null)
        } catch (fatalError: Exception) {
            onFinished(fatalError)
        }
    }

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


    /** A structure for a single item of a [Queuer] */
    interface Item {
        /** A unique ID */
        val id: String

        /** 0=>pending, 1=>failed */
        var status: Byte

        /* A lazy field fo file name */
        val fileName: String


        fun ready() = status == 0.toByte()

        fun isFailed() = status == 1.toByte()
    }

    class FailureException(val times: Int) :
        IllegalStateException("$times executive failures detected!"),
        Utils.InstaToolsException
}
