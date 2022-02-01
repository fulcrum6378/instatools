package ir.mahdiparastesh.instatools

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import ir.mahdiparastesh.instatools.databinding.SettingsBinding
import ir.mahdiparastesh.instatools.more.BaseActivity

class Settings : BaseActivity() {
    private lateinit var b: SettingsBinding
    private lateinit var prf: SharedPreferences
    private var globalMode = false
    private val saveLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.data?.data == null) return@registerForActivityResult
            val uri = it.data!!.data!!
            contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            prf.edit().putString(spStorage, uri.toString()).commit()
        }

    companion object {
        const val EXTRA_IS_GLOBAL = "isGlobal"
        const val spStorage = "storage"

        // Hidden

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        intent.extras?.getBoolean(EXTRA_IS_GLOBAL)?.let { globalMode = it }
        b = SettingsBinding.inflate(layoutInflater)
        setContentView(b.root)
        toolbar(
            b.toolbar, R.string.settings,
            changeTitleTo = getString(if (globalMode) R.string.gSettings else R.string.aSettings)
        )
        prf = if (globalMode || sp == null) gsp else sp!!

        if (!prf.contains(spStorage))
            saveLauncher.launch(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE))
    }
}
