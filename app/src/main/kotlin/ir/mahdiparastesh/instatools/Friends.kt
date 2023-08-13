package ir.mahdiparastesh.instatools

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.lifecycle.ViewModel
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.android.volley.NetworkResponse
import com.android.volley.Request
import ir.mahdiparastesh.instatools.data.Friend
import ir.mahdiparastesh.instatools.databinding.FriendsBinding
import ir.mahdiparastesh.instatools.json.Api
import ir.mahdiparastesh.instatools.json.Rest
import ir.mahdiparastesh.instatools.list.ListFri
import ir.mahdiparastesh.instatools.more.UserListActivity
import ir.mahdiparastesh.instatools.view.UiTools.vis
import java.util.concurrent.CopyOnWriteArrayList

class Friends : UserListActivity() {
    private lateinit var b: FriendsBinding
    val mm: MyModel by viewModels()

    override val menuRes: Int? = null
    override val com: ActivityCompanion get() = Companion
    override val bRefresher: SwipeRefreshLayout get() = b.refresher
    override val bRv: RecyclerView get() = b.rv
    override val bTbShadow: View get() = b.tbShadow
    override val bJumper: ImageView get() = b.jumper

    companion object : ActivityCompanion()

    class MyModel : ViewModel() {
        val friends = CopyOnWriteArrayList<Friend>()
        var statuses: Map<String, Rest.FriendshipStatus>? = null
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
                        b.refresher.isRefreshing = false
                        adapt()
                        updateCount(mm.friends.size)
                    }
                    Api.HANDLE_ERROR -> {
                        val res = msg.obj as? NetworkResponse
                        Toast.makeText(
                            this@Friends, res?.statusCode.toString() + ": " +
                                (res?.data?.toString(Charsets.UTF_8) ?: "null"),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }

        prepare()
        load()
    }

    override fun load() {
        FriLoader().start()
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

    inner class FriLoader : Thread() {
        override fun run() {
            mm.friends.clear()
            mm.friends.addAll(dao.friends())
            Api<Rest.Friendships>(
                this@Friends, Api.Endpoint.FRIENDSHIPS_MANY.url, Rest.Friendships::class,
                handler, "user_ids=" + mm.friends.joinToString(",") { it.id },
                method = Request.Method.POST
            ) { friendships ->
                mm.statuses = friendships.friendship_statuses
                // TODO
                done()
            }
        }

        private fun done() {
            mm.friends.sortBy { it.user }
            handler?.obtainMessage(HANDLE_LOADED)?.sendToTarget()
        }
    }
}
