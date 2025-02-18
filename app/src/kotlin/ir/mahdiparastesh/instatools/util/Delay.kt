package ir.mahdiparastesh.instatools.util

import android.os.Handler
import android.os.Looper
import androidx.annotation.MainThread

/** Executes a Runnable after a specified amount of delay. */
@MainThread
open class Delay(timeout: Long, listener: Runnable) :
    Handler(Looper.myLooper() ?: Looper.getMainLooper()) {
    init {
        postDelayed(listener, timeout)
    }
}
