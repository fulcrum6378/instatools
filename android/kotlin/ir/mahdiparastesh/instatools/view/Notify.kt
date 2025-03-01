package ir.mahdiparastesh.instatools.view

import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.annotation.StringRes
import ir.mahdiparastesh.instatools.R

/** Helper class for maintaining Notifications. */
class Notify {
    companion object {
        const val ID_DOWNLOADER = 1
        const val ID_EXPORTER = 2
        const val ID_COMMANDER = 3
        const val ID_DOWNLOADER_ERROR = 10
        const val ID_DOWNLOADER_SOME_FAILED = 11
        const val ID_EXPORTER_ERROR = 20
        const val ID_EXPORTER_DONE = 25 // incremental
        const val ID_COMMANDER_ERROR = 30
        const val ID_COMMANDER_SOME_FAILED = 31
    }

    enum class Channel(
        val id: String, // unique within this app, recommended maximum 40 characters
        @StringRes val rName: Int,
        @StringRes val rDesc: Int,
        private val importance: Int = NotificationManager.IMPORTANCE_LOW,
        private val groupId: String? = null,
    ) {
        DOWNLOADER(
            "downloading", R.string.downloaderChannel, R.string.downloaderChannelDesc,
            groupId = ChannelGroup.SERVICES.id
        ),
        EXPORTER(
            "exporting", R.string.exporterChannel, R.string.exporterChannelDesc,
            groupId = ChannelGroup.SERVICES.id
        ),
        COMMANDER(
            "commanding", R.string.commanderChannel, R.string.commanderChannelDesc,
            groupId = ChannelGroup.SERVICES.id
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
