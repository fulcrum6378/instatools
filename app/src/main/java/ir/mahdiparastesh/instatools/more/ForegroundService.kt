package ir.mahdiparastesh.instatools.more

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Handler
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import ir.mahdiparastesh.instatools.Main
import ir.mahdiparastesh.instatools.R
import ir.mahdiparastesh.instatools.data.Database
import ir.mahdiparastesh.instatools.data.Model
import ir.mahdiparastesh.instatools.serv.Exporter
import ir.mahdiparastesh.instatools.serv.Queuer
import kotlin.reflect.KClass

@SuppressLint("UnspecifiedImmutableFlag")
abstract class ForegroundService : Service(), ViewModelStoreOwner, Persistent {
    private var mViewModelStore = ViewModelStore()
    abstract val com: ForegroundServiceCompanion
    protected lateinit var db: Database
    lateinit var dao: Database.DAO

    companion object {
        const val ACTION_STOP = "ACTION_STOP"
        private val services = arrayOf(Queuer::class, Exporter::class)

        fun anyRunning() = arrayOf(Queuer, Exporter).any { it.active }
        // Never reference "Queuer"'s self in a static context

        fun terminateTasks(c: Context) {
            services.forEach { service ->
                c.stopService(Intent(c, service.java).apply { action = ACTION_STOP })
            }
        }
    }

    abstract class ForegroundServiceCompanion(val CH_ID: Int, private val klass: KClass<*>) {
        protected val pack: String = klass.java.`package`!!.name
        abstract val channel: String
        abstract var chName: Int
        abstract var chDesc: Int
        abstract var ntfSmallIcon: Int
        abstract var ntfTitle: Int
        abstract var ntfActions: Array<Pair<String, Int>>

        abstract var active: Boolean
        abstract var handler: Handler?

        open fun pi(c: Context, code: String): PendingIntent = PendingIntent.getService(
            c, 0, Intent(c, klass.java).apply { action = code },
            PendingIntent.FLAG_CANCEL_CURRENT
        )
    }

    // With getters and setters, you'll avoid NullPointerException;
    // Because "this" is apparently null at the time of instantiation,
    // So you cannot invoke "applicationContext" on it!
    override lateinit var c: Context
    override lateinit var m: Model
    override lateinit var gsp: SharedPreferences
    override var sp: SharedPreferences? = null

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent.action != null) when (intent.action) {
            ACTION_STOP -> if (com.active) onCancel()
        }
        return START_NOT_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        com.active = true
        c = applicationContext
        m = ViewModelProvider(viewModelStore, Model.Factory()).get("Model", Model::class.java)
        gsp = Persistent.initGsp(c)
        sp = Persistent.initSp(c, m.acc)
    }

    open fun notification(
        com: ForegroundServiceCompanion, openActivity: KClass<*>, turnToPage: Int? = null
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(
                    NotificationChannel(
                        com.channel, c.resources.getString(com.chName),
                        NotificationManager.IMPORTANCE_LOW
                    ).apply { description = c.resources.getString(com.chDesc) })
        startForeground(Exporter.CH_ID, NotificationCompat.Builder(c, com.channel).apply {
            setSmallIcon(com.ntfSmallIcon)
            setContentTitle(c.resources.getString(com.ntfTitle))
            setOngoing(true)
            setProgress(0, 0, true)
            priority = NotificationCompat.PRIORITY_LOW
            setContentIntent(
                PendingIntent.getActivity(
                    c, 0, Intent(c, openActivity.java).apply {
                        if (turnToPage != null) putExtra(Main.EXTRA_TURN_TO_PAGE, turnToPage)
                    }, PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
            addAction(0, c.resources.getString(R.string.exporterStop), com.pi(c, ACTION_STOP))
        }.build())
    }

    open fun onCancel() {
        onAbort(true)
    }

    open fun onAbort(cancelled: Boolean) {
        destroy()
    }

    open fun destroy() {
        stopForeground(true)
        stopSelf()
    }

    override fun onDestroy() {
        com.handler = null
        com.active = false
        // if (::db.isInitialized && !Inquisitor.active && !Exporter.active && !Queuer.active) db.close()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun getViewModelStore(): ViewModelStore = mViewModelStore
}
