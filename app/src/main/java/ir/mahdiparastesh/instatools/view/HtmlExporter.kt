package ir.mahdiparastesh.instatools.view

import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import ir.mahdiparastesh.instatools.data.Exportable
import ir.mahdiparastesh.instatools.json.Api
import ir.mahdiparastesh.instatools.more.BaseExporter
import ir.mahdiparastesh.instatools.more.Persistent
import ir.mahdiparastesh.instatools.more.Versioned
import java.lang.StringBuilder

@Suppress("MemberVisibilityCanBePrivate")
abstract class HtmlExporter(c: Persistent, exp: Exportable) : BaseExporter(c, exp) {
    private lateinit var folder: DocumentFile
    val divisions = arrayListOf<String>()
    var needJquery = false

    val divInd = "  "
    val divDial = "<p class=\"dial\">%s</p>"
    val divHint = "<p class=\"hint\">%s</p>"
    val divHintAndDial = divHint.format("%1\$s") + "\n$divInd" + divDial.format("%2\$s")
    val divLink = divDial.format("<a href=\"%1\$s\">%2\$s</a>")
    val divGif = "<img src=\"%s\" class=\"gif\">"
    val divImg = "<img src=\"%s\" class=\"media\">"

    override fun run() {
        if (exp.threadData == null) {
            progress(0f, false); return; }
        (DocumentFile.fromTreeUri(c.c, Uri.parse(Api.encode(exp.uri)))
            ?.createDirectory(exp.threadData!!.exported())).let {
                if (it == null) {
                    progress(0f, false)
                    return@run
                } else folder = it
            }
        progress(0f, false)
        // put assets there if each are necessary
        // create more pages if messages exceed some number
        for (dm in exp.threadData!!.items) {
            val sb = StringBuilder()
            sb.append("<div class=\"dm\">\n$divInd")
            var media: Versioned? = null
            val nonMedia = when {
                dm.action_log != null -> divHint.format(dm.action_log.description)
                dm.animated_media != null ->
                    divGif.format(dm.animated_media.images.fixed_height.url)
                dm.clip != null -> {
                    media = dm.clip.clip
                    ""
                }
                dm.felix_share != null -> {
                    media = dm.felix_share.video
                    divDial.format(dm.felix_share.text)
                }
                dm.like != null -> divDial.format(dm.like)
                dm.link != null -> divLink.format(dm.link.link_context.link_url, dm.link.text)
                dm.live_viewer_invite != null ->
                    divHintAndDial.format(
                        dm.live_viewer_invite.cta_button_name,
                        dm.live_viewer_invite.text
                    )
                dm.media != null -> {
                    media = dm.media
                    ""
                }
                dm.media_share != null -> {
                    media = dm.media_share
                    ""
                }
                dm.placeholder != null -> divHint.format(dm.placeholder.message)
                dm.profile != null -> divLink.format(
                    UiTools.PROFILE.format(dm.profile.username),
                    "@${dm.profile.username}"
                )
                dm.raven_media != null -> {
                    media = dm.raven_media
                    ""
                }
                dm.reel_share != null -> {
                    media = dm.reel_share.media
                    divHintAndDial.format(dm.reel_share.message, dm.reel_share.text)
                }
                dm.story_share != null -> {
                    media = dm.story_share.media
                    divHintAndDial.format(dm.story_share.message, dm.story_share.text)
                }
                dm.text != null -> divDial.format(dm.text)
                dm.video_call_event != null ->
                    divHint.format(dm.video_call_event.description)
                dm.voice_media != null ->
                    "<audio controls>\n" +
                            "$divInd  <source src=\"\" type=\"audio/mpeg\">\n" + // TODO: mime type ?
                            "$divInd</audio>"
                else -> ""
            }
            if (media != null) sb.append(
                when {
                    media.video_versions != null && exp.opt?.vid() == true ->
                        "<video width=\"500\" height=\"500\" controls>\n" +
                                "$divInd  <source src=\"\" type=\"video/mp4\">\n" +
                                "$divInd</video>"
                    media.video_versions != null && exp.opt?.vid() == true ||
                            media.video_versions == null && exp.opt?.img() == true ->
                        divImg.format()
                    else -> divImg.format() // placeholder icon
                } + "\n$divInd"
            )
            sb.append(nonMedia)
            sb.append("\n</div>\n")
            divisions.add(sb.toString())
        }
        progress(100f, true)
    }
}
