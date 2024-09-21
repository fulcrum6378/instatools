package ir.mahdiparastesh.instatools

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.View
import androidx.activity.viewModels
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.lifecycle.ViewModel
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.android.volley.NetworkResponse
import com.android.volley.Request
import ir.mahdiparastesh.instatools.data.Friend
import ir.mahdiparastesh.instatools.databinding.FriendsBinding
import ir.mahdiparastesh.instatools.json.Api
import ir.mahdiparastesh.instatools.json.Rest
import ir.mahdiparastesh.instatools.list.ListFri
import ir.mahdiparastesh.instatools.more.LongThread
import ir.mahdiparastesh.instatools.more.UserListActivity
import ir.mahdiparastesh.instatools.view.OnlineDataLoader
import ir.mahdiparastesh.instatools.view.UiTools.vis
import java.lang.Integer.min
import java.util.concurrent.CopyOnWriteArrayList

class Friends : UserListActivity(), OnlineDataLoader {
    private lateinit var b: FriendsBinding
    val mm: MyModel by viewModels()

    override val menuRes: Int? = null
    override val com: ActivityCompanion get() = Companion
    override val root: ConstraintLayout? get() = if (bInitialised) b.root else null
    override val bInitialised: Boolean get() = ::b.isInitialized
    override val bRefresher: SwipeRefreshLayout get() = b.refresher
    override val bTbShadow: View get() = b.tbShadow

    companion object : ActivityCompanion()

    class MyModel : ViewModel() {
        val friends = CopyOnWriteArrayList<Friend>()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = FriendsBinding.inflate(layoutInflater)
        setContentView(b.root)
        initToolbar(b.toolbar, R.string.friends)

        handler = object : Handler(Looper.getMainLooper()) {
            @SuppressLint("UnsafeOptInUsageError")
            override fun handleMessage(msg: Message) {
                when (msg.what) {
                    HANDLE_LOADED -> {
                        onLoaded(mm.friends.isEmpty())
                        adapt()
                        updateCount(mm.friends.size)
                    }
                    Api.HANDLE_ERROR -> onFailed(
                        c.getString(
                            R.string.unknownError, "${(msg.obj as? NetworkResponse)?.statusCode}"
                        )
                    )
                }
            }
        }

        prepareListing(this)
        error()?.setOnClickListener {
            b.refresher.isRefreshing = true
            load()
        }
        load()
    }

    override fun load() {
        FriLoader().start()
    }

    override fun onLoaded(isEmpty: Boolean) {
        b.refresher.isRefreshing = false
        super.onLoaded(isEmpty)
    }

    override fun onFailed(message: String) {
        b.refresher.isRefreshing = false
        super.onFailed(message)
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun adapt() {
        if (mm.friends.isNotEmpty()) {
            if (b.rv.adapter == null) b.rv.adapter = ListFri(this)
            else b.rv.adapter?.notifyDataSetChanged()
        } else {
            b.rv.vis(false)
            b.empty.vis(true)
        }
    }

    inner class FriLoader : LongThread(Looper.getMainLooper()) {
        private val dataLimit = 500 // no more no less

        override val messages: Array<Pair<Int, (msg: Message) -> Unit>> = arrayOf(
            0 to { msg ->
                for (f in mm.friends) (msg.obj as Rest.Friendships).friendship_statuses[f.id]?.also {
                    f.bestie = it.is_bestie
                    f.feedFav = it.is_feed_favorite == true
                    f.restricted = it.is_restricted == true
                }
                statuses(msg.arg1 + dataLimit)
            }
        )

        override fun run() {
            super.run()
            mm.friends.clear()
            mm.friends.addAll(dao.friends())
            statuses(0)
        }

        private fun statuses(n: Int) {
            if (n >= mm.friends.size) {
                interrupt(); return; }
            Api<Rest.Friendships>(
                this@Friends, Api.Endpoint.FRIENDSHIPS_MANY.url, Rest.Friendships::class,
                Friends.handler, "user_ids=" + mm.friends
                    .subList(n, min(n + dataLimit, mm.friends.size))
                    .joinToString(",") { it.id }, method = Request.Method.POST
            ) { handler?.obtainMessage(0, n, 0, it)?.sendToTarget() }
        }

        override fun interrupt() {
            //mm.friends.sortBy { it.bestie }
            //mm.friends.sortBy { it.feedFav }
            Friends.handler?.obtainMessage(HANDLE_LOADED)?.sendToTarget()
            super.interrupt()
        }
    }
}
