package ir.mahdiparastesh.instatools.more

import android.os.Handler
import android.os.Looper
import android.os.Message
import androidx.recyclerview.selection.Selection

/**
 * Subclass of Thread with a boolean field named "active" which indicates whether the thread is
 * still working or not.
 */
open class BaseThread : Thread() {
    var active = false

    override fun run() {
        active = true
    }

    override fun interrupt() {
        if (!active) return
        active = false
        super.interrupt()
    }
}

/**
 * Subclass of BaseThread, specialised for long-running tasks.
 * It automatically handles Messages and can possess a dedicated Looper thread.
 *
 * When you call "Looper.prepare()", the thread will be dedicated to the act of looping,
 * and therefore it will no longer useful for other purposes!
 */
abstract class LongThread(private val looper: Looper) : BaseThread() {
    var handler: Handler? = null
    abstract val messages: Array<Pair<Int, ((msg: Message) -> Unit)>>

    override fun run() {
        active = true
        super.run()
        handler = object : Handler(looper) {
            override fun handleMessage(msg: Message) {
                messages.find { it.first == msg.what }?.second?.let { func -> func(msg) }
            }
        }
    }

    override fun interrupt() {
        handler = null
        super.interrupt()
    }
}

/** Subclass of BaseThread, it can have interactions with databases. */
abstract class DbRelatedThread(val c: Persistent) : BaseThread() {
    abstract val com: Alive.OfThread

    override fun run() {
        com.active = true
        super.run()
    }

    override fun interrupt() {
        if (!com.active) return
        if (c.dbLazy.isInitialized() && !Alive.anyLiving()) c.db.close()
        com.active = false
        super.interrupt()
    }
}

/**
 * Subclass of DbRelatedThread, it queues the selected IG posts coming from
 * androidx.recyclerview.selection and performs the abstract handle() function on the first item in
 * "list" and deletes it immediately. The first item is available using the function "next()".
 *
 * Do not automate the ended() function, it needs to be called by the implementer of handle().
 */
abstract class SelectionHandler<C>(c: C, selection: Selection<String>) :
    DbRelatedThread(c) where C : Persistent {
    private val list = ArrayList(selection.toList())

    override fun run() {
        super.run()
        handle()
    }

    abstract fun handle()

    protected fun next(): String? = list.firstOrNull()

    protected fun size(): Int = list.size

    open fun ended() {
        list.removeAt(0)
        if (!active) return
        handle()
    }
}
