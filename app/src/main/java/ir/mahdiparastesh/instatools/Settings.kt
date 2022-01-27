package ir.mahdiparastesh.instatools

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.documentfile.provider.DocumentFile
import ir.mahdiparastesh.instatools.Downloads.Companion.spStorage
import ir.mahdiparastesh.instatools.databinding.SettingsBinding
import ir.mahdiparastesh.instatools.more.BaseActivity
import java.io.FileOutputStream
import java.util.*

class Settings : BaseActivity() {
    private lateinit var b: SettingsBinding
    private val saveLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.data?.data == null) return@registerForActivityResult
            val uri = it.data!!.data!!
            contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            sp.edit().apply {
                putString(spStorage, uri.toString())
                apply()
            }
            files()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = SettingsBinding.inflate(layoutInflater)
        setContentView(b.root)
        toolbar(b.toolbar, R.string.stTitle)

        if (!sp.contains(spStorage)) saveLauncher.launch(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE))
        else files()
    }

    private fun files() {
        val tree = DocumentFile.fromTreeUri(c, Uri.parse(sp.getString(spStorage, null)))!!
        val oldie = "1.txt"
        var file = tree.findFile(oldie)
        if (file == null) file = tree.createFile("text/plain", oldie)
        c.contentResolver.openFileDescriptor(file!!.uri, "wa")?.use { des ->
            FileOutputStream(des.fileDescriptor).use { fos ->
                fos.write("${Calendar.getInstance().timeInMillis}: Kun\n".encodeToByteArray())
            }
        }
    }
}
