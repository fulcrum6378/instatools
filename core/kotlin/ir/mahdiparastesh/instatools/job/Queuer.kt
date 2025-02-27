package ir.mahdiparastesh.instatools.job

import ir.mahdiparastesh.instatools.util.Utils
import java.lang.IllegalStateException
import java.util.concurrent.CopyOnWriteArrayList

interface Queuer<Item> where Item : Queuer.Queued {
    val queue: CopyOnWriteArrayList<Item>

    /** Starts handling the queue. */
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
                    onSuccess(q)
                    queue.removeIf { it.id == q!!.id }
                    consecutiveFailures = 0
                } else {
                    q.status = 1
                    onFailure(q)
                    consecutiveFailures++
                    if (consecutiveFailures > 5)
                        throw FailureException(consecutiveFailures)
                }
            }
            onFinished()
            onEnd(true)
        } catch (e: Exception) {
            onFatalError(e)
            onEnd(false)
        }
    }

    /**
     * Handles one item at a time.
     * @return true if it was successful
     */
    fun handle(q: Item, remaining: Int): Boolean

    fun onSuccess(q: Item)

    fun onFailure(q: Item)

    /** Called when all the queue is finished. */
    fun onFinished()

    fun onFatalError(e: Exception)

    fun onEnd(finished: Boolean)


    interface Queued {
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
