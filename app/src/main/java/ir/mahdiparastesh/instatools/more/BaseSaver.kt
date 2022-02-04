package ir.mahdiparastesh.instatools.more

import androidx.recyclerview.selection.Selection

abstract class BaseSaver(selection: Selection<String>) : BasePage.BaseThread() {
    val list = ArrayList(selection.toList())

    override fun run() {
        super.run()
        handle()
    }

    abstract fun handle()

    fun ended() {
        list.removeAt(0)
        handle()
    }
}
