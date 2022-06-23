package ir.mahdiparastesh.instatools.expt

import android.net.Uri
import ir.mahdiparastesh.instatools.data.Exportable
import ir.mahdiparastesh.instatools.json.Api
import ir.mahdiparastesh.instatools.json.Rest
import ir.mahdiparastesh.instatools.serv.Exporter
import ir.mahdiparastesh.instatools.view.UiTools
import ir.mahdiparastesh.instatools.view.UiTools.xFromMicroseconds
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

abstract class TxtExporter(c: Exporter, exp: Exportable) : BaseExporter(c, exp) {
    private var ink = StringBuilder()
    private val allUsers = exp.threadData?.users?.plusElement(
        Rest.User(c.m.acc?.name, false, c.m.acc?.id.toString(), "", c.m.acc?.user.toString())
    )

    override fun run() {
        if (exp.threadData == null) {
            progress(100f, false); return; }
        progress(0f, false)
        for (dm in exp.threadData!!.items) {
            if (dm.action_log != null) continue
            ink.append(
                SimpleDateFormat(
                    "${UiTools.DATE_FORMAT}, ${UiTools.TIME_FORMAT}", Locale.getDefault()
                ).format(Date(dm.timestamp.xFromMicroseconds()))
            ).append(" - ")
            val user = if (dm.user_id == c.m.acc?.id?.toDouble()) "${c.m.acc?.user}"
            else exp.threadData!!.users.find { it.pk.toDouble() == dm.user_id }?.username
            ink.append("$user : ")
            ink.append(
                when {
                    dm.animated_media != null -> "<sent a gif>"
                    dm.clip != null -> "<shared a clip>"
                    dm.direct_media_share != null -> "<tagged you in a post>"
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
                    dm.raven_media != null ->
                        "<captured a ${if (dm.raven_media.media_type == 1f) "photo" else "video"}>"
                    dm.reel_share != null -> "<shared a reel>${dm.reel_share.text.shareText()}"
                    dm.story_share != null -> "<shared a story>${dm.story_share.text.shareText()}"
                    dm.text != null -> dm.text
                    dm.video_call_event != null -> "<${dm.video_call_event.description}>"
                    dm.voice_media != null -> "<sent a voice message>"
                    else -> "<unknown media type>"
                }
            )
            if (dm.reactions != null) for (r in dm.reactions.emojis) ink.append("\n")
                .append(r.emoji)
                .append(" by ${allUsers?.find { it.pk.toDouble() == r.sender_id }?.username} at ")
                .append(
                    SimpleDateFormat(
                        "${UiTools.DATE_FORMAT} - ${UiTools.TIME_FORMAT}", Locale.getDefault()
                    ).format(Date(r.timestamp.xFromMicroseconds()))
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

    private fun String?.shareText() = if (!isNullOrBlank()) ": $this" else ""
}
