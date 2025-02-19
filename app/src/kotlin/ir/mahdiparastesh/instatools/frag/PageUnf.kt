package ir.mahdiparastesh.instatools.frag

import android.Manifest
import android.annotation.SuppressLint
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
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.edit
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
import ir.mahdiparastesh.instatools.view.Notify
import ir.mahdiparastesh.instatools.view.TriplePageActivity
import kotlinx.coroutines.*

class PageUnf : BasePageMain() {
    lateinit var b: PageUnfBinding
    var thread: Inquiry? = null

    override val theme: BaseActivity.Theme = BaseActivity.Theme.PRIMARY
    override val bInitialised: Boolean get() = ::b.isInitialized
    override val root: ConstraintLayout? get() = if (bInitialised) b.root else null
    override val emptyIcon: Int = R.drawable.done_unf
    override val selectiveMenuRes: Int? = null

    override fun onCreateView(inf: LayoutInflater, parent: ViewGroup?, state: Bundle?): View =
        PageUnfBinding.inflate(inflater, parent, false).let { b = it; it.root }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (Main.guest) return

        if (c.mm.unfollowers.value != null) onLoaded(c.mm.unfollowers.value.isNullOrEmpty())
        else load(true)
    }

    override fun onResume() {
        super.onResume()
        (c.c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .cancel(Notify.ID_UNF_NEW_ITEMS)
    }

    override fun onRefresh() {
        if (thread?.active == true) return
        b.rv.adapter = null
        c.mm.unfollowers.value = null
        thread = Inquiry(c).also { it.start() }
    }

    private fun load(initial: Boolean) {
        CoroutineScope(Dispatchers.IO).launch {
            val unf = ArrayList(c.dao.unfollowers()).apply {
                val favIds = (c.m.fav ?: listOf()).map { it.id }
                for (f in 0 until size) this[f].inFav = this[f].id in favIds
                specialSort()
            }

            withContext(Dispatchers.Main) {
                if (unf.isEmpty() && initial &&
                    (Persistent.now() - (c.sp?.getLong(Settings.spUnfLastChecked, 0L) ?: 0L)
                        ) > 86400000
                )
                    thread = Inquiry(c).also { it.start() }
                else {
                    c.mm.unfollowers.value = unf
                    onLoaded(unf.isEmpty())
                }
            }
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onLoaded(isEmpty: Boolean) {
        super.onLoaded(isEmpty)
        if (b.rv.adapter == null) b.rv.adapter = ListUnf(c, this)
        else b.rv.adapter?.notifyDataSetChanged()
    }


    /**
     * Fetches lists of following and then followers, saves them in the database (Friend)
     * and then sorts out the unfollowers.
     *
     * Notes for debugging:
     * - Even after 10 seconds of delay between each fetch, IG signed me out!
     * - Limiting maximum items to 12 on each fetch made it worse!
     * - Adding additional invalid random query parameters also didn't help!
     */
    inner class Inquiry(private val c: Persistent) : Thread() {
        var active = false
        private lateinit var oldFriends: List<Friend>
        private val newFriends = arrayListOf<Friend>()

        override fun run() {
            active = true
            runBlocking {
                oldFriends = c.dao.friends()
                allFollow(theFollowers = false)
            }
        }

        /**
         * Fetches a list lazily, whether from followers or following.
         * @next_max_id used for continuing to the next API fetch.
         * @param theFollowers true for followers, false for following.
         */
        private suspend fun allFollow(next_max_id: String = "", theFollowers: Boolean) {
            if (c.m.acc == null) return
            val flw = Api.call<Rest.Follow>(
                (if (theFollowers) Api.Endpoint.FOLLOWERS else Api.Endpoint.FOLLOWING).url
                    .format(c.m.acc?.id ?: 0, next_max_id), Rest.Follow::class,
                onError = { code -> onFailed(getString(Api.error(code), code)) }
            )
            if (c.m.acc == null || flw?.users == null) return
            for (u in flw.users) {
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
                allFollow(flw.next_max_id, theFollowers)
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
                        newer.unfollowedMeAt = Persistent.now()
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
            c.sp?.edit { putLong(Settings.spUnfLastChecked, Persistent.now()) }
            val newUnf = newFriends.filter {
                (it.unfollowedMeAt != null
                    && it.unfollowedMeAt!! > (c.sp?.getLong(Settings.spNotifiedUnfTill, 0L)
                    ?: 0L))
            }
            if (newUnf.isNotEmpty()) {
                withContext(Dispatchers.Main) { gotNewOnes(newUnf.size) }
                // HIGHLIGHT THEM IF YOU WANT
            }
            interrupt()
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
            c.sp?.edit { putLong(Settings.spNotifiedUnfTill, Persistent.now()) }
        } // Never use Fragment::getString()

        override fun interrupt() {
            if (!active) return
            active = false
            super.interrupt()
        }
    }
}
