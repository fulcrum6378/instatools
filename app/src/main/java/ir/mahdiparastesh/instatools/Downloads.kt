package ir.mahdiparastesh.instatools

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.documentfile.provider.DocumentFile
import ir.mahdiparastesh.instatools.databinding.DownloadsBinding
import ir.mahdiparastesh.instatools.json.Api
import ir.mahdiparastesh.instatools.json.Media
import ir.mahdiparastesh.instatools.more.BaseActivity
import java.io.FileOutputStream

class Downloads : BaseActivity() {
    private lateinit var b: DownloadsBinding

    companion object {
        const val spStorage = "storage"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = DownloadsBinding.inflate(layoutInflater)
        setContentView(b.root)
        toolbar(b.toolbar, R.string.dwTitle)

        intent.getStringExtra(Intent.EXTRA_TEXT)?.let { handleLink(it) }
        //https://www.instagram.com/tv/CZMS8OXBS_r/?utm_medium=share_sheet
        //https://instagram.com/stories/vesnaparapapa/2759673704843926757?utm_medium=share_sheet
    }

    private fun handleLink(link: String) {
        Api<Media.MediaWrapperApi>(
            this, link.substringBefore("?") + "?__a=1", Media.MediaWrapperApi::class.java
        ) { wrapper ->
            val med = wrapper.items[0]
            when {
                med.carousel_media != null -> {
                }
                med.image_versions2 != null -> {
                }
                else -> Toast.makeText(c, "Sorry dunno what to do!?!?", Toast.LENGTH_LONG).show()
            }
        }
        // TODO: RECOGNISE BY ID LATER
    }

    private fun download(user: String, mediaId: String) {
        TODO()
    }

    private fun save(user: String, mediaId: String, mimeType: String, data: ByteArray) {
        val stem = DocumentFile.fromTreeUri(c, Uri.parse(sp.getString(spStorage, null)))!!
        var branch = stem.findFile(user)
        if (branch == null) branch = stem.createDirectory(user)
        var leaf = branch!!.findFile(mediaId)
        if (leaf == null) leaf = branch.createFile(mimeType, mediaId)
        c.contentResolver.openFileDescriptor(leaf!!.uri, "w")?.use { des ->
            FileOutputStream(des.fileDescriptor).use { fos -> fos.write(data) }
        }
    }
}
