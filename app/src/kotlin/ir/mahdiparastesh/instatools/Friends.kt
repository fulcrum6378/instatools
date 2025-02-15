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
import ir.mahdiparastesh.instatools.more.UserListActivity
import ir.mahdiparastesh.instatools.view.OnlineDataLoader
import ir.mahdiparastesh.instatools.view.UiTools.vis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.lang.Integer.min
import java.util.concurrent.CopyOnWriteArrayList

class Friends : UserListActivity(), OnlineDataLoader {
    private lateinit var b: FriendsBinding
    val mm: MyModel by viewModels()
    private val friendshipsDataLimit = 500 // no more no less

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
        CoroutineScope(Dispatchers.IO).launch {
            mm.friends.clear()
            mm.friends.addAll(dao.friends())
            friendships(0)
        }
    }

    private suspend fun friendships(index: Int) {
        if (index >= mm.friends.size) {
            withContext(Dispatchers.Main) {
                onLoaded(mm.friends.isEmpty())
                adapt()
                updateCount(mm.friends.size)
            }
            return;}

        Api<Rest.Friendships>(
            this@Friends, Api.Endpoint.FRIENDSHIPS_MANY.url, Rest.Friendships::class,
            handler, "user_ids=" + mm.friends
                .subList(index, min(index + friendshipsDataLimit, mm.friends.size))
                .joinToString(",") { it.id }, method = Request.Method.POST
        ) {
            for (f in mm.friends) it.friendship_statuses[f.id]?.also {
                f.bestie = it.is_bestie
                f.feedFav = it.is_feed_favorite == true
                f.restricted = it.is_restricted == true
            }
            handler?.obtainMessage(0, index, 0, it)?.sendToTarget()
            CoroutineScope(Dispatchers.IO).launch {
                friendships(index + friendshipsDataLimit)
            }
        }
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
}
