package ir.mahdiparastesh.instatools.frag

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.sqlite.SQLiteDatabaseLockedException
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.edit
import androidx.recyclerview.widget.RecyclerView
import ir.mahdiparastesh.instatools.BuildConfig
import ir.mahdiparastesh.instatools.Main
import ir.mahdiparastesh.instatools.R
import ir.mahdiparastesh.instatools.Settings
import ir.mahdiparastesh.instatools.api.Api
import ir.mahdiparastesh.instatools.api.Rest
import ir.mahdiparastesh.instatools.data.Friend
import ir.mahdiparastesh.instatools.data.Friend.Companion.specialSort
import ir.mahdiparastesh.instatools.databinding.PageUnfBinding
import ir.mahdiparastesh.instatools.list.ListUnf
import ir.mahdiparastesh.instatools.util.*
import ir.mahdiparastesh.instatools.view.Expandable
import ir.mahdiparastesh.instatools.view.Notify
import ir.mahdiparastesh.instatools.view.TriplePageActivity
import kotlinx.coroutines.*

class PageUnf : BasePageMain(BaseActivity.Theme.PRIMARY) {
    lateinit var b: PageUnfBinding

    override val root: ConstraintLayout? get() = b.root
    override val rv: RecyclerView? get() = b.rv
    override val empty: View? get() = b.empty
    override val jumper: ImageView? get() = b.jumper
    override val emptyIcon: Int = R.drawable.done_unf
    override val expandable: Expandable? = null
    override val selectiveMenuRes: Int? = null

    override fun isBInitialised(): Boolean = ::b.isInitialized
    override fun isModelLoaded(): Boolean = c.mm.unfollowers.value != null
    override fun isModelEmpty(): Boolean = c.mm.unfollowers.value?.isEmpty() == true
    override fun createAdapter(): RecyclerView.Adapter<*> = ListUnf(c, this)
    override fun canLoadMore(): Boolean = false

    override fun onCreateView(inf: LayoutInflater, parent: ViewGroup?, state: Bundle?): View =
        PageUnfBinding.inflate(inflater, parent, false).let { b = it; it.root }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
    }

    override fun onResume() {
        super.onResume()
        (c.c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .cancel(Notify.ID_UNF_NEW_ITEMS)
    }

    override fun onRefresh() {
        super.onRefresh()
        c.mm.unfollowers.value = null
    }

    private lateinit var oldFriends: List<Friend>
    private val newFriends = arrayListOf<Friend>()
    override suspend fun fetch(reset: Boolean) {
        // load from the database if available
        val unf = ArrayList(c.dao.unfollowers()).apply {
            val favIds = (c.m.fav ?: listOf()).map { it.id }
            for (f in 0 until size) this[f].inFav = this[f].id in favIds
            specialSort()
        }
        if (unf.isNotEmpty() && !reset) {
            withContext(Dispatchers.Main) {
                c.mm.unfollowers.value = unf
                onLoaded()
            }
            return; }

        oldFriends = c.dao.friends()
        allFollow(theFollowers = false)
    }

    /**
     * Fetches a list lazily, whether from followers or following.
     * @next_max_id used for continuing to the next API fetch.
     * @param theFollowers true for followers, false for following.
     */
    private suspend fun allFollow(next_max_id: String = "", theFollowers: Boolean) {
        if (c.m.acc == null) return
        val flw = try {
            Api.json<Rest.Follow>(
                (if (theFollowers) Api.Endpoint.FOLLOWERS else Api.Endpoint.FOLLOWING).url
                    .format(c.m.acc?.id ?: 0, next_max_id)
            )
        } catch (e: Api.FailureException) {
            withContext(Dispatchers.IO) { onFailed(e.code) }
            return
        }
        if (c.m.acc == null) return
        for (u in flw.users!!) {
            val already = newFriends.indexOfFirst { it.id == u.pk }
            if (already > -1) newFriends[already].apply {
                if (theFollowers) follows = true
                else followed = true
            } else newFriends.add(
                Friend(
                    u.id(), u.username!!, u.full_name!!, u.picture(), u.pv(),
                    theFollowers, !theFollowers
                )
            )
        }
        if (flw.next_max_id == null) {
            if (!theFollowers) allFollow(theFollowers = true)
            else ended()
        } else {
            delay(7000)
            allFollow(flw.next_max_id!!, theFollowers)
        }
    }

    /** Updates the database and decides what to do with the new unfollowers. */
    private suspend fun ended() {
        // Update newFriends
        if (c.m.acc == null || !c.db.isOpen) return
        newFriends.forEach { newer ->
            if (newer.follows) newer.unfollowedMeAt = null
            else oldFriends.find { it.id == newer.id }.also { before ->
                if (before?.follows == true && !newer.follows)
                    newer.unfollowedMeAt = Utils.now()
                else newer.unfollowedMeAt = before?.unfollowedMeAt
            }
        }

        // Replace Friends
        if (c.m.acc == null || !c.db.isOpen) return
        try {
            c.dao.deleteFriends()
            c.dao.addFriends(newFriends)
        } catch (e: IllegalStateException) { // DB is closed.
            if (BuildConfig.DEBUG) throw e
        } catch (e: SQLiteDatabaseLockedException) { // perhaps there were heavy transactions then.
            if (BuildConfig.DEBUG) throw e
        }

        // Update the Favourites
        if (c.m.acc == null || !c.db.isOpen) return
        c.m.fav?.forEach { fav ->
            newFriends.find { fav.id == it.id }?.also { friend ->
                fav.user = friend.user
                fav.name = friend.name
                fav.photo = friend.pict
                fav.isPrivate = friend.priv
                c.dao.updateFavourite(fav)
            }
        }

        // Notify the results
        if (c.m.acc == null) return
        withContext(Dispatchers.Main) {
            load(false)
            b.refresher.isRefreshing = false
        }
        val newUnf = newFriends.filter {
            (it.unfollowedMeAt != null
                && it.unfollowedMeAt!! > (c.sp?.getLong(Settings.spNotifiedUnfTill, 0L)
                ?: 0L))
        }
        if (newUnf.isNotEmpty()) {
            withContext(Dispatchers.Main) { gotNewOnes(newUnf.size) }
            // HIGHLIGHT THEM IF YOU WANT
        }
        job = null
    }

    /** Makes a Notification about new unfollowers. */
    private fun gotNewOnes(num: Int) {
        if (c.m.acc == null) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            (c.c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(Notify.Channel.UNF_NEW_ITEMS.create(c.c))
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ActivityCompat.checkSelfPermission(c.c, Manifest.permission.POST_NOTIFICATIONS)
            == PackageManager.PERMISSION_GRANTED
        ) NotificationManagerCompat.from(c.c).notify(
            Notify.ID_UNF_NEW_ITEMS, NotificationCompat.Builder(
                c.c, Notify.Channel.UNF_NEW_ITEMS.id
            ).apply {
                setSmallIcon(R.drawable.notification)
                setContentTitle(c.c.getString(R.string.newUnfNtfChannel))
                setContentText(c.c.getString(R.string.newUnfNtfText, num))
                priority = NotificationCompat.PRIORITY_HIGH
                setContentIntent(
                    PendingIntent.getActivity(
                        c.c, 0, Intent(c.c, Main::class.java)
                            .apply {
                                putExtra(
                                    TriplePageActivity.Companion.EXTRA_TURN_TO_PAGE,
                                    0
                                )
                            },
                        ForegroundService.ntfMutability()
                    )
                )
                setAutoCancel(true)
            }.build()
        )
        c.sp?.edit { putLong(Settings.spNotifiedUnfTill, Utils.now()) }
    } // Never use Fragment::getString()
}
