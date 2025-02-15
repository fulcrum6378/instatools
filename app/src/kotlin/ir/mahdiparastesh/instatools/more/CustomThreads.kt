package ir.mahdiparastesh.instatools.more

import androidx.recyclerview.selection.Selection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
abstract class SelectionHandler(selection: Selection<String>) {
    private val list = ArrayList(selection.toList())
    var job: Job? = null
    val active: Boolean get() = job?.isActive == true

    init {
        CoroutineScope(Dispatchers.IO).launch { handle() }
    }

    abstract suspend fun handle()

    protected fun next(): String? = list.firstOrNull()

    protected fun size(): Int = list.size

    open suspend fun ended() {
        list.removeAt(0)
        handle()
    }
}
