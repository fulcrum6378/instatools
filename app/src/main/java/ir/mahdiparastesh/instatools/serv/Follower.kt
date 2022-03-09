package ir.mahdiparastesh.instatools.serv

import android.content.Intent
import android.os.*
import com.android.volley.NetworkResponse
import com.android.volley.Request
import ir.mahdiparastesh.instatools.BuildConfig
import ir.mahdiparastesh.instatools.MassFollower
import ir.mahdiparastesh.instatools.R
import ir.mahdiparastesh.instatools.Settings
import ir.mahdiparastesh.instatools.data.Followable
import ir.mahdiparastesh.instatools.data.Friend
import ir.mahdiparastesh.instatools.json.Api
import ir.mahdiparastesh.instatools.json.Rest
import ir.mahdiparastesh.instatools.more.ForegroundService
import ir.mahdiparastesh.instatools.more.LongThread
import ir.mahdiparastesh.instatools.more.Persistent
import ir.mahdiparastesh.instatools.more.ServiceOwnerActivity
import kotlinx.coroutines.runBlocking

class Follower : ForegroundService() {
    private var toBeEnqueued = arrayListOf<ToBeEnqueued>()
    private var enqueuer: Enqueuer? = null
    private var scheduler: Scheduler? = null
    private var following = arrayListOf<Friend>()

    override val requiresHandling = true
    override val com: ForegroundServiceCompanion get() = Companion
    override val waveLockTimeout = 60

    companion object : ForegroundServiceCompanion(77, Follower::class) {
        override val channel: String = "$pack.FOLLOWING"
        override val chName: Int = R.string.followerChannel
        override val chDesc: Int = R.string.followerChannelDesc
        override val ntfSmallIcon: Int = R.mipmap.launcher_round
        override val ntfTitle: Int = R.string.followerTitle
        override val ntfActions: Array<Pair<String, Int>> = arrayOf(
            ACTION_STOP to R.string.followerStop
        )

        const val EXTRA_ENQUEUE = "enqueue"
        const val HANDLE_ENQUEUE = 0
        var DELAY = Settings.defSpFollowerDelay

        fun properDelay(c: Persistent) =
            c.sp?.getLong(Settings.spFollowerDelay, Settings.defSpFollowerDelay)
                ?: Settings.defSpFollowerDelay
    }

    override fun resolveIntent(intent: Intent) {
        intent.getParcelableExtra<ToBeEnqueued>(EXTRA_ENQUEUE)?.let { tbe ->
            toBeEnqueued.add(tbe)
            if (enqueuer?.active != true) enqueuer = Enqueuer().also { it.start() }
        }
    }

    override fun onCreate() {
        super.onCreate()
        notification(Follower, MassFollower::class)
        handler = object : Handler(Looper.getMainLooper()) {
            override fun handleMessage(msg: Message) {
                when (msg.what) {
                    HANDLE_ENQUEUE -> {
                        toBeEnqueued.add(msg.obj as ToBeEnqueued)
                        if (enqueuer?.active != true) enqueuer = Enqueuer().also { it.start() }
                    }
                }
            }
        }
        DELAY = properDelay(this)
        Thread {
            runBlocking { following.addAll(dao.following()) }
            if (scheduler?.active != true) scheduler = Scheduler().also { it.start() }
        }.start()
    }

    inner class Enqueuer : LongThread(handling.looper) {
        private var total = 0

        override val messages: Array<Pair<Int, (msg: Message) -> Unit>> = arrayOf(
            0 to { msg ->
                val flw = msg.obj as Rest.Follow
                var sum: Int
                dao.addFollowables(flw.users.filter {
                    (toBeEnqueued[0].includePv || !it.is_private) &&
                            it.pk !in following.map { f -> f.id } && it.pk != m.acc!!.id.toString()
                }.map { Followable(it.pk, it.username, it.is_private) }.also {
                    sum = it.size
                    total += sum
                    MassFollower.handler?.obtainMessage(ServiceOwnerActivity.HANDLE_INSERTED, it)
                        ?.sendToTarget()
                })
                if (flw.next_max_id == null || total >= toBeEnqueued[0].limitTo) enqueuingDone()
                else allFollow(flw.next_max_id)
                if (sum > 0 && scheduler?.active != true)
                    scheduler = Scheduler().also { it.start() }
            },
            Api.HANDLE_ERROR to {
                /////
            }
        )

        override fun run() {
            super.run()
            enqueue()
        }

        private fun enqueue() {
            if (enqueuer?.active == false) return
            val cur = toBeEnqueued.getOrNull(0)
            total = 0
            if (cur == null || !Follower.active.value!!) {
                interrupt()
                if (scheduler?.active != true) this@Follower.destroy()
                return; }
            allFollow()
        }

        private fun allFollow(next_max_id: String = "") {
            Api<Rest.Follow>(
                this@Follower,
                (if (toBeEnqueued[0].isItFollowers) Api.Type.FOLLOWERS else Api.Type.FOLLOWING).url
                    .format(toBeEnqueued[0].id, next_max_id), Rest.Follow::class, handler
            ) { flw -> handler?.obtainMessage(0, flw)?.sendToTarget() }
        }

        private fun enqueuingDone() {
            toBeEnqueued.removeAt(0)
            enqueue()
        }
    }

    inner class Scheduler : LongThread(handling.looper) {
        private var fwb: Followable? = null
        private var errorCount = 0

        override val messages: Array<Pair<Int, (msg: Message) -> Unit>> = arrayOf(
            0 to { followed() },
            Api.HANDLE_ERROR to {
                if ((it.obj as NetworkResponse?)?.statusCode == 200) followed() else {
                    errorCount++
                    if (errorCount > 5) {
                        if (toBeEnqueued.isEmpty() && enqueuer?.active != true)
                            this@Follower.destroy()
                        interrupt()
                        if (BuildConfig.DEBUG) throw Exception(
                            (it.obj as NetworkResponse?)?.statusCode.toString() + " : " + String(
                                (it.obj as NetworkResponse?)?.data ?: "null".encodeToByteArray()
                            )
                        )
                    }
                }
            }
        )

        override fun run() {
            super.run()
            if (follow()) {
                m.acc!!.mfrw--
                m.acc!!.saveMe(c)
                MassFollower.handler?.obtainMessage(MassFollower.HANDLE_REWARD_CONSUMED)
                    ?.sendToTarget()
            }
        }

        private fun follow(): Boolean {
            fwb = dao.aFollowable().getOrNull(0)
            if (fwb == null || !Follower.active.value!!) {
                if (toBeEnqueued.isEmpty() && enqueuer?.active != true) this@Follower.destroy()
                interrupt()
                return false; }
            Api<Rest.DoFollow>(
                this@Follower, Api.Type.FOLLOW.url.format(fwb!!.id), Rest.DoFollow::class,
                handler, method = Request.Method.POST
            ) { handler?.obtainMessage(0, fwb)?.sendToTarget() }
            return true
        }

        private fun followed() {
            errorCount = 0
            fwb?.let {
                dao.deleteFollowable(it)
                MassFollower.handler?.obtainMessage(ServiceOwnerActivity.HANDLE_DELETED, it)
                    ?.sendToTarget()
            }
            sleep(DELAY)
            follow()
        }
    }

    class ToBeEnqueued(
        val id: String,
        val isItFollowers: Boolean,
        val includePv: Boolean,
        val limitTo: Long
    ) : Parcelable {
        constructor(parcel: Parcel) : this(
            parcel.readString()!!,
            parcel.readByte() != 0.toByte(),
            parcel.readByte() != 0.toByte(),
            parcel.readLong()
        )

        override fun writeToParcel(parcel: Parcel, flags: Int) {
            parcel.writeString(id)
            parcel.writeByte(if (isItFollowers) 1 else 0)
            parcel.writeByte(if (includePv) 1 else 0)
            parcel.writeLong(limitTo)
        }

        override fun describeContents(): Int = 0

        companion object CREATOR : Parcelable.Creator<ToBeEnqueued> {
            override fun createFromParcel(parcel: Parcel): ToBeEnqueued = ToBeEnqueued(parcel)
            override fun newArray(size: Int): Array<ToBeEnqueued?> = arrayOfNulls(size)
        }
    }
}
