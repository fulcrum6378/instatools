package ir.mahdiparastesh.instatools.job

import ir.mahdiparastesh.instatools.util.Utils
import java.lang.IllegalStateException

/**
 * A structure for handling multiple items as a queue.
 * NOTE: [Queuer] is not responsible for interacting with lists;
 * You must manually add and remove items.
 * This pain is caused by kotlinx-serialization requiring reified generics everywhere!
 */
interface Queuer<Item> {

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
                for (qq in iterator()) {
                    if (shouldSkipForNow(qq)) continue
                    if (q == null) q = qq
                    remaining++
                }
                if (q == null) break

                if (handle(q, remaining)) {
                    onHandled(q, true)
                    consecutiveFailures = 0
                } else {
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

    /** @return a [Collection.iterator] from the queue. */
    fun iterator(): Iterator<Item>

    /** Is this item qualified for [Queuer.handle]? */
    fun shouldSkipForNow(q: Item): Boolean

    /**
     * Handles one item at a time.
     * @return true if it was successful
     */
    fun handle(q: Item, remaining: Int): Boolean

    /**
     * Called when finished handling an item.
     * Remember to explicitly remove the item from the queue.
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
