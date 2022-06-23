package ir.mahdiparastesh.instatools.more

import android.animation.ObjectAnimator
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import androidx.appcompat.widget.AppCompatTextView
import androidx.appcompat.widget.Toolbar
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.contains
import androidx.fragment.app.Fragment
import androidx.lifecycle.MutableLiveData
import androidx.recyclerview.widget.RecyclerView
import com.airbnb.lottie.LottieAnimationView
import com.google.android.material.snackbar.Snackbar
import ir.mahdiparastesh.instatools.R
import ir.mahdiparastesh.instatools.view.UiTools
import ir.mahdiparastesh.instatools.view.UiTools.vis

abstract class BasePage<C> : Fragment(), BackStackOwner,
    Toolbar.OnMenuItemClickListener where C : BaseActivity {

    @Suppress("UNCHECKED_CAST")
    protected val c: C by lazy { activity as C }
    // If you use "get()", it'll throw NullPointerException in picture-in-picture!

    abstract val com: PageCompanion
    abstract val root: ConstraintLayout
    abstract val selectiveMenuRes: Int?
    abstract val messages: Array<Pair<Int, ((msg: Message) -> Unit)>>
    open var afterMessageHandled: () -> Unit = {}

    abstract val bInitialised: Boolean
    open fun rv(): RecyclerView = root.findViewById(R.id.rv)
    open fun empty(): AppCompatTextView? = root.findViewById(R.id.empty)
    open fun error(): ImageView? = root.findViewById(R.id.error)
    open fun loading(): LottieAnimationView? = root.findViewById(R.id.loading)
    open fun jumper(): ImageView = root.findViewById(R.id.jumper)

    abstract class PageCompanion : Alive()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        com.active.value = true
        super.onViewCreated(view, savedInstanceState)

        com.handler = object : Handler(Looper.getMainLooper()) {
            override fun handleMessage(msg: Message) {
                messages.find { it.first == msg.what }?.second?.let { func -> func(msg) }
                afterMessageHandled()
            }
        }

        rv().addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                onRecyclerViewScrolled()
            }
        })
        jumper().apply {
            setOnClickListener { rv().smoothScrollToPosition(0) }
            translationY = UiTools.jumperTrans(c)
        }
        shouldShowJumper.observe(c) {
            anJumper?.cancel()
            anJumper = UiTools.anJumper(c, jumper(), it)
        }
    }

    open fun onLoaded(isEmpty: Boolean, asGuest: Boolean = false) {
        if (loading() != null && root.contains(loading()!!)) {
            loading()?.animation?.cancel()
            root.removeView(loading())
        }
        error()?.vis(false)
        emptied(isEmpty)
    }

    open fun onFailed(message: String) {
        UiTools.snackbar(root, message, Snackbar.LENGTH_LONG)
        if (loading() != null && root.contains(loading()!!)) {
            loading()?.animation?.cancel()
            root.removeView(loading())
        }
        if (rv().adapter == null) error()?.vis()
        emptied(false)
    }

    open fun emptied(isEmpty: Boolean) {
        empty()?.vis(isEmpty)
    }

    override fun onMenuItemClick(item: MenuItem): Boolean = true

    open fun onRecyclerViewScrolled() {
        updateJumper()
    }

    abstract fun updateShadow()

    var shouldShowJumper = MutableLiveData(false)
    private var anJumper: ObjectAnimator? = null
    open fun updateJumper() {
        if (bInitialised) (rv().computeVerticalScrollOffset() > c.dm.heightPixels)
            .apply { if (this != shouldShowJumper.value) shouldShowJumper.value = this }
    }

    override fun onDestroy() {
        com.handler = null
        com.active.value = false
        super.onDestroy()
    }

    companion object {
        const val HANDLE_FETCHED = 0
        const val HANDLE_ABORTED = 1
    }
}
