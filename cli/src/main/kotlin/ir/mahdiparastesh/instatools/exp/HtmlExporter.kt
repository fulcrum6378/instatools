package ir.mahdiparastesh.instatools.exp

import ir.mahdiparastesh.instatools.api.Media
import ir.mahdiparastesh.instatools.job.Exporter
import ir.mahdiparastesh.instatools.util.Utils
import java.util.*

class HtmlExporter(exportable: Exporter.Exportable) : BaseExporter(exportable) {
    override val method: Exporter.Method = Exporter.Method.HTML

    private val containers = arrayListOf<List<String>>()
    private var divisions: ArrayList<String>? = null // temporarily a page's contents
    private var limit = 0
    private val div1Ind = "      "
    private val div2Ind = "$div1Ind  "
    private val divDial = "<p class=\"dial\">%s</p>"
    private val divHint = "<p class=\"hint\">%s</p>"
    private val divLink = divDial.format("\n$div2Ind  <a href=\"%1\$s\">%2\$s</a>%3\$s\n$div2Ind")
    private val divGif = "<img src=\"%s\" class=\"gif\">"
    private val dirRtl = false // FIXME

    private fun hintAndDial(hint: String?, dial: String?) =
        (if (!hint.isNullOrBlank()) divHint.format(hint) else "") +
                (if (!hint.isNullOrBlank() && !dial.isNullOrBlank()) "\n$div2Ind" else "") +
                (if (!dial.isNullOrBlank()) divDial.format(dial) else "")

    companion object {
        const val MAX_PAGINATION = 3
        const val MAX_PER_PAGE = 200
    }

    init {
        for (i in exp.thread.items.indices) {
            val dm = exp.thread.items[i]
            if (divisions == null) divisions = arrayListOf()
            val div = StringBuilder()
            var showPro =
                divisions.isNullOrEmpty() || exp.thread.items[i - 1].is_sent_by_viewer
                        || exp.thread.items[i - 1].action_log != null

            // Date
            val cal = Utils.calendar(Utils.compileMicrosecondsTS(dm.timestamp))
            var showDate = true
            if (i > 0 && !divisions.isNullOrEmpty()) {
                val prev = Utils.calendar(
                    Utils.compileMicrosecondsTS(exp.thread.items[i - 1].timestamp)
                )
                if (cal[Calendar.YEAR] == prev[Calendar.YEAR] &&
                    cal[Calendar.MONTH] == prev[Calendar.MONTH] &&
                    cal[Calendar.DAY_OF_MONTH] == prev[Calendar.DAY_OF_MONTH]
                ) showDate = false
            }
            if (showDate) {
                div.append(
                    "    <p class=\"date text-center${if (!divisions.isNullOrEmpty()) " mt-5" else ""} " +
                            "mb-2\">${cal[Calendar.YEAR]}.${Utils.z(cal[Calendar.MONTH] + 1)}." +
                            "${Utils.z(cal[Calendar.DAY_OF_MONTH])}</p>\n"
                )
                showPro = true
            }
            if (dm.action_log != null) continue

            // Media
            var media: Media? = null
            var mediaVideoAllowed = true
            var mediaQuality: Float? = null
            val nonMedia = when {
                dm.animated_media != null -> {
                    limit += 2
                    divGif.format(dm.animated_media.images.fixed_height.url)
                }

                dm.clip != null -> {
                    media = dm.clip.clip
                    mediaVideoAllowed = (exp.reel ?: exp.video) != Media.Version.THUMB
                    mediaQuality = if (mediaVideoAllowed) exp.reel ?: exp.video else exp.post ?: exp.image
                    ""
                }

                dm.direct_media_share != null -> {
                    media = dm.direct_media_share.media
                    mediaVideoAllowed = (exp.uploadedVideo ?: exp.video) != Media.Version.THUMB
                    mediaQuality =
                        if (dm.direct_media_share.media.video_versions != null && mediaVideoAllowed)
                            exp.uploadedVideo ?: exp.video
                        else exp.uploadedImage ?: exp.image
                    dm.direct_media_share.text
                }

                dm.felix_share != null -> {
                    media = dm.felix_share.video
                    mediaVideoAllowed = (exp.reel ?: exp.video) != Media.Version.THUMB
                    mediaQuality = if (mediaVideoAllowed) exp.reel ?: exp.video else exp.post ?: exp.image
                    dm.felix_share.text?.let { divDial.format(it) } ?: ""
                }

                dm.like != null -> divDial.format(dm.like)
                dm.link != null -> divLink.format(dm.link.link_context.link_url, dm.link.text, "")
                dm.live_viewer_invite != null -> hintAndDial(
                    dm.live_viewer_invite.cta_button_name, dm.live_viewer_invite.text
                )

                dm.media != null -> {
                    media = dm.media
                    mediaVideoAllowed = (exp.uploadedVideo ?: exp.video) != Media.Version.THUMB
                    mediaQuality =
                        if (dm.media.video_versions != null && mediaVideoAllowed)
                            exp.uploadedVideo ?: exp.video
                        else exp.uploadedImage ?: exp.image
                    ""
                }

                dm.media_share != null -> {
                    media = dm.media_share
                    mediaVideoAllowed = (exp.reel ?: exp.video) != Media.Version.THUMB
                    mediaQuality =
                        if (dm.media_share.video_versions != null && mediaVideoAllowed)
                            exp.reel ?: exp.video
                        else exp.uploadedImage ?: exp.image
                    ""
                }

                dm.placeholder != null -> divHint.format(dm.placeholder.message)
                dm.profile != null -> divLink.format(
                    Utils.PROFILE.format(dm.profile.username), "@${dm.profile.username}",
                    " <i>[User ID: ${dm.profile.pk}]</i>"
                )

                dm.raven_media != null -> {
                    media = dm.raven_media
                    mediaVideoAllowed = (exp.uploadedVideo ?: exp.video) != Media.Version.THUMB
                    mediaQuality =
                        if (dm.raven_media.video_versions != null && mediaVideoAllowed)
                            exp.uploadedVideo ?: exp.video
                        else exp.uploadedImage ?: exp.image
                    ""
                }

                dm.reel_share != null -> {
                    media = dm.reel_share.media
                    mediaVideoAllowed = (exp.reel ?: exp.video) != Media.Version.THUMB
                    mediaQuality = if (mediaVideoAllowed) exp.reel ?: exp.video else exp.post ?: exp.image
                    hintAndDial(dm.reel_share.message, dm.reel_share.text)
                }

                dm.story_share != null -> {
                    media = dm.story_share.media
                    if (dm.story_share.media?.video_versions != null && mediaVideoAllowed)
                        exp.uploadedVideo ?: exp.video
                    else exp.uploadedImage ?: exp.image
                    hintAndDial(dm.story_share.message, dm.story_share.text)
                }

                dm.text != null -> divDial.format(dm.text)
                dm.video_call_event != null ->
                    divHint.format(dm.video_call_event.description)

                dm.voice_media != null -> when {
                    exp.voice && dm.voice_media.media != null -> {
                        limit += 4
                        "<audio controls>\n" +
                                "$div2Ind  <source src=\"${dm.item_id}.m4a\" type=\"audio/mp4\">\n" +
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
                exp.thread.users.find { it.pk == userId }?.apply {
                    hRef = Utils.PROFILE.format(username)
                    imgTitle = " title=\"$full_name\""
                }
                @Suppress("SpellCheckingInspection")
                div.append(
                    "$div1Ind<a href=\"$hRef\">\n$div1Ind  <img ${
                        when {
                            exp.image == null ->
                                "src=\"data:image/gif;base64,R0lGODlhAQABAAD/ACwAAAAAAQABAAACADs=\"" +
                                        " style=\"background-color: #33AADD;\" "

                            showPro -> "src=\"${Exporter.USER_PROFILE_IMG.format(userId)}.jpg\" "

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
                    media.video_versions != null && mediaVideoAllowed -> {
                        limit += 6
                        ("<a href=\"${media.link()}\">\n$div2Ind  %s\n$div2Ind</a>").format(
                            "<video width=\"500\" height=\"500\" controls class=\"media\">\n" +
                                    "$div2Ind    <source src=\"${dm.item_id}.mp4\" type=\"video/mp4\">\n" +
                                    "$div2Ind  </video>"
                        )
                    }

                    (media.video_versions != null && mediaVideoAllowed) ||
                            (media.video_versions == null && mediaQuality != null) -> {
                        limit += 4
                        val imgThumb =
                            "<img src=\"${dm.item_id}.jpg\" class=\"media\">"
                        ("<a href=\"${media.link()}\">\n$div2Ind  $imgThumb\n$div2Ind</a>")
                    }

                    else -> divHint.format(
                        "<a href=\"${media.link()}\">" +
                                "${if (media.video_versions != null) "Video" else "Image"} file omitted!" +
                                "</a>"
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
                        "<p class=\"time\">${Utils.z(cal[Calendar.HOUR_OF_DAY])}:" +
                        "${Utils.z(cal[Calendar.MINUTE])}:${Utils.z(cal[Calendar.SECOND])}</p>\n" +
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
            if (dirRtl) "<link href=\"https://cdn.jsdelivr.net/npm/bootstrap@5.1.3/" +
                    "dist/css/bootstrap.rtl.min.css\"" +
                    "      integrity=\"sha384-+qdLaIRZfNu4cVPK/PxJJEy0B0f3Ugv8i" +
                    "482AKY7gwXwhaCroABd086ybrVKTa0q\"" +
                    "      rel=\"stylesheet\" crossorigin=\"anonymous\">"
            else "<link href=\"https://cdn.jsdelivr.net/npm/bootstrap@5.1.3/" +
                    "dist/css/bootstrap.min.css\"\n" +
                    "      integrity=\"sha384-1BmE4kWBq78iYhFldvKuhfTAU6auU8tT94Wr" +
                    "HftjDbrCEXSU1oBoqyl2QvZ6jIW3\"\n" +
                    "      rel=\"stylesheet\" crossorigin=\"anonymous\">"
        containers.forEachIndexed { page, divisions ->
            val html = StringBuilder()
            @Suppress("SpellCheckingInspection")
            html.append(
                """<!DOCTYPE HTML>
<html dir="${if (dirRtl) "rtl" else "ltr"}">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width,initial-scale=1">
  <meta name="theme-color" media="(prefers-color-scheme: light)" content="#F5F5F5">
  <meta name="theme-color" media="(prefers-color-scheme: dark)" content="#222222">
  <title>${exp.name}</title>
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
                }>Prev</a>
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
                }>Next</a>
        </li>
      </ul>
    </nav>
  </main>
  <p id="copyright">
    Created by <a href="https://github.com/fulcrum6378/instatools_cli">InstaTools</a>
    from <a href="${Utils.MAHDI}">Mahdi Parastesh</a>
  </p>
</body>
</html>"""
            )
            write(html.toString().encodeToByteArray(), page)
        }
    }
}