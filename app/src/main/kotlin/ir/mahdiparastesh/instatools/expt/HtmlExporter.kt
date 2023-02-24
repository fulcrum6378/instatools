package ir.mahdiparastesh.instatools.expt

import android.annotation.SuppressLint
import android.provider.DocumentsContract
import ir.mahdiparastesh.instatools.R
import ir.mahdiparastesh.instatools.data.Exportable
import ir.mahdiparastesh.instatools.json.Media
import ir.mahdiparastesh.instatools.json.Versioned
import ir.mahdiparastesh.instatools.serv.Exporter
import ir.mahdiparastesh.instatools.view.UiTools
import ir.mahdiparastesh.instatools.view.UiTools.calendar
import ir.mahdiparastesh.instatools.view.UiTools.xFromMicroseconds
import ir.mahdiparastesh.instatools.view.UiTools.z
import kotlinx.coroutines.runBlocking
import java.io.FileOutputStream
import java.util.*

abstract class HtmlExporter(c: Exporter, exp: Exportable) : MultiExporter(c, exp) {
    private val containers = arrayListOf<List<String>>()
    private var divisions: ArrayList<String>? = null // temporarily a page's contents
    private val dirRtl = c.c.resources.getBoolean(R.bool.dirRtl)
    private var limit = 0
    private val div1Ind = "      "
    private val div2Ind = "$div1Ind  "
    private val divDial = "<p class=\"dial\">%s</p>"
    private val divHint = "<p class=\"hint\">%s</p>"
    private val divLink = divDial.format("\n$div2Ind  <a href=\"%1\$s\">%2\$s</a>%3\$s\n$div2Ind")
    private val divGif = "<img src=\"%s\" class=\"gif\">"

    private fun hintAndDial(hint: String?, dial: String?) =
        (if (!hint.isNullOrBlank()) divHint.format(hint) else "") +
                (if (!hint.isNullOrBlank() && !dial.isNullOrBlank()) "\n$div2Ind" else "") +
                (if (!dial.isNullOrBlank()) divDial.format(dial) else "")

    companion object {
        const val MAX_PAGINATION = 3
        const val MAX_PER_PAGE = 200
    }

    @SuppressLint("NewApi")
    override fun run() {
        if (exp.threadData == null) {
            progress(0f, false); return; }
        super.run()

        progress(0f, false)
        for (i in exp.threadData!!.items.indices) {
            val dm = exp.threadData!!.items[i]
            if (divisions == null) divisions = arrayListOf()
            val div = StringBuilder()
            var showPro =
                divisions.isNullOrEmpty() || exp.threadData!!.items[i - 1].is_sent_by_viewer
                        || exp.threadData!!.items[i - 1].action_log != null

            // Date
            val cal = dm.timestamp.xFromMicroseconds().calendar()
            var showDate = true
            if (i > 0 && !divisions.isNullOrEmpty()) {
                val prev = exp.threadData!!.items[i - 1].timestamp.xFromMicroseconds().calendar()
                if (cal[Calendar.YEAR] == prev[Calendar.YEAR] &&
                    cal[Calendar.MONTH] == prev[Calendar.MONTH] &&
                    cal[Calendar.DAY_OF_MONTH] == prev[Calendar.DAY_OF_MONTH]
                ) showDate = false
            }
            if (showDate) {
                div.append(
                    "    <p class=\"date text-center${if (!divisions.isNullOrEmpty()) " mt-5" else ""} " +
                            "mb-2\">${cal[Calendar.YEAR]}.${z(cal[Calendar.MONTH] + 1)}." +
                            "${z(cal[Calendar.DAY_OF_MONTH])}</p>\n"
                )
                showPro = true
            }
            if (dm.action_log != null) continue

            // Media
            var media: Versioned? = null
            val nonMedia = when {
                dm.animated_media != null -> {
                    limit += 2
                    divGif.format(dm.animated_media.images.fixed_height.url)
                }
                dm.clip != null -> {
                    media = dm.clip.clip
                    ""
                }
                dm.direct_media_share != null -> {
                    media = dm.direct_media_share.media
                    dm.direct_media_share.text
                }
                dm.felix_share != null -> {
                    media = dm.felix_share.video
                    dm.felix_share.text?.let { divDial.format(it) } ?: ""
                }
                dm.like != null -> divDial.format(dm.like)
                dm.link != null -> divLink.format(dm.link.link_context.link_url, dm.link.text, "")
                dm.live_viewer_invite != null -> hintAndDial(
                    dm.live_viewer_invite.cta_button_name, dm.live_viewer_invite.text
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
                    UiTools.PROFILE.format(dm.profile.username), "@${dm.profile.username}",
                    " <i>[User ID: ${dm.profile.pk}]</i>"
                )
                dm.raven_media != null -> {
                    media = dm.raven_media
                    ""
                }
                dm.reel_share != null -> {
                    media = dm.reel_share.media
                    hintAndDial(dm.reel_share.message, dm.reel_share.text)
                }
                dm.story_share != null -> {
                    media = dm.story_share.media
                    hintAndDial(dm.story_share.message, dm.story_share.text)
                }
                dm.text != null -> divDial.format(dm.text)
                dm.video_call_event != null ->
                    divHint.format(dm.video_call_event.description)
                dm.voice_media != null ->
                    when {
                        exp.opt?.voi() == true && dm.voice_media.media != null -> {
                            limit += 4
                            "<audio controls>\n" +
                                    "$div2Ind  <source src=\"./${subFolderNames[2]}/${dm.item_id}.m4a\"" +
                                    " type=\"audio/mp4\">\n" +
                                    "$div2Ind</audio>"
                        }
                        dm.voice_media.media == null ->
                            divHint.format("Sent a voice message.")
                        else -> divHint.format("Voice message omitted!")
                    }
                else -> ""
            }
            div.append( // "flex-direction" is direction-relative.
                "    <div class=\"dm\" style=\"flex-direction: " +
                        "row${if (dm.is_sent_by_viewer) "-reverse" else ""};\">\n"
            )
            if (!dm.is_sent_by_viewer) {
                val userId = dm.user_id.toLong().toString()
                var hRef = "#"
                var imgTitle = ""
                exp.threadData?.users?.find { it.pk == userId }?.apply {
                    hRef = UiTools.PROFILE.format(username)
                    imgTitle = " title=\"$full_name\""
                }
                @Suppress("SpellCheckingInspection")
                div.append(
                    "$div1Ind<a href=\"$hRef\">\n$div1Ind  <img ${
                        when {
                            exp.opt?.img() != true ->
                                "src=\"data:image/gif;base64,R0lGODlhAQABAAD/ACwAAAAAAQABAAACADs=\"" +
                                        " style=\"background-color: #33AADD;\" "
                            showPro -> "src=\"./${subFolderNames[0]}/" +
                                    "${Exporter.USER_PROFILE_IMG.format(userId)}.jpg\" "
                            else -> ""
                        }
                    }class=\"profile${if (!showPro) " repeated" else ""}\"$imgTitle>\n$div1Ind</a>\n"
                )
            }
            div.append(
                "$div1Ind<div class=\"d-inline-flex p-2 border rounded-3 mt-1 px-3 btn disabled " +
                        (if (dm.is_sent_by_viewer) "btn-light" else "btn-outline-dark") +
                        "${if (media != null) " card" else ""}\">\n$div2Ind"
            )
            if (media != null) div.append(
                when {
                    media.video_versions != null && exp.opt?.actVid() == true -> {
                        limit += 6
                        (if (media is Media)
                            "<a href=\"${media.link()}\">\n$div2Ind  %s\n$div2Ind</a>" else "%s").format(
                            "<video width=\"500\" height=\"500\" controls class=\"media\">\n" +
                                    "$div2Ind    <source src=\"./${subFolderNames[1]}/${dm.item_id}.mp4\"" +
                                    " type=\"video/mp4\">\n" +
                                    "$div2Ind  </video>"
                        )
                    }
                    (media.video_versions != null && exp.opt?.video == 3) ||
                            (media.video_versions == null && exp.opt?.img() == true) -> {
                        limit += 4
                        val imgThumb =
                            "<img src=\"./${subFolderNames[0]}/${dm.item_id}.jpg\" class=\"media\">"
                        (if (media is Media)
                            "<a href=\"${media.link()}\">\n$div2Ind  $imgThumb\n$div2Ind</a>"
                        else imgThumb) // don't use string formatting instead of imgThumb!
                    }
                    else -> divHint.format(
                        (if (media is Media) "<a href=\"${media.link()}\">" else "") +
                                "${if (media.video_versions != null) "Video" else "Image"} file omitted!" +
                                (if (media is Media) "</a>" else "")
                    )
                } + (if (nonMedia.isNotBlank()) "\n$div2Ind" else "")
            )
            limit += 1
            div.append(nonMedia)
            if (nonMedia.isNotBlank()) limit++
            if (dm.reactions != null) {
                div.append("\n$div2Ind<p class=\"reactions\">")
                for (r in dm.reactions.emojis) div.append(r.emoji)
                div.append("</p>")
            }
            div.append(
                "\n$div1Ind</div>\n$div1Ind" +
                        "<p class=\"time\">${z(cal[Calendar.HOUR_OF_DAY])}:" +
                        "${z(cal[Calendar.MINUTE])}:${z(cal[Calendar.SECOND])}</p>\n" +
                        "    </div>\n"
            )
            divisions!!.add(div.toString())
            if (limit >= MAX_PER_PAGE) {
                containers.add(divisions!!.toList())
                divisions = null
                limit = 0
            }
        }
        if (divisions != null) {
            containers.add(divisions!!.toList())
            divisions = null
            limit = 0
        }

        @Suppress("SpellCheckingInspection")
        val bootstrapCss =
            if (dirRtl) "<link href=\"https://cdn.jsdelivr.net/npm/bootstrap@5.1.3/dist/css/bootstrap.rtl.min.css\"" +
                    "      integrity=\"sha384-+qdLaIRZfNu4cVPK/PxJJEy0B0f3Ugv8i482AKY7gwXwhaCroABd086ybrVKTa0q\"" +
                    "      rel=\"stylesheet\" crossorigin=\"anonymous\">"
            else "<link href=\"https://cdn.jsdelivr.net/npm/bootstrap@5.1.3/dist/css/bootstrap.min.css\"\n" +
                    "      integrity=\"sha384-1BmE4kWBq78iYhFldvKuhfTAU6auU8tT94WrHftjDbrCEXSU1oBoqyl2QvZ6jIW3\"\n" +
                    "      rel=\"stylesheet\" crossorigin=\"anonymous\">"
        @Suppress("SpellCheckingInspection")
        containers.forEachIndexed { page, divisions ->
            val html = StringBuilder()
            html.append(
                """<!DOCTYPE HTML>
<html dir="${if (dirRtl) "rtl" else "ltr"}">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width,initial-scale=1">
  <meta name="theme-color" media="(prefers-color-scheme: light)" content="#F5F5F5">
  <meta name="theme-color" media="(prefers-color-scheme: dark)" content="#222222">
  <title>${exp.threadData!!.exported()}</title>
  <base target="_blank">
  $bootstrapCss
  <script src="https://code.jquery.com/jquery-3.6.0.slim.min.js"></script>
  <script src="https://cdn.jsdelivr.net/npm/bootstrap@5.1.3/dist/js/bootstrap.bundle.min.js"
      integrity="sha384-ka7Sk0Gln4gmtz2MlQnikT1wXgYsOg+OMhuP+IlRH9sENBO0LRn5q+8nbTov4+1p"
      crossorigin="anonymous"></script>
  <style>
body { word-break: break-all; }
p { margin-bottom: 0; }
body { background: #FCFCFC; }
@media (prefers-color-scheme: dark) {
    body { background: #222 !important; color: #EEE !important; }
    a { color: #FFF !important; }
    a:hover { color: #E66 !important; }
    .date, .time { color: #BBB !important; }
    .dm .btn-outline-dark { color: #F8F9FA !important; }
    .dm .btn-light { background-color: #52565A !important; color: #FFF !important; }
    .page-link { background-color: inherit !important; }
}
.date, .time { color: #888; }
.date { font-size: 17px; }
.time { font-size: 13px; padding: 0 0.5rem; }
.dm { display: flex; align-items: flex-end; }
.profile { width: 2.5rem; height: 2.5rem; border-radius: 50%; margin: 5px 12px 0 12px; align-self: normal; }
.profile.repeated { opacity: 0; }
.dm .btn { cursor: auto; user-select: text; opacity: 1 !important; pointer-events: auto; max-width: 514px; }
.dm .btn-light { text-align: right; }
.dm .btn-outline-dark { text-align: left; }
.hint { opacity: .7; font-style: italic; }
.media { width: 100%; max-width: 480px; }
.reactions { width: 0; height: 0; overflow: visible; align-self: flex-end; z-index: 1; }
#copyright { opacity: .3; font-size: 14px; padding: 12px 17px; }
  </style>
</head>

<body>
  <main class="container border mt-4 mb-3 rounded pt-3 pb-4">
""" // .dm .btn-light +{ display: flex !important; flex-direction: row-reverse; flex-wrap: wrap; }
            )
            for (div in divisions) html.append(div)
            html.append(
                """    <nav class="mt-5">
      <ul class="pagination justify-content-center">
        <li class="page-item${if (page == 0) " disabled" else ""}">
          <a class="page-link" href="./$page.html" target="_self"${
                    if (page == 0) " tabindex=\"-1\"" else ""
                }>${c.c.resources.getString(R.string.prev)}</a>
        </li>
"""
            )
            var pMin = 0
            var pMax = containers.size - 1
            if (page > MAX_PAGINATION) pMin = page - MAX_PAGINATION
            if ((pMax - page) > MAX_PAGINATION) pMax = page + MAX_PAGINATION + 1
            val range = (pMin..pMax).toMutableList()
            if (!range.contains(0)) range.add(0, 0)
            if (!range.contains(containers.size - 1)) range.add(range.size, containers.size - 1)
            for (p in range) html.append(
                "        <li class=\"page-item${if (p == page) " disabled" else ""}\">" +
                        "<a class=\"page-link\" href=\"./${p + 1}.html\" target=\"_self\"" +
                        "${if (p == page) " tabindex=\"-1\"" else ""}>${p + 1}</a></li>\n"
            )
            val canNext = page == containers.size - 1
            html.append(
                """        <li class="page-item${if (canNext) " disabled" else ""}">
          <a class="page-link" href="./${page + 2}.html" target="_self"${
                    if (canNext) " tabindex=\"-1\"" else ""
                }>${c.c.resources.getString(R.string.next)}</a>
        </li>
      </ul>
    </nav>
  </main>
  <p id="copyright">
    Created by <a href="https://www.instagram.com/instatools.apk/">InstaTools</a>
    app from <a href="${UiTools.MP}">Mahdi Parastesh</a>
  </p>
</body>
</html>"""
            )
            if (canCreateDirSelf) {
                val tmp = tmpDir.createFile("text/html", "${page + 1}.html")!!
                runBlocking {
                    c.c.contentResolver.openFileDescriptor(tmp.uri, "w")?.use { des ->
                        FileOutputStream(des.fileDescriptor).use { fos ->
                            fos.write(html.toString().encodeToByteArray())
                        }
                    }
                }
                DocumentsContract.moveDocument(
                    c.c.contentResolver, tmp.uri, tmpDir.uri, rescueFolder?.uri ?: folder.uri
                )
            } else {
                val file = folder.createFile("text/html", "${page + 1}.html")!!
                c.c.contentResolver.openFileDescriptor(file.uri, "w")?.use { des ->
                    FileOutputStream(des.fileDescriptor).use { fos ->
                        fos.write(html.toString().encodeToByteArray())
                    }
                }
            }
        }

        progress(100f, true)
    }
    /* In Android's DocumentsContract API, if you invoke an intent to create a document with
    * the mime type "vnd.android.document/directory", it does not mean that you can create
    * documents inside that directory directly using FileOutputStream. If you do that it'll
    * throw a SecurityException, instead make the files somewhere else and then move them there! */
}
