package ir.mahdiparastesh.instatools.serv

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
import ir.mahdiparastesh.instatools.data.Model
import ir.mahdiparastesh.instatools.more.Persistent

@SuppressLint("UnspecifiedImmutableFlag")
class Exporter : Service(), ViewModelStoreOwner, Persistent {
    private var mViewModelStore = ViewModelStore()

    override var c: Context
        get() = applicationContext
        set(_) {}
    override var m: Model
        get() = ViewModelProvider(viewModelStore, Model.Factory()).get("Model", Model::class.java)
        set(_) {}
    override var gsp: SharedPreferences
        get() = Persistent.initGsp(c)
        set(_) {}
    override var sp: SharedPreferences?
        get() = Persistent.initSp(c, m.acc)
        set(_) {}

    companion object {
        private val pack: String = Exporter::class.java.`package`!!.name
        val CHANNEL = "$pack.EXPORTING"
        val ACTION_STOP = "$pack.ACTION_STOP"
        const val CH_ID = 103
        var active = false
        var handler: Handler? = null

        fun pi(c: Context, code: String): PendingIntent = PendingIntent.getService(
            c, 0, Intent(c, Exporter::class.java).apply { action = code },
            PendingIntent.FLAG_CANCEL_CURRENT
        )
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent.action != null) when (intent.action) {
            ACTION_STOP -> if (active) stopForeground(true)
        }
        return START_NOT_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        active = true

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(
                    NotificationChannel(
                        CHANNEL, c.resources.getString(R.string.exporterChannel),
                        NotificationManager.IMPORTANCE_LOW
                    ).apply { description = c.resources.getString(R.string.exporterChannelDesc) })
        startForeground(CH_ID, NotificationCompat.Builder(c, CHANNEL).apply {
            setSmallIcon(R.mipmap.launcher_round)
            setContentTitle(c.resources.getString(R.string.exporterTitle))
            setOngoing(true)
            priority = NotificationCompat.PRIORITY_LOW
            setContentIntent(
                PendingIntent.getActivity(
                    c, 0, Intent(c, Main::class.java), PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
            addAction(0, c.resources.getString(R.string.exporterStop), pi(c, ACTION_STOP))
        }.build())

        // TODO: DO IT
    }

    override fun onDestroy() {
        handler = null
        stopForeground(true)
        super.onDestroy()
        active = false
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun getViewModelStore(): ViewModelStore = mViewModelStore
}
