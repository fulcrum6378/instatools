package ir.mahdiparastesh.instatools.frag

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.constraintlayout.widget.ConstraintLayout
import com.android.volley.NetworkResponse
import com.google.android.material.snackbar.Snackbar
import ir.mahdiparastesh.instatools.Main
import ir.mahdiparastesh.instatools.R
import ir.mahdiparastesh.instatools.data.Friend
import ir.mahdiparastesh.instatools.databinding.PageUnfBinding
import ir.mahdiparastesh.instatools.json.Api
import ir.mahdiparastesh.instatools.json.Rest
import ir.mahdiparastesh.instatools.list.ListUnf
import ir.mahdiparastesh.instatools.more.*
import ir.mahdiparastesh.instatools.view.UiTools.Companion.vish
import kotlinx.coroutines.runBlocking

class PageUnf(c: Main) : BasePage(c) {
    lateinit var b: PageUnfBinding
    private var thread: Inquiry? = null
    override lateinit var inflater: LayoutInflater
    override val root: ConstraintLayout get() = b.root
    override var handler: Handler? = object : Handler(Looper.getMainLooper()) {
        @Suppress("UNCHECKED_CAST")
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                HANDLE_LOADED -> (msg.obj as List<Friend>).apply {
                    c.m.unfollowers.value = ArrayList(this)
                    c.m.unfollowers.value!!.sortBy { it.user }
                    if (isNullOrEmpty() && msg.arg1 == 1)
                        thread = Inquiry().also { it.start() }
                    else onLoaded(isNullOrEmpty())
                    // TODO: WHAT IF ALL FOLLOWING, FOLLOW BACK!?!
                    // TODO: OR THERE ARE NO FOLLOWERS/FOLLOWING!?!
                }
                HANDLE_FETCHED -> {
                    load(false)
                    b.refresher.isRefreshing = false
                }
                //HANDLE_ABORTED -> onFailed(c.getString(R.string.loadFailed))
                Api.HANDLE_ERROR -> onFailed(
                    c.getString(
                        R.string.unknownError,
                        (msg.obj as NetworkResponse?)?.statusCode.toString()
                    )
                )
                HANDLE_COULD_NOT ->
                    Snackbar.make(b.root, R.string.unfCouldNot, Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    companion object {
        const val HANDLE_LOADED = 2
        const val HANDLE_COULD_NOT = 5
    }

    override fun onCreateView(inf: LayoutInflater, parent: ViewGroup?, state: Bundle?): View {
        inflater = c.themeInflater(BaseActivity.Theme.PRIMARY, inf)
        b = PageUnfBinding.inflate(inflater, parent, false)
        if (Main.guest) {
            guestMode(b.root, BaseActivity.Theme.PRIMARY); return b.root; }

        essentials()
        b.refresher.setOnRefreshListener {
            if (thread?.active == true) return@setOnRefreshListener
            b.rv.adapter = null
            thread = Inquiry().also { it.start() }
        }

        //b.refresher.isRefreshing = true
        if (c.m.unfollowers.value != null) onLoaded(c.m.unfollowers.value.isNullOrEmpty())
        else load(true)
        return b.root
    }

    private fun load(initial: Boolean) {
        Thread {
            handler?.obtainMessage(HANDLE_LOADED, if (initial) 1 else 0, 0, c.dao.unfollowers())
                ?.sendToTarget()
        }.start()
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onLoaded(isEmpty: Boolean, asGuest: Boolean) {
        super.onLoaded(isEmpty, asGuest)
        if (b.rv.adapter == null) b.rv.adapter = ListUnf(c, this)
        else b.rv.adapter?.notifyDataSetChanged()
    }

    override fun onMenuItemClick(item: MenuItem): Boolean = when (item.itemId) {
        else -> false
    }

    override fun updateShadow() {
        c.b.tbShadow.vish(b.rv.computeVerticalScrollOffset() > 0)
    }

    override fun updateJumper() {
    }


    inner class Inquiry : BaseThread() {
        private lateinit var oldFriends: List<Friend>
        private var newFriends = arrayListOf<Friend>()

        init {
            c.m.unfollowers.value = null
        }

        override fun run() {
            super.run()
            runBlocking { oldFriends = c.dao.friends() }
            allFollow(theFollowers = true)
        }

        private fun allFollow(next_max_id: String = "", theFollowers: Boolean) {
            if (!active) return
            Api<Rest.Follow>(
                c, (if (theFollowers) Api.Type.FOLLOWERS else Api.Type.FOLLOWING).url
                    .format(c.m.acc!!.id, next_max_id), Rest.Follow::class,
                handler, onError = { interrupt() }
            ) { flw ->
                Thread {
                    flw.users.forEach { u ->
                        Friend(
                            u.pk, u.username, u.full_name, u.profile_pic_url, u.is_private,
                            theFollowers, !theFollowers
                        ).apply {
                            newFriends.add(this)
                            Friend.add(c.dao, this, theFollowers)
                        }
                    }
                    if (flw.next_max_id == null) {
                        if (theFollowers) Delay(looper = Looper.getMainLooper()) {
                            allFollow(theFollowers = false)
                        } else ended()
                    } else Delay(looper = Looper.getMainLooper()) {
                        allFollow(flw.next_max_id, theFollowers)
                    }
                }.start()
            }
        }

        private fun ended() {
            runBlocking {
                oldFriends.filter { it.id !in newFriends.map { f -> f.id } }
                    .forEach { c.dao.deleteFriend(it) }
                newFriends.forEach { newer ->
                    if (oldFriends.find { it.id == newer.id }?.follows == true && !newer.follows)
                        c.dao.updateFriend(newer.apply { unfollowedMeAt = Persistent.now() })
                }
            }
            handler?.obtainMessage(HANDLE_FETCHED)?.sendToTarget()
            interrupt()
        }
    }
}
