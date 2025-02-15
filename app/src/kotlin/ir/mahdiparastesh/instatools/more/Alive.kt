package ir.mahdiparastesh.instatools.more

import android.os.Handler
import ir.mahdiparastesh.instatools.frag.PageSvd
import ir.mahdiparastesh.instatools.frag.PageTag
import ir.mahdiparastesh.instatools.frag.PageUnf
import ir.mahdiparastesh.instatools.frag.PageVwr

/**
 * An abstract class, when implemented gives:
 * - active : a Boolean which indicates if the object is alive or not,
 *            and must be changed during onCreate and onDestroy.
 * - handler : which accepts Messages.
 * In this app we implemented it on companion objects indirectly.
 *
 * @see BaseActivity.ActivityCompanion
 * @see BasePage.PageCompanion
 */
abstract class Alive {
    var active = false
    var handler: Handler? = null

    companion object {
        private fun anyDbRelatedRunning() = arrayOf(
            PageUnf.Inquiry, PageSvd.Saver, PageVwr.Saver, PageTag.Saver
        ).any { it.active }

        fun anyLiving() =
            BaseActivity.anyActive() || ForegroundService.anyRunning() || anyDbRelatedRunning()
    }

    /** Another version of Alive, specialised for Thread subclasses. */
    abstract class OfThread {
        var active = false
    }
}
