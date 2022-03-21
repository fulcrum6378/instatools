package ir.mahdiparastesh.instatools.view

import android.net.Uri
import ir.mahdiparastesh.instatools.data.Exportable
import ir.mahdiparastesh.instatools.json.Api
import ir.mahdiparastesh.instatools.more.BaseExporter
import ir.mahdiparastesh.instatools.more.Persistent
import ir.mahdiparastesh.instatools.view.UiTools.Companion.calendar
import ir.mahdiparastesh.instatools.view.UiTools.Companion.xFromMicroseconds
import ir.mahdiparastesh.instatools.view.UiTools.Companion.z
import java.io.FileOutputStream
import java.util.*

abstract class TxtExporter(c: Persistent, exp: Exportable) : BaseExporter(c, exp) {
    private var ink = StringBuilder()

    override fun run() {
        if (exp.threadData == null) return
        progress(0f, false)
        for (dm in exp.threadData!!.items) {
            val cal = dm.timestamp.xFromMicroseconds().calendar()
            ink.append("${cal[Calendar.YEAR]}/${z(cal[Calendar.MONTH] + 1)}/${z(cal[Calendar.DAY_OF_MONTH])}, ")
            ink.append("${z(cal[Calendar.HOUR_OF_DAY])}:${z(cal[Calendar.MINUTE])}:${z(cal[Calendar.SECOND])} - ")
            val user = if (dm.user_id == c.m.acc?.id?.toDouble()) "${c.m.acc?.user}"
            else exp.threadData!!.users.find { it.pk.toDouble() == dm.user_id }?.username
            ink.append("$user : ")
            ink.append(
                when {
                    dm.action_log != null -> "<${dm.action_log.description}>"
                    dm.animated_media != null -> "<sent a GIPHY>"
                    dm.clip != null -> "<shared a clip>"
                    dm.felix_share != null -> "<shared a long video>${dm.felix_share.text.shareText()}"
                    dm.like != null -> dm.like
                    dm.link != null -> dm.link.text
                    dm.live_viewer_invite != null ->
                        "<${dm.live_viewer_invite.cta_button_name}>${dm.live_viewer_invite.text.shareText()}"
                    dm.media != null ->
                        "<uploaded a ${if (dm.media.media_type == 1f) "picture" else "video"}>"
                    dm.media_share != null ->
                        "<shared a ${if (dm.media_share.media_type == 1f) "picture" else "video"}>"
                    dm.placeholder != null -> "<${dm.placeholder.message}>"
                    dm.profile != null -> "@${dm.profile.username}"
                    dm.raven_media != null -> "<captured a photo>"
                    dm.reel_share != null -> "<shared a reel>${dm.reel_share.text.shareText()}"
                    dm.story_share != null -> "<shared a story>${dm.story_share.text.shareText()}"
                    dm.text != null -> dm.text
                    dm.video_call_event != null -> "<${dm.video_call_event.description}>"
                    dm.voice_media != null -> "<sent a voice message>"
                    else -> "<unknown media type>"
                }
            )
            ink.append("\n")
        }
        c.c.contentResolver.openFileDescriptor(Uri.parse(Api.encode(exp.uri)), "w")?.use {
            FileOutputStream(it.fileDescriptor).use { fos ->
                fos.write(ink.toString().encodeToByteArray())
            }
        }
        progress(100f, true)
    }

    private fun String.shareText() = if (!isBlank()) ": $this" else ""
}
