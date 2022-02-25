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
import androidx.core.view.contains
import androidx.recyclerview.widget.RecyclerView
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
import ir.mahdiparastesh.instatools.view.UiTools.Companion.vis
import ir.mahdiparastesh.instatools.view.UiTools.Companion.vish
import kotlinx.coroutines.runBlocking

class PageUnf(c: Main) : BasePage(c) {
    lateinit var b: PageUnfBinding
    private var thread: Inquiry? = null
    override lateinit var inflater: LayoutInflater
    override var handler: Handler? = object : Handler(Looper.getMainLooper()) {
        @Suppress("UNCHECKED_CAST")
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                HANDLE_LOADED -> (msg.obj as List<Friend>).apply {
                    c.m.unfollowers.value = ArrayList(this)
                    c.m.unfollowers.value!!.sortBy { it.user }
                    if (isNullOrEmpty() && msg.arg1 == 1)
                        thread = Inquiry().also { it.start() }
                    else onLoaded()
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
            b.refresher.isEnabled = false
            guestMode(b.root, BaseActivity.Theme.PRIMARY); return b.root; }

        b.refresher.setOnRefreshListener {
            if (thread?.active == true) return@setOnRefreshListener
            b.rv.adapter = null
            thread = Inquiry().also { it.start() }
        }
        b.rv.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                updateShadow()
                updateJumper()
            }
        })

        //b.refresher.isRefreshing = true
        if (c.m.unfollowers.value != null) onLoaded() else load(true)
        return b.root
    }

    private fun load(initial: Boolean) {
        Thread {
            handler?.obtainMessage(HANDLE_LOADED, if (initial) 1 else 0, 0, c.dao.unfollowers())
                ?.sendToTarget()
        }.start()
    }

    override fun onFailed(message: String) {
        b.refresher.isRefreshing = false
        try {
            Snackbar.make(b.root, message, Snackbar.LENGTH_LONG).show()
        } catch (ignored: IllegalArgumentException) {
        }
        if (b.root.contains(b.loading)) {
            b.loading.animation?.cancel()
            b.root.removeView(b.loading)
        }
        if (b.rv.adapter == null) b.error.vis()
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onLoaded(asGuest: Boolean) {
        b.refresher.isRefreshing = false
        if (b.root.contains(b.loading)) {
            b.loading.animation?.cancel()
            b.root.removeView(b.loading)
        }
        b.error.vis(false)

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
                    .format(c.m.acc!!.id, next_max_id), Rest.Follow::class, handler
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
