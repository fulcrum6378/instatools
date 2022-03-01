package ir.mahdiparastesh.instatools.more

import android.os.Handler
import androidx.lifecycle.MutableLiveData

abstract class Alive {
    var active = MutableLiveData(false)
    var handler: Handler? = null

    companion object {
        fun anyLiving() = BaseActivity.anyActive() || ForegroundService.anyRunning()
    }
}
