package ir.mahdiparastesh.instatools.view

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.annotation.StringRes
import ir.mahdiparastesh.instatools.R

@SuppressLint("NewApi")
class Notify {
    companion object {
        const val ID_QUEUER = 1
        const val ID_EXPORTER = 2
        const val ID_FOLLOWER = 3
        const val ID_EXPORTER_UNK_FETCH_ERROR = 20
        const val ID_EXPORTER_429 = 21
        const val ID_EXPORTER_DONE = 25 // incremental
        const val ID_UNF_NEW_ITEMS = 101
    }

    enum class Channel(
        val id: String, // unique within this app, recommended maximum 40 characters
        @StringRes val rName: Int,
        @StringRes val rDesc: Int,
        private val importance: Int = NotificationManager.IMPORTANCE_LOW,
        private val groupId: String? = null,
    ) {
        EXPORTER(
            "exporting", R.string.exporterChannel, R.string.exporterChannelDesc,
            groupId = ChannelGroup.SERVICES.id
        ),
        FOLLOWER(
            "following", R.string.followerChannel, R.string.followerChannelDesc,
            groupId = ChannelGroup.SERVICES.id
        ),
        QUEUER(
            "downloading", R.string.queuerChannel, R.string.queuerChannelDesc,
            groupId = ChannelGroup.SERVICES.id
        ),

        UNF_NEW_ITEMS(
            "unf_new_items", R.string.newUnfNtfChannel, R.string.newUnfNtfChannelDesc,
            NotificationManager.IMPORTANCE_HIGH
        ),

        RESULT("result", R.string.taskResultChannel, R.string.taskResultChannelDesc);

        fun create(c: Context) = NotificationChannel(id, c.resources.getString(rName), importance)
            .apply {
                description = c.resources.getString(rDesc)
                group = groupId
            }
    }

    enum class ChannelGroup(
        val id: String,
        @StringRes val rName: Int,
        @StringRes val rDesc: Int,
    ) {
        SERVICES(
            "services", R.string.servicesChannelGroup, R.string.servicesChannelGroupDesc
        );

        fun create(c: Context) = NotificationChannelGroup(id, c.resources.getString(rName)).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                description = c.resources.getString(rDesc)
        }
    }
}
