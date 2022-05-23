package ir.mahdiparastesh.instatools.more

import android.os.Bundle
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentFactory
import androidx.lifecycle.MutableLiveData
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import ir.mahdiparastesh.instatools.Main
import ir.mahdiparastesh.instatools.R
import kotlin.reflect.KClass

abstract class TriplePageActivity<A, B, C> : BaseActivity()
        where A : BasePage<*>, B : BasePage<*>, C : BasePage<*> {
    protected var page1: A? = null
    protected var page2: B? = null
    protected var page3: C? = null

    abstract val currentPage: MutableLiveData<Int>
    abstract val aKlass: KClass<A>
    abstract val bKlass: KClass<B>
    abstract val cKlass: KClass<C>
    abstract val mode: TripleMode
    abstract fun defPage(): Int

    companion object {
        const val EXTRA_TURN_TO_PAGE = "turnToPage"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        supportFragmentManager.fragmentFactory = PageFactory()
        super.onCreate(savedInstanceState)
    }

    open fun createPages(pager: ViewPager2? = null) {
        currentPage.value = defPage()
        createCurrentPage()
        if (pager == null) {
            val pages = pages()
            for (i in pages.indices) if (pages[i] != null) transFrag().apply {
                if (pages[i]!!.isAdded) remove(pages[i]!!)
                add(R.id.frame, pages[i]!!)
                if (i != currentPage.value) {
                    detach(pages[i]!!)
                    (pages[i] as BasePageMain).ftDetached = true
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
                pages()[it]?.updateShadow()
                pages()[it]?.updateJumper()
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
    protected open fun turnToPage(i: Int): Boolean {
        if (i == currentPage.value || currentPage.value == null) return false
        lastPage = currentPage.value!!
        currentPage.value = i
        val createdCur = createCurrentPage()
        transFrag(lastPage, currentPage.value!!).apply {
            detach(pages()[lastPage]!!)
            if (createdCur) add(R.id.frame, pages()[currentPage.value!!]!!)
            else attach(pages()[currentPage.value!!]!!)
            commit()
        }
        (pages()[lastPage] as BasePageMain).ftDetached = true
        return true
    }

    private var isSelective = false
    open fun selective(bb: Boolean): Boolean { // shall pass
        if (isSelective == bb) return false
        isSelective = bb
        toolbar.menu.clear()
        toolbar.inflateMenu(if (bb) pages()[currentPage.value!!]!!.selectiveMenuRes!! else menuRes!!)
        toolbar.setOnMenuItemClickListener(
            if (isSelective) arrayOf<Toolbar.OnMenuItemClickListener>(
                page1!!, page2!!, page3!!
            )[currentPage.value!!] else this
        )
        if (this is Main) styliseToolbar()
        Delay(100) { onPrepareOptionsMenu(null) }
        return true
    }

    protected fun pageGoBack() = pages()[currentPage.value!!]?.goBack() ?: false

    override fun onDestroy() {
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

    enum class TripleMode { FRAGMENT_MANAGER, VIEW_PAGER }
}
