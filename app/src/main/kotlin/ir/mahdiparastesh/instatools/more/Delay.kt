package ir.mahdiparastesh.instatools.more

import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.SystemClock

/** Executes codes with a specified amount of delay. */
open class Delay(
    private val timeout: Long = 5000L,
    private val looper: Looper = Looper.myLooper()!!,
    private val listener: () -> Unit,
) {
    private var mStopTimeInFuture = SystemClock.elapsedRealtime() + timeout
    private val mHandler = object : Handler(looper) {
        override fun handleMessage(msg: Message) {
            synchronized(this@Delay) {
                if (mStopTimeInFuture - SystemClock.elapsedRealtime() <= 0)
                    listener()
                else sendMessageDelayed(obtainMessage(MSG), timeout)
            }
        }
    }

    init {
        if (timeout > 0L) mHandler.sendMessage(mHandler.obtainMessage(MSG))
        else listener()
    }

    companion object {
        private const val MSG = 1
    }
}
