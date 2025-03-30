package ir.mahdiparastesh.instatools.util

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.annotation.MainThread
import androidx.lifecycle.MutableLiveData
import ir.mahdiparastesh.instatools.InstaTools
import ir.mahdiparastesh.instatools.R
import ir.mahdiparastesh.instatools.job.CommandService
import ir.mahdiparastesh.instatools.job.DownloadService
import ir.mahdiparastesh.instatools.view.MultiPagedActivity
import ir.mahdiparastesh.instatools.view.Notify
import kotlin.reflect.KClass

/**
 * Abstract class for all foreground services in this app.
 * Most functions do not require to be called on the main thread.
 */
abstract class ForegroundService : Service() {
    protected val c: InstaTools by lazy { applicationContext as InstaTools }
    protected lateinit var ntfManager: NotificationManager

    abstract val com: ForegroundServiceCompanion<*>
    abstract val ntfChannel: Notify.Channel
    abstract val ntfId: Int
    abstract var ntfTitle: String
    abstract var ntfText: String?
    abstract var ntfSmallText: String?
    abstract val ntfActions: Array<Pair<Int, String>>
    open val ntfSmallIcon: Int = R.drawable.notification

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val HANDLE_ITEM_UPDATED = 2
        const val HANDLE_ITEM_DELETED = 3

        private val services = arrayOf(DownloadService::class, CommandService::class)

        fun anyRunning() = arrayOf(DownloadService, CommandService)
            .any { it.active.value == true }

        fun terminateTasks(c: Context) {
            services.forEach { service ->
                c.stopService(Intent(c, service.java).apply { action = ACTION_STOP })
            }
        }

        fun ntfMutability(bb: Boolean = true): Int = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
                if (bb) PendingIntent.FLAG_MUTABLE else PendingIntent.FLAG_IMMUTABLE
            else ->
                if (bb) PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_IMMUTABLE
        }
    }

    /**
     * Abstract class from which all companion objects of [ForegroundService] subclasses must extend.
     */
    abstract class ForegroundServiceCompanion<Item> {
        val active = MutableLiveData(false)

        @Volatile
        var processingItem: Item? = null
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent.action != null) when (intent.action) {
            ACTION_START -> {}
            ACTION_STOP -> onCancel()
        }
        return START_NOT_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        com.active.value = true
        ntfManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
    }

    private var ntfActivity: KClass<*>? = null
    private var ntfPage: Int? = null
    fun initialNotification(
        openActivity: KClass<*>? = null, turnToPage: Int? = null, progress: Pair<Int, Int>? = null
    ) {
        ntfActivity = openActivity
        ntfPage = turnToPage
        ntfManager.createNotificationChannelGroup(Notify.ChannelGroup.SERVICES.create(c))
        ntfManager.createNotificationChannel(ntfChannel.create(c))
        startForeground(ntfId, notification(progress))
    }

    fun updateNotification(progress: Pair<Int, Int>? = null) {
        ntfManager.notify(ntfId, notification(progress))
    }

    private fun notification(progress: Pair<Int, Int>?) =
        Notification.Builder(c, ntfChannel.id).apply {
            setSmallIcon(ntfSmallIcon)
            setContentTitle(ntfTitle)
            ntfSmallText?.also { setContentText(it) }
            setStyle(Notification.BigTextStyle().bigText(ntfText))
            // setSound(null) setSilent(true)
            setOngoing(true)
            setProgress(progress?.second ?: 0, progress?.first ?: 0, progress == null)
            if (ntfActivity != null) setContentIntent(
                PendingIntent.getActivity(
                    c, 0, Intent(c, ntfActivity!!.java).apply {
                        if (ntfPage != null)
                            putExtra(MultiPagedActivity.Companion.EXTRA_TURN_TO_PAGE, ntfPage)
                    }, ntfMutability()
                )
            )
            for (pair in ntfActions) addAction(
                Notification.Action.Builder(null, getString(pair.first), pi(c, pair.second)).build()
            )
        }.build()

    fun pi(c: Context, code: String): PendingIntent = PendingIntent.getService(
        c, 0, Intent(c, this::class.java).apply { action = code }, ntfMutability()
    )

    protected fun notifyFailure(
        id: Int, activity: KClass<*>?, func: Notification.Builder.() -> Unit
    ) {
        ntfManager.createNotificationChannel(Notify.Channel.RESULT.create(c))
        ntfManager.notify(
            id, Notification.Builder(c, Notify.Channel.RESULT.id).apply {
                setSmallIcon(R.drawable.notification)
                func()
                if (activity != null) setContentIntent(
                    PendingIntent.getActivity(c, 0, Intent(c, activity.java), ntfMutability())
                )
                addAction(
                    Notification.Action.Builder(
                        null, getString(R.string.tryAgain), pi(c, ACTION_START)
                    ).build()
                )
            }.build()
        )
    }

    @MainThread
    abstract fun onCancel()

    fun destroy() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        com.active.value = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
