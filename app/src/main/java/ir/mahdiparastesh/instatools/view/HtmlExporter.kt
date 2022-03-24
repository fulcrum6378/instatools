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

abstract class HtmlExporter(c: Persistent, exp: Exportable) : BaseExporter(c, exp) {
    private lateinit var folder: DocumentFile
    private val dwnFolder by lazy {
        DocumentFile.fromTreeUri(c.c, Uri.parse(c.sPreference(Settings.spStorage)))!!
    }
    private val tmpDir by lazy {
        dwnFolder.findFile(TEMP_DIR) ?: dwnFolder.createDirectory(TEMP_DIR)!!
    }
    private val canCreateDirSelf = Exporter.canCreateDirSelf(c)
    private val subFolders = Array<DocumentFile?>(3) { null }
    private val containers = arrayListOf<List<String>>()
    private var divisions: ArrayList<String>? = null
    private var limit = 0

    private val subFolderNames = arrayOf("image", "video", "audio")
    private val fileTypes =
        arrayOf("image/jpg" to "jpg", "video/mp4" to "mp4", "audio/mp4" to "m4a")
    val maximum = 500
    private val divInd = "  "
    private val divDial = "<p class=\"dial\">%s</p>"
    private val divHint = "<p class=\"hint\">%s</p>"
    private val divLink = divDial.format("<a href=\"%1\$s\">%2\$s</a>")
    private val divGif = "<img src=\"%s\" class=\"gif\">"
    private val divImg = "<img src=\"./${subFolderNames[0]}/%s.jpg\" class=\"media\">"

    private fun hintAndDial(hint: String?, dial: String?) =
        (if (!hint.isNullOrBlank()) (divHint.format(hint) + "\n$divInd") else "") +
                (if (!dial.isNullOrBlank()) divDial.format(dial) else "")

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
                    dm.felix_share.text?.let { divDial.format(it) } ?: ""
                }
                dm.like != null -> divDial.format(dm.like)
                dm.link != null -> divLink.format(dm.link.link_context.link_url, dm.link.text)
                dm.live_viewer_invite != null ->
                    hintAndDial(
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
                    hintAndDial(dm.reel_share.message, dm.reel_share.text)
                }
                dm.story_share != null -> {
                    media = dm.story_share.media
                    hintAndDial(dm.story_share.message, dm.story_share.text)
                }
                dm.text != null -> divDial.format(dm.text)
                dm.video_call_event != null ->
                    divHint.format(dm.video_call_event.description)
                dm.voice_media != null -> {
                    limit += 4
                    "<audio controls>\n" +
                            "$divInd  <source src=\"./${subFolderNames[2]}/${dm.item_id}.m4a\"" +
                            " type=\"audio/mp4\">\n" +
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
                } + (if (nonMedia.isNotBlank()) "\n$divInd" else "")
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
            html.append( // MAKE JQUERY BE DOWNLOADED FROM WEB EACH TIME THE USER OPENS HTML
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
            // TODO: ADD A VERY SIMPLE PAGING ABILITY
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
            } else {
                val file = folder.createFile("text/html", "${i + 1}.html")!!
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
