package ir.mahdiparastesh.instatools.view

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.content.Context
import androidx.annotation.StringRes
import ir.mahdiparastesh.instatools.R

/** Helper class for maintaining [Notification]s */
class Notify {
    companion object {
        const val ID_DOWNLOADER = 1
        const val ID_COMMANDER = 2
        const val ID_DOWNLOADER_PAUSED = 10
        const val ID_DOWNLOADER_ERROR = 11
        const val ID_DOWNLOADER_SOME_FAILED = 12
        const val ID_COMMANDER_PAUSED = 20
        const val ID_COMMANDER_ERROR = 21
        const val ID_COMMANDER_SOME_FAILED = 22
    }

    enum class Channel(
        val id: String, // unique within this app, recommended maximum 40 characters
        @StringRes val rName: Int,
        @StringRes val rDesc: Int,
        private val importance: Int,
        private val groupId: String?,
    ) {
        DOWNLOADER(
            "downloading",
            R.string.downloaderChannel, R.string.downloaderChannelDesc,
            NotificationManager.IMPORTANCE_LOW,
            ChannelGroup.SERVICES.id
        ),
        COMMANDER(
            "commanding",
            R.string.commanderChannel, R.string.commanderChannelDesc,
            NotificationManager.IMPORTANCE_LOW,
            ChannelGroup.SERVICES.id
        ),
        TASK_SUSPENSION(
            "task_suspension",
            R.string.taskSuspensionChannel, R.string.taskSuspensionChannelDesc,
            NotificationManager.IMPORTANCE_LOW,
            ChannelGroup.SERVICES.id
        ),
        TASK_RESULTS(
            "task_results",
            R.string.taskResultChannel, R.string.taskResultChannelDesc,
            NotificationManager.IMPORTANCE_HIGH,
            ChannelGroup.SERVICES.id
        );

        fun create(c: Context) = NotificationChannel(id, c.resources.getString(rName), importance)
            .apply {
                description = c.resources.getString(rDesc)
                group = groupId
                setSound(null, null)  // all notification are silent in this app.
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
            description = c.resources.getString(rDesc)
        }
    }
}
