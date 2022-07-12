package ir.mahdiparastesh.instatools.more

import android.os.Handler
import androidx.lifecycle.MutableLiveData
import ir.mahdiparastesh.instatools.frag.PageSvd
import ir.mahdiparastesh.instatools.frag.PageTag
import ir.mahdiparastesh.instatools.frag.PageUnf
import ir.mahdiparastesh.instatools.frag.PageVwr

abstract class Alive {
    var active = MutableLiveData(false)
    var handler: Handler? = null

    companion object {
        private fun anyDbRelatedRunning() = arrayOf(
            PageUnf.Inquiry, PageSvd.Saver, PageVwr.Saver, PageTag.Saver
        ).any { it.active }

        fun anyLiving() =
            BaseActivity.anyActive() || ForegroundService.anyRunning() || anyDbRelatedRunning()
    }

    abstract class OfThread {
        var active = false
    }
}
