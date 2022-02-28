package ir.mahdiparastesh.instatools.more

import android.os.Handler

abstract class Alive {
    var active: Boolean = false
    var handler: Handler? = null

    companion object {
        fun anyLiving() = BaseActivity.anyActive() || ForegroundService.anyRunning()
    }
}
