package ir.mahdiparastesh.instatools.more

import android.animation.ObjectAnimator
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.MenuItem
import android.view.View
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.lifecycle.MutableLiveData
import ir.mahdiparastesh.instatools.view.OnlineDataLoader

/** Abstract class for all page fragments which reside inside a BaseActivity. */
abstract class BasePage<C> : Fragment(), Toolbar.OnMenuItemClickListener, OnlineDataLoader
    where C : BaseActivity {
    var ftDetached = false

    @Suppress("UNCHECKED_CAST")
    protected val c: C by lazy { activity as C }
    // If you use "get()", it'll throw NullPointerException in picture-in-picture!

    abstract val com: PageCompanion
    abstract val selectiveMenuRes: Int?
    abstract val messages: Array<Pair<Int, ((msg: Message) -> Unit)>>
    open var afterMessageHandled: () -> Unit = {}
    override val heightPixels: Int by lazy { c.dm.heightPixels }
    override var shouldShowJumper = MutableLiveData(false)
    override var anJumper: ObjectAnimator? = null

    /** Abstract class from which all companion objects of BasePage subclasses must extend. */
    abstract class PageCompanion : Alive()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        com.active = true
        super.onViewCreated(view, savedInstanceState)
        com.handler = object : Handler(Looper.getMainLooper()) {
            override fun handleMessage(msg: Message) {
                messages.find { it.first == msg.what }?.second?.let { func -> func(msg) }
                afterMessageHandled()
            }
        }
        prepareListing(c)
    }

    override fun onResume() {
        super.onResume()
        ftDetached = false
    }

    override fun onMenuItemClick(item: MenuItem): Boolean = true

    /**
     * Handle onBackPressed action for this page.
     * @return false if no action is to be taken.
     */
    open fun goBack(): Boolean {
        return false
    }

    override fun onDestroy() {
        com.handler = null
        com.active = false
        super.onDestroy()
    }

    companion object {
        const val HANDLE_FETCHED = 0
        const val HANDLE_ABORTED = 1
    }
}
