package ir.mahdiparastesh.instatools.view

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentFactory
import androidx.lifecycle.MutableLiveData
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.badge.BadgeDrawable
import ir.mahdiparastesh.instatools.Main
import ir.mahdiparastesh.instatools.R
import ir.mahdiparastesh.instatools.more.BaseActivity
import ir.mahdiparastesh.instatools.more.BasePage
import ir.mahdiparastesh.instatools.more.Delay
import kotlin.reflect.KClass

/**
 * An abstract subclass of BaseActivity which takes three generic types and each one of them is a
 * subclass BasePage; therefore they'll make a three-paged activity.
 * This class contains every utility required for a three-paged activity.
 *
 * It has 2 modes of switching between fragments:
 * - TripleMode.FRAGMENT_MANAGER
 * - TripleMode.VIEW_PAGER which uses ViewPager2.
 *
 * In order to implement it, createPages() must be called for rendering the pages.
 *
 * @see BasePage
 */
abstract class TriplePageActivity<A, B, C> : BaseActivity()
    where A : BasePage<*>, B : BasePage<*>, C : BasePage<*> {

    /** Variable that holds the first page (fragment). */
    protected var page1: A? = null

    /** Variable that holds the second page (fragment). */
    protected var page2: B? = null

    /** Variable that holds the third page (fragment). */
    protected var page3: C? = null

    /** A LiveData whose value indicates the current page and must never be null. */
    abstract val currentPage: MutableLiveData<Int>

    /** Kotlin class name of the first page. */
    abstract val aKlass: KClass<A>

    /** Kotlin class name of the second page. */
    abstract val bKlass: KClass<B>

    /** Kotlin class name of the third page. */
    abstract val cKlass: KClass<C>

    /** @see TripleMode */
    abstract val mode: TripleMode

    /** Algorithm to select a page as default. */
    abstract fun defPage(): Int

    /** Indicates the index of the last fragment before switching to a new one. */
    protected var lastPage: Int = 0

    /** @see TriplePageActivity.selective */
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
     *
     * @param pager required only in VIEW_PAGER mode.
     * @param toDefaultPage true if it's going to switch to the default page.
     */
    open fun createPages(pager: ViewPager2? = null, toDefaultPage: Boolean = true) {
        if (toDefaultPage) currentPage.value = defPage()
        createCurrentPage()
        if (pager == null) {
            val pages = pages()
            for (i in pages.indices) if (pages[i] != null) transFrag().apply {
                if (pages[i]!!.isAdded) remove(pages[i]!!)
                add(R.id.frame, pages[i]!!)
                if (i != currentPage.value) {
                    detach(pages[i]!!)
                    (pages[i] as BasePage).ftDetached = true
                }
                commit()
            }
        } else {
            pager.adapter = PageAdapter(this)
            pager.setCurrentItem(currentPage.value!!, false)
            pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(i: Int) {
                    currentPage.value = i
                }
            })
        }
        Delay(100) {
            currentPage.observe(this) {
                try {
                    pages()[it]?.updateShadow()
                    pages()[it]?.updateJumper()
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
        when (currentPage.value!!) {
            0 -> if (page1 == null) {
                page1 = aKlass.java.getDeclaredConstructor().newInstance()
                created = true
            }
            1 -> if (page2 == null) {
                page2 = bKlass.java.getDeclaredConstructor().newInstance()
                created = true
            }
            2 -> if (page3 == null) {
                page3 = cKlass.java.getDeclaredConstructor().newInstance()
                created = true
            }
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

    /** @return an array of variables pointing to each page. */
    protected fun pages(): Array<BasePage<*>?> = arrayOf(page1, page2, page3)

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
            pages().getOrNull(lastPage)?.also { detach(it) }
            pages().getOrNull(currentPage.value!!)?.also {
                if (createdCur) add(R.id.frame, it) else attach(it)
            }
            commit()
        }
        pages()[lastPage]?.ftDetached = true
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
        val page = pages()[currentPage.value!!]!!
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
    protected fun pageGoBack() = pages()[currentPage.value!!]?.goBack() == true

    override fun onDestroy() {
        // don't call transFrag().remove() here: Can not perform this action after onSaveInstanceState!
        page1 = null
        page2 = null
        page3 = null
        super.onDestroy()
    }

    /**
     * Creates fragments and assigns them to their variables. Used mostly on a configuration change.
     * Required for both FRAGMENT_MANAGER and VIEW_PAGER modes.
     */
    private inner class PageFactory : FragmentFactory() {
        override fun instantiate(loader: ClassLoader, name: String): Fragment = when (name) {
            aKlass.java.name -> {
                if (mode == TripleMode.FRAGMENT_MANAGER && page1 != null && page1?.isAdded == true)
                    transFrag().remove(page1!!).commit()
                aKlass.java.getDeclaredConstructor().newInstance().also { page1 = it }
            }
            bKlass.java.name -> {
                if (mode == TripleMode.FRAGMENT_MANAGER && page2 != null && page2?.isAdded == true)
                    transFrag().remove(page2!!).commit()
                bKlass.java.getDeclaredConstructor().newInstance().also { page2 = it }
            }
            cKlass.java.name -> {
                if (mode == TripleMode.FRAGMENT_MANAGER && page3 != null && page3?.isAdded == true)
                    transFrag().remove(page3!!).commit()
                cKlass.java.getDeclaredConstructor().newInstance().also { page3 = it }
            }
            else -> super.instantiate(loader, name)
        }
    }

    /** An adapter for fragments required for VIEW_PAGER mode. */
    private inner class PageAdapter(c: TriplePageActivity<*, *, *>) : FragmentStateAdapter(c) {
        override fun getItemCount(): Int = 3
        override fun createFragment(i: Int): Fragment = when (i) {
            0 -> aKlass.java.getDeclaredConstructor().newInstance().also { page1 = it }
            1 -> bKlass.java.getDeclaredConstructor().newInstance().also { page2 = it }
            2 -> cKlass.java.getDeclaredConstructor().newInstance().also { page3 = it }
            else -> throw IllegalArgumentException("Page $i?!?")
        }
    }

    /**
     * Enumeration used to indicate the method of maintaining the fragments,
     * used in an abstract variable of TriplePageActivity called "mode", which
     * must be set during instantiation.
     *
     * @see TriplePageActivity.mode
     */
    @Suppress("unused")
    enum class TripleMode { FRAGMENT_MANAGER, VIEW_PAGER }
}
