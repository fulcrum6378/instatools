package ir.mahdiparastesh.instatools

import android.animation.ObjectAnimator
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import androidx.activity.viewModels
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.lifecycle.ViewModel
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.badge.BadgeDrawable
import ir.mahdiparastesh.instatools.api.Api
import ir.mahdiparastesh.instatools.api.Rest
import ir.mahdiparastesh.instatools.data.Friend
import ir.mahdiparastesh.instatools.databinding.FriendsBinding
import ir.mahdiparastesh.instatools.list.ListFri
import ir.mahdiparastesh.instatools.util.BaseActivity
import ir.mahdiparastesh.instatools.view.Counter
import ir.mahdiparastesh.instatools.view.OnlineLister
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import java.lang.Integer.min
import java.util.concurrent.CopyOnWriteArrayList

class Friends : BaseActivity(), OnlineLister, Counter {
    private lateinit var b: FriendsBinding
    val mm: MyModel by viewModels()
    private val friendshipsDataLimit = 500 // no more no less

    override val com: ActivityCompanion get() = Companion
    override var job: Job? = null
    override val root: ConstraintLayout? get() = b.root
    override val rv: RecyclerView? get() = b.rv
    override val empty: View? get() = b.empty
    override val jumper: ImageView? get() = b.jumper
    override val menuRes: Int? = null
    override val tbShadow get() = b.tbShadow
    override var shouldShowJumper: Boolean = false
    override var anJumper: ObjectAnimator? = null
    override val expandable = null
    override var countBadge: BadgeDrawable? = null

    override fun isBInitialised(): Boolean = ::b.isInitialized
    override fun isModelLoaded(): Boolean = mm.friends != null
    override fun isModelEmpty(): Boolean = mm.friends.isEmpty() == true
    override fun createAdapter(): RecyclerView.Adapter<*> = ListFri(this)
    override fun screenHeight(): Int = dm.heightPixels
    override fun canLoadMore(): Boolean = false

    companion object : ActivityCompanion()

    class MyModel : ViewModel() {
        val friends = CopyOnWriteArrayList<Friend>()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = FriendsBinding.inflate(layoutInflater)
        setContentView(b.root)
        initToolbar(b.toolbar, R.string.friends)
        prepareListing(this)
    }

    override suspend fun fetch(reset: Boolean) {
        mm.friends.clear()
        mm.friends.addAll(dao.friends())
        friendships(0)
    }

    private suspend fun friendships(index: Int) {
        if (index >= mm.friends.size) {
            withContext(Dispatchers.Main) { onLoaded() }
            return; }

        val rest = Api.call<Rest.Friendships>(
            Api.Endpoint.FRIENDSHIPS_MANY.url, Rest.Friendships::class,
            isPost = true, body = "user_ids=" + mm.friends
                .subList(index, min(index + friendshipsDataLimit, mm.friends.size))
                .joinToString(",") { it.id },
            onError = { code -> onFailed(code) }
        ) ?: return

        for (f in mm.friends) rest.friendship_statuses[f.id]?.also {
            f.bestie = it.is_bestie
            f.feedFav = it.is_feed_favorite == true
            f.restricted = it.is_restricted == true
        }
        friendships(index + friendshipsDataLimit)
    }

    override fun onLoaded() {
        super.onLoaded()
        updateCount(this, mm.friends.size)
    }
}
