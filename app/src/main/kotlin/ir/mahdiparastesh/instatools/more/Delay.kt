package ir.mahdiparastesh.instatools.more

import android.os.Handler
import android.os.Looper

/** Executes codes with a specified amount of delay. */
open class Delay(timeout: Long, listener: Runnable) : Handler(Looper.myLooper()!!) {
    init {
        postDelayed(listener, timeout)
    }
}

/** Imitates human behaviour in order to fool Instagram's API server :D */
class HumanDelay(range: LongRange = 500L..5000L, runnable: Runnable) :
    Handler(Looper.myLooper()!!) {
    init {
        postDelayed(runnable, range.random())
    }
}
