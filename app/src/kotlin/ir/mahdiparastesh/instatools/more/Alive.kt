package ir.mahdiparastesh.instatools.more

import android.os.Handler

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
        fun anyLiving() = BaseActivity.anyActive() || ForegroundService.anyRunning()
    }
}
