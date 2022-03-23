package ir.mahdiparastesh.instatools.view

import android.annotation.SuppressLint
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import ir.mahdiparastesh.instatools.R
import ir.mahdiparastesh.instatools.Settings
import ir.mahdiparastesh.instatools.data.Exportable
import ir.mahdiparastesh.instatools.json.Api
import ir.mahdiparastesh.instatools.more.BaseExporter
import ir.mahdiparastesh.instatools.more.Persistent
import ir.mahdiparastesh.instatools.more.Versioned
import ir.mahdiparastesh.instatools.serv.Exporter
import kotlinx.coroutines.runBlocking
import java.io.FileOutputStream

@Suppress("MemberVisibilityCanBePrivate")
abstract class HtmlExporter(c: Persistent, exp: Exportable) : BaseExporter(c, exp) {
    lateinit var folder: DocumentFile
    val dwnFolder by lazy {
        DocumentFile.fromTreeUri(c.c, Uri.parse(c.sPreference(Settings.spStorage)))!!
    }
    val tmpDir by lazy { dwnFolder.createDirectory(TEMP_DIR)!! }
    val canCreateDirSelf = Exporter.canCreateDirSelf(c)
    val subFolders = Array<DocumentFile?>(3) { null }
    val containers = arrayListOf<List<String>>()
    var divisions: ArrayList<String>? = null
    var limit = 0
    var needJquery = false

    val subFolderNames = arrayOf("image", "video", "audio")
    val fileTypes = arrayOf("image/jpg" to "jpg", "video/mp4" to "mp4", "audio/mp4" to "mp4")
    val maximum = 500
    val divInd = "  "
    val divDial = "<p class=\"dial\">%s</p>"
    val divHint = "<p class=\"hint\">%s</p>"
    val divHintAndDial = divHint.format("%1\$s") + "\n$divInd" + divDial.format("%2\$s")
    val divLink = divDial.format("<a href=\"%1\$s\">%2\$s</a>")
    val divGif = "<img src=\"%s\" class=\"gif\">"
    val divImg = "<img src=\"./${subFolderNames[0]}/%s.jpg\" class=\"media\">"

    companion object {
        const val TEMP_DIR = ".export_temp"
    }

    @SuppressLint("NewApi")
    override fun run() {
        if (exp.threadData == null) {
            progress(0f, false); return; }

        val myUri = Uri.parse(Api.encode(exp.uri))
        (if (canCreateDirSelf) DocumentFile.fromSingleUri(c.c, myUri)
        else DocumentFile.fromTreeUri(c.c, myUri)?.createDirectory(exp.threadData!!.exported()))
            .apply {
                if (this == null) {
                    progress(0f, false)
                    return@run
                } else folder = this
            }
        if (canCreateDirSelf) tmpDir.listFiles().forEach { it.delete() }

        for (dwn in exp.media!!.entries) {
            val t = dwn.value.type.toInt()
            if (t == 3) continue
            if (subFolders[t] == null) subFolders[t] =
                (if (canCreateDirSelf) tmpDir else folder).createDirectory(subFolderNames[t])!!
            val ft = fileTypes[t]
            subFolders[t]!!.createFile(ft.first, "${dwn.key}.${ft.second}")!!.apply {
                c.c.contentResolver.openFileDescriptor(uri, "w")?.use { des ->
                    FileOutputStream(des.fileDescriptor).use { fos ->
                        fos.write(dwn.value.data)
                    }
                }
            }
        }
        exp.media = null
        if (canCreateDirSelf) subFolders.forEach {
            if (it != null)
                DocumentsContract.moveDocument(c.c.contentResolver, it.uri, tmpDir.uri, folder.uri)
        }

        progress(0f, false)
        for (dm in exp.threadData!!.items) {
            if (divisions == null) divisions = arrayListOf()
            val div = StringBuilder()
            div.append("<div class=\"dm\">\n$divInd")
            var media: Versioned? = null
            val nonMedia = when {
                dm.action_log != null -> divHint.format(dm.action_log.description)
                dm.animated_media != null -> {
                    limit += 2
                    divGif.format(dm.animated_media.images.fixed_height.url)
                }
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
                dm.voice_media != null -> {
                    limit += 4
                    "<audio controls>\n" +
                            "$divInd  <source src=\"./${subFolderNames[2]}/${dm.item_id}.mp4\" type=\"audio/mp4\">\n" +
                            "$divInd</audio>"
                }
                else -> ""
            }
            if (media != null) div.append(
                when {
                    media.video_versions != null && exp.opt?.vid() == true -> {
                        limit += 6
                        "<video width=\"500\" height=\"500\" controls>\n" +
                                "$divInd  <source src=\"./${subFolderNames[1]}/${dm.item_id}.mp4\"" +
                                " type=\"video/mp4\">\n" +
                                "$divInd</video>"
                    }
                    media.video_versions != null && exp.opt?.vid() == true ||
                            media.video_versions == null && exp.opt?.img() == true -> {
                        limit += 4
                        divImg.format(dm.item_id)
                    }
                    else -> {
                        limit += 2
                        divImg.format(dm.item_id) // placeholder icon
                    }
                } + "\n$divInd"
            )
            div.append(nonMedia)
            if (nonMedia.isNotBlank()) limit++
            div.append("\n</div>\n")
            divisions!!.add(div.toString())
            if (limit >= maximum) {
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

        containers.forEachIndexed { i, divisions ->
            val html = StringBuilder()
            html.append(
                """<!DOCTYPE HTML>
<html dir="${if (c.c.resources.getBoolean(R.bool.dirRtl)) "rtl" else "ltr"}">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width,initial-scale=1">
  <meta name="theme-color" media="(prefers-color-scheme: light)" content="#F5F5F5">
  <meta name="theme-color" media="(prefers-color-scheme: dark)" content="#222222">
  <title>${exp.threadData!!.exported()}</title>
</head>
<body>
"""
            )
            for (div in divisions) {
                html.append(div)
            }
            html.append(
                """</body>
</html>"""
            )
            if (canCreateDirSelf) {
                val tmp = tmpDir.createFile("text/html", "${i + 1}.html")!!
                runBlocking {
                    c.c.contentResolver.openFileDescriptor(tmp.uri, "w")?.use { des ->
                        FileOutputStream(des.fileDescriptor).use { fos ->
                            fos.write(html.toString().encodeToByteArray())
                        }
                    }
                }
                DocumentsContract.moveDocument(c.c.contentResolver, tmp.uri, tmpDir.uri, folder.uri)
            }
        }

        progress(100f, true)
    }
    /* In Android's DocumentsContract API, if you invoke an intent to create a document with
    * the mime type "vnd.android.document/directory", it does not mean that you can create
    * documents inside that directory directly using FileOutputStream. If you do that it'll
    * throw a SecurityException, instead make the files somewhere else and then move them there! */
}
