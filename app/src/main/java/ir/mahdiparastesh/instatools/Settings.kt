package ir.mahdiparastesh.instatools

import android.content.Intent
import android.os.Bundle
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import ir.mahdiparastesh.instatools.Downloads.Companion.spStorage
import ir.mahdiparastesh.instatools.databinding.SettingsBinding
import ir.mahdiparastesh.instatools.more.BaseActivity
import ir.mahdiparastesh.instatools.more.Persistent

class Settings : BaseActivity() {
    private lateinit var b: SettingsBinding
    private val saveLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.data?.data == null) return@registerForActivityResult
            val uri = it.data!!.data!!
            contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            esp.edit().apply {
                putString(spStorage, uri.toString())
                apply()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = SettingsBinding.inflate(layoutInflater)
        setContentView(b.root)
        toolbar(b.toolbar, R.string.stTitle)

        sp = Persistent.initSp(c, m.acc)
        if (sp?.contains(spStorage) == false)
            saveLauncher.launch(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE))
    }
}
