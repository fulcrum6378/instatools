package ir.mahdiparastesh.instatools.more

import android.os.Handler
import android.os.Looper

/** Executes a Runnable after a specified amount of delay. */
open class Delay(timeout: Long, listener: Runnable) :
    Handler(Looper.myLooper() ?: Looper.getMainLooper()) {
    init {
        postDelayed(listener, timeout)
    }
}

/** Imitates the human behaviour in order to fool the API system of Instagram :D */
class HumanDelay(
    @Suppress("UNUSED_PARAMETER") range: LongRange = 500L..5000L, runnable: Runnable
) : Handler(Looper.myLooper() ?: Looper.getMainLooper()) {
    init {
        //postDelayed(runnable, range.random())
        post(runnable)
    }
}
