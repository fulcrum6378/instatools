package ir.mahdiparastesh.instatools.more

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentFactory
import androidx.lifecycle.MutableLiveData
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.badge.BadgeDrawable
import ir.mahdiparastesh.instatools.Main
import ir.mahdiparastesh.instatools.R
import kotlin.reflect.KClass

abstract class TriplePageActivity<A, B, C> : BaseActivity()
    where A : BasePage<*>, B : BasePage<*>, C : BasePage<*> {
    protected var page1: A? = null
    protected var page2: B? = null
    protected var page3: C? = null

    abstract val currentPage: MutableLiveData<Int> // NON-NULL
    abstract val aKlass: KClass<A>
    abstract val bKlass: KClass<B>
    abstract val cKlass: KClass<C>
    abstract val mode: TripleMode
    abstract fun defPage(): Int

    var selectionBadge: BadgeDrawable? = null

    companion object {
        const val EXTRA_TURN_TO_PAGE = "turnToPage"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        supportFragmentManager.fragmentFactory = PageFactory()
        super.onCreate(savedInstanceState)
    }

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
                } catch (e: NullPointerException) {
                    // getC() might cause it!
                }
            }
        }
    }

    private fun createCurrentPage(): Boolean {
        var created = false
        when (currentPage.value!!) {
            0 -> if (page1 == null) {
                page1 = aKlass.java.newInstance()
                created = true
            }
            1 -> if (page2 == null) {
                page2 = bKlass.java.newInstance()
                created = true
            }
            2 -> if (page3 == null) {
                page3 = cKlass.java.newInstance()
                created = true
            }
        }
        return created
    }

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

    protected fun pages(): Array<BasePage<*>?> = arrayOf(page1, page2, page3)

    protected var lastPage: Int = 0
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

    private var isSelective = false
    open fun selective(bb: Boolean): Boolean { // shall pass
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

    protected fun pageGoBack() = pages()[currentPage.value!!]?.goBack() ?: false

    override fun onDestroy() {
        // don't call transFrag().remove() here: Can not perform this action after onSaveInstanceState!
        page1 = null
        page2 = null
        page3 = null
        super.onDestroy()
    }

    private inner class PageFactory : FragmentFactory() {
        override fun instantiate(loader: ClassLoader, name: String): Fragment = when (name) {
            aKlass.java.name -> {
                if (mode == TripleMode.FRAGMENT_MANAGER && page1 != null && page1?.isAdded == true)
                    transFrag().remove(page1!!).commit()
                aKlass.java.newInstance().also { page1 = it }
            }
            bKlass.java.name -> {
                if (mode == TripleMode.FRAGMENT_MANAGER && page2 != null && page2?.isAdded == true)
                    transFrag().remove(page2!!).commit()
                bKlass.java.newInstance().also { page2 = it }
            }
            cKlass.java.name -> {
                if (mode == TripleMode.FRAGMENT_MANAGER && page3 != null && page3?.isAdded == true)
                    transFrag().remove(page3!!).commit()
                cKlass.java.newInstance().also { page3 = it }
            }
            else -> super.instantiate(loader, name)
        }
    }

    private inner class PageAdapter(c: TriplePageActivity<*, *, *>) : FragmentStateAdapter(c) {
        override fun getItemCount(): Int = 3
        override fun createFragment(i: Int): Fragment = when (i) {
            0 -> aKlass.java.newInstance().also { page1 = it }
            1 -> bKlass.java.newInstance().also { page2 = it }
            2 -> cKlass.java.newInstance().also { page3 = it }
            else -> throw IllegalArgumentException("Page $i?!?")
        }
    }

    @Suppress("unused")
    enum class TripleMode { FRAGMENT_MANAGER, VIEW_PAGER }
}
