package ir.mahdiparastesh.instatools.more

import androidx.recyclerview.selection.Selection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

abstract class BaseSaver(selection: Selection<String>) : BaseThread() {
    val list = ArrayList(selection.toList())

    override fun run() {
        super.run()
        handle()
    }

    abstract fun handle()

    open fun ended() {
        list.removeAt(0)
        CoroutineScope(Dispatchers.IO).launch { handle() }
    }
}
