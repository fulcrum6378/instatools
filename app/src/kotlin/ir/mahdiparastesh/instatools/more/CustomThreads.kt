package ir.mahdiparastesh.instatools.more

import androidx.annotation.MainThread
import androidx.recyclerview.selection.Selection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Subclass of Thread with a boolean field named "active" which indicates whether the thread is
 * still working or not.
 */
abstract class BaseThread : Thread() {
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
 * Subclass of DbRelatedThread, it queues the selected IG posts coming from
 * androidx.recyclerview.selection and performs the abstract handle() function on the first item in
 * "list" and deletes it immediately. The first item is available using the function "next()".
 *
 * Do not automate the ended() function, it needs to be called by the implementer of handle().
 */
abstract class SelectionHandler(selection: Selection<String>) : BaseThread() {
    private val list = ArrayList(selection.toList())

    override fun run() {
        super.run()
        handle()
    }

    abstract fun handle()

    protected fun next(): String? = list.firstOrNull()

    protected fun size(): Int = list.size

    @MainThread // except when called at the bottom of PageSvd$Saver::handle
    open fun ended() {
        list.removeAt(0)
        if (!active) return
        CoroutineScope(Dispatchers.IO).launch { handle() }
    }
}
