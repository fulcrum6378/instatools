package ir.mahdiparastesh.instatools.serv

import android.content.Intent
import android.os.*
import com.android.volley.NetworkResponse
import com.android.volley.Request
import com.android.volley.toolbox.Volley
import com.google.gson.Gson
import ir.mahdiparastesh.instatools.MassFollower
import ir.mahdiparastesh.instatools.R
import ir.mahdiparastesh.instatools.Settings
import ir.mahdiparastesh.instatools.data.Followable
import ir.mahdiparastesh.instatools.json.Api
import ir.mahdiparastesh.instatools.json.Api.Companion.adder
import ir.mahdiparastesh.instatools.json.Rest
import ir.mahdiparastesh.instatools.more.ForegroundService
import ir.mahdiparastesh.instatools.more.LongThread
import ir.mahdiparastesh.instatools.more.Persistent
import ir.mahdiparastesh.instatools.more.ServiceOwnerActivity
import ir.mahdiparastesh.instatools.view.Notify

class Follower : ForegroundService() {
    private var toBeEnqueued = arrayListOf<ToBeEnqueued>()
    private var enqueuer: Enqueuer? = null
    private var scheduler: Scheduler? = null
    private var following = arrayListOf<String>()
    private val reqQueue by lazy { Volley.newRequestQueue(c) }

    override val com: ForegroundServiceCompanion get() = Companion
    override lateinit var ntfTitle: String
    override val requiresHandling = true
    override val waveLockTimeout = 60

    companion object : ForegroundServiceCompanion() {
        override val klass = Follower::class.java
        override val channel = Notify.Channel.FOLLOWER
        override val ntfId = Notify.ID_FOLLOWER
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
        @Suppress("DEPRECATION")
        (if (Build.VERSION.SDK_INT < 33)
            intent.getParcelableExtra(EXTRA_ENQUEUE)
        else intent.getParcelableExtra(EXTRA_ENQUEUE, ToBeEnqueued::class.java))?.let { tbe ->
            toBeEnqueued.add(tbe)
            if (enqueuer?.active != true) enqueuer = Enqueuer().also { it.start() }
        }
    }

    override fun onCreate() {
        super.onCreate()
        ntfTitle = getString(R.string.followerTitle)
        initialNotification(Follower, MassFollower::class)
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
            following.addAll(dao.following().map { f -> f.id })
            if (scheduler?.active != true) scheduler = Scheduler().also { it.start() }
        }.start()
    }

    inner class Enqueuer : LongThread(handling.looper) {
        private var total = 0

        override val messages: Array<Pair<Int, (msg: Message) -> Unit>> = arrayOf(
            0 to { msg ->
                val flw = msg.obj as Rest.Follow
                var sum = 0
                flw.users?.filter {
                    (toBeEnqueued[0].includePv || !it.is_private) &&
                            it.pk !in following && it.pk != m.acc!!.id.toString()
                }?.let {
                    val toLimit = toBeEnqueued[0].limitTo - total
                    if (toLimit < it.size) it.subList(0, toLimit) else it
                }?.map { Followable(it.pk, it.username, it.is_private) }?.also {
                    sum = it.size
                    total += sum
                    MassFollower.handler?.obtainMessage(ServiceOwnerActivity.HANDLE_INSERTED, it)
                        ?.sendToTarget()
                }?.also { dao.addFollowables(it) }
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
                if (scheduler?.active != true) scheduler = Scheduler().also { it.start() }
                interrupt()
            } else allFollow()
        }

        private fun allFollow(next_max_id: String = "") {
            reqQueue.adder = Api<Rest.Follow>(
                this@Follower,
                (if (toBeEnqueued[0].isItFollowers) Api.Endpoint.FOLLOWERS else Api.Endpoint.FOLLOWING).url
                    .format(toBeEnqueued[0].id, next_max_id),
                Rest.Follow::class, handler, autoQueue = false
            ) { flw -> handler?.obtainMessage(0, flw)?.sendToTarget() }
        }

        private fun enqueuingDone() {
            toBeEnqueued.removeFirstOrNull()
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
                    val doFollow = try {
                        (it.obj as NetworkResponse?)?.data?.let { ba ->
                            Gson().fromJson(String(ba), Rest.DoFollow::class.java)
                        }
                    } catch (e: Exception) {
                        null
                    }
                    errorCount++
                    if (doFollow?.spam == true || errorCount > 5) {
                        if (doFollow?.spam == true) MassFollower.handler
                            ?.obtainMessage(MassFollower.HANDLE_DETECTED_AS_SPAMMER)
                            ?.sendToTarget()
                        end()
                    } else {
                        sleep(DELAY)
                        follow()
                    }
                }
            }
        )

        override fun run() {
            super.run()
            follow()
        }

        private fun follow() {
            fwb = dao.aFollowable().getOrNull(0)
            if (fwb == null || !Follower.active.value!!) {
                end(); return; }
            reqQueue.adder = Api<Rest.DoFollow>(
                this@Follower, Api.Endpoint.FOLLOW.url.format(fwb!!.id), Rest.DoFollow::class,
                handler, autoQueue = false, method = Request.Method.POST
            ) { handler?.obtainMessage(0, fwb)?.sendToTarget() }
        }

        private fun followed() {
            errorCount = 0
            fwb?.let {
                dao.deleteFollowable(it)
                MassFollower.handler?.obtainMessage(ServiceOwnerActivity.HANDLE_DELETED, it)
                    ?.sendToTarget()
            }
            if (dao.countFollowables() > 0) {
                sleep(DELAY)
                follow()
            } else end()
        }

        private fun end() {
            if (toBeEnqueued.isEmpty() && enqueuer?.active != true)
                this@Follower.finish(false)
            interrupt()
        }
    }

    class ToBeEnqueued(
        val id: String,
        val isItFollowers: Boolean,
        val includePv: Boolean,
        val limitTo: Int
    ) : Parcelable {
        constructor(parcel: Parcel) : this(
            parcel.readString()!!,
            parcel.readByte() != 0.toByte(),
            parcel.readByte() != 0.toByte(),
            parcel.readInt()
        )

        override fun writeToParcel(parcel: Parcel, flags: Int) {
            parcel.writeString(id)
            parcel.writeByte(if (isItFollowers) 1 else 0)
            parcel.writeByte(if (includePv) 1 else 0)
            parcel.writeInt(limitTo)
        }

        override fun describeContents(): Int = 0

        companion object CREATOR : Parcelable.Creator<ToBeEnqueued> {
            override fun createFromParcel(parcel: Parcel): ToBeEnqueued = ToBeEnqueued(parcel)
            override fun newArray(size: Int): Array<ToBeEnqueued?> = arrayOfNulls(size)
        }
    }
}
