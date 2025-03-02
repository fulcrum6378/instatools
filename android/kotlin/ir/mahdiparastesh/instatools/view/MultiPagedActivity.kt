package ir.mahdiparastesh.instatools.view

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentFactory
import androidx.lifecycle.MutableLiveData
import com.google.android.material.badge.BadgeDrawable
import ir.mahdiparastesh.instatools.Main
import ir.mahdiparastesh.instatools.R
import ir.mahdiparastesh.instatools.util.BaseActivity
import ir.mahdiparastesh.instatools.util.BasePage
import ir.mahdiparastesh.instatools.util.Delay
import kotlin.reflect.KClass

/**
 * An abstract subclass of [BaseActivity] which takes multiple classes and each one of them is a
 * subclass of [BasePage]; therefore they'll make a multi-paged activity.
 * This class contains every utility required for a multi-paged activity.
 * In order to implement it, createPages() must be called for rendering the pages.
 */
abstract class MultiPagedActivity(vararg classes: KClass<*>) : BaseActivity() {

    private val classes = arrayOf(*classes)
    val pages: Array<BasePage<*>?> = arrayOfNulls(classes.size)

    /** A LiveData whose value indicates the current page and must never be null. */
    abstract val currentPage: MutableLiveData<Int>

    /** Algorithm to select a page as default. */
    abstract fun defPage(): Int

    /** Indicates the index of the last fragment before switching to a new one. */
    protected var lastPage: Int = 0

    /** @see MultiPagedActivity.selective */
    private var isSelective = false

    /** Holds the BadgeDrawable which enumerates the selected items in RecyclerView. */
    var selectionBadge: BadgeDrawable? = null

    companion object {
        /** Extra value for an intent to turn to a specific page after creation. */
        const val EXTRA_TURN_TO_PAGE = "turnToPage"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        supportFragmentManager.fragmentFactory = PageFactory()
        super.onCreate(savedInstanceState)
    }

    /**
     * This method must be called for rendering the pages, normally inside onCreate().
     * @param toDefaultPage true if it's going to switch to the default page.
     */
    open fun createPages(toDefaultPage: Boolean = true) {
        if (toDefaultPage) currentPage.value = defPage()
        createCurrentPage()
        for (i in pages.indices) if (pages[i] != null) transFrag().apply {
            if (pages[i]!!.isAdded) remove(pages[i]!!)
            add(R.id.frame, pages[i]!!)
            if (i != currentPage.value)
                detach(pages[i]!!)
            try {
                commit()
            } catch (_: IllegalStateException) {
                // Can not perform this action after onSaveInstanceState
            }
        }
        Delay(100) {
            currentPage.observe(this) {
                try {
                    pages[it]?.updateShadow()
                    pages[it]?.updateJumper()
                } catch (_: NullPointerException) {
                    // getC() might cause it!
                }
            }
        }
    }

    /**
     * Creates the fragment attributed to the current page, LAZILY.
     * @return false if it was created before.
     */
    private fun createCurrentPage(): Boolean {
        var created = false
        if (pages[currentPage.value!!] == null) {
            pages[currentPage.value!!] =
                classes[currentPage.value!!].java.getDeclaredConstructor().newInstance()
                    as BasePage<*>?
            created = true
        }

        return created
    }

    /** @return a FragmentTransaction containing animations. */
    private fun transFrag(from: Int? = null, to: Int? = null) =
        supportFragmentManager.beginTransaction().apply {
            if (from == null || to == null || from == to) return@apply
            if (if (!dirRtl) from < to else from > to) setCustomAnimations(
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
        }

    /**
     * Switches to a fragment by index.
     * @return if switching was successful.
     */
    open fun turnToPage(i: Int): Boolean {
        if (i == currentPage.value || currentPage.value == null) return false
        lastPage = currentPage.value!!
        currentPage.value = i
        val createdCur = createCurrentPage()
        transFrag(lastPage, currentPage.value!!).apply {
            pages[lastPage]?.also { detach(it) }
            pages[currentPage.value!!]?.also {
                if (createdCur) add(R.id.frame, it) else attach(it)
            }
            commit()
        }
        return true
    }

    /**
     * Changes the "selective" mode;
     * in this mode the activity shows utilities for selection in RecyclerView.
     *
     * @param bb true if you just turned the selection on, false if you turned it off.
     * @return false if the selective mode was already changed to "bb".
     */
    open fun selective(bb: Boolean): Boolean {
        if (isSelective == bb) return false
        isSelective = bb
        toolbar.menu.clear()
        val page = pages[currentPage.value!!]!!
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
    protected fun pageGoBack() = pages[currentPage.value!!]?.goBack() == true

    override fun onDestroy() {
        // don't call transFrag().remove() here: Can not perform this action after onSaveInstanceState!
        for (p in pages.indices) pages[p] = null
        super.onDestroy()
    }

    /**
     * Creates fragments and assigns them to their variables.
     * Used mostly on a configuration change.
     */
    private inner class PageFactory : FragmentFactory() {
        override fun instantiate(loader: ClassLoader, name: String): Fragment {
            val index = classes.indexOfFirst { it.java.name == name }
            val frag = return classes[index].java.getDeclaredConstructor().newInstance() as Fragment
            pages[index] = frag as BasePage<*>?
            return frag
        }
    }
}
