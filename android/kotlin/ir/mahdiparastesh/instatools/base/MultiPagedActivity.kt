package ir.mahdiparastesh.instatools.base

import android.view.View
import android.widget.FrameLayout
import androidx.fragment.app.Fragment
import ir.mahdiparastesh.instatools.R
import kotlin.reflect.KClass

/**
 * Subclass of [SelectiveActivity] which handles multiple [Fragment]s inside a [FrameLayout]
 */
abstract class MultiPagedActivity(vararg classes: KClass<*>) : SelectiveActivity() {

    private val classes = arrayOf(*classes)

    /** A LiveData whose value indicates the current page and must never be null */
    abstract var currentPage: Int

    /** Indicates the index of the last fragment before switching to a new one. */
    protected var lastPage: Int = 0

    companion object {
        const val CURRENT_PAGE = "current_page"

        /** Extra value for an intent to turn to a specific page after creation */
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
     * Invokes the current fragment to process the onBackPressed action for its own.
     * @return false, if the fragment didn't have anything to do with onBackPressed.
     */
    protected fun pageGoBack() = currentPage()?.goBack() == true
}
