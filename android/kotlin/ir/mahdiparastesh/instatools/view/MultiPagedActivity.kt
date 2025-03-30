package ir.mahdiparastesh.instatools.view

import android.view.View
import androidx.fragment.app.Fragment
import com.google.android.material.badge.BadgeDrawable
import ir.mahdiparastesh.instatools.Main
import ir.mahdiparastesh.instatools.R
import ir.mahdiparastesh.instatools.util.BaseActivity
import ir.mahdiparastesh.instatools.util.BasePage
import ir.mahdiparastesh.instatools.util.Delay
import kotlin.reflect.KClass

/**
 * A subclass of [BaseActivity] which handles multiple [Fragment]s inside a FrameLayout.
 */
abstract class MultiPagedActivity(vararg classes: KClass<*>) : BaseActivity() {

    private val classes = arrayOf(*classes)

    /** A LiveData whose value indicates the current page and must never be null. */
    abstract var currentPage: Int

    /** Indicates the index of the last fragment before switching to a new one. */
    protected var lastPage: Int = 0

    /** @see MultiPagedActivity.selective */
    private var isSelective = false

    /** Holds the BadgeDrawable which enumerates the selected items in RecyclerView. */
    var selectionBadge: BadgeDrawable? = null

    companion object {
        const val CURRENT_PAGE = "current_page"

        /** Extra value for an intent to turn to a specific page after creation. */
        const val EXTRA_TURN_TO_PAGE = "turnToPage"
    }

    override fun setContentView(view: View?) {
        super.setContentView(view)

        // create the initial page if it doesn't exist
        if (currentPage() == null) {
            val javaClass = classes[currentPage].java
            val fragment = javaClass.getDeclaredConstructor().newInstance() as Fragment
            supportFragmentManager
                .beginTransaction()
                .add(R.id.frame, fragment, CURRENT_PAGE)
                .commit()
        }
    }

    /**
     * Switches to a fragment by index.
     * @return if switching was successful.
     */
    open fun turnToPage(i: Int): Boolean {
        if (i == currentPage) return false
        lastPage = currentPage
        currentPage = i
        supportFragmentManager.beginTransaction().apply {
            if (if (!dirRtl) lastPage < i else lastPage > i) setCustomAnimations(
                R.anim.enter_from_right,
                R.anim.exit_to_left,
                R.anim.enter_from_left,
                R.anim.exit_to_right
            ) else setCustomAnimations(
                R.anim.enter_from_left,
                R.anim.exit_to_right,
                R.anim.enter_from_right,
                R.anim.exit_to_left
            )
            replace(
                R.id.frame,
                classes[i].java.getDeclaredConstructor().newInstance() as BasePage<*>,
                CURRENT_PAGE
            )
            commit()
        }
        return true
    }

    fun currentPage(): BasePage<*>? =
        supportFragmentManager.findFragmentByTag(CURRENT_PAGE) as BasePage<*>?

    /**
     * Changes the "selective" mode;
     * in this mode the activity shows utilities for selection in a RecyclerView.
     *
     * @param bb true if you just turned the selection on, false if you turned it off.
     * @return false if the selective mode was already changed to "bb".
     */
    open fun selective(bb: Boolean): Boolean {
        if (isSelective == bb) return false
        isSelective = bb
        toolbar.menu.clear()
        val page = currentPage()!!
        toolbar.inflateMenu(if (bb) page.selectiveMenuRes!! else menuRes!!)
        toolbar.setOnMenuItemClickListener(if (isSelective) page else this)
        if (this is Main) styliseToolbar()
        Delay(100) { onPrepareOptionsMenu(toolbar.menu) }
        return true
    }

    /**
     * Invokes the current fragment to process the onBackPressed action for its own.
     * @return false, if the fragment didn't have anything to do with onBackPressed.
     */
    protected fun pageGoBack() = currentPage()?.goBack() == true
}
