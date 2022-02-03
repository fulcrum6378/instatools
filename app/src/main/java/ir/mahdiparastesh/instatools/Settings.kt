package ir.mahdiparastesh.instatools

import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.get
import androidx.core.view.iterator
import ir.mahdiparastesh.instatools.databinding.SettingsBinding
import ir.mahdiparastesh.instatools.more.BaseActivity

class Settings : BaseActivity() {
    private lateinit var b: SettingsBinding
    override val menuRes: Int? = null
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
            updateMainPath(uri.toString())
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

        // Font
        for (l in b.ll.iterator())
            if (l is LinearLayout) (l[0] as TextView).typeface = fontRegular
        b.stMainPath.typeface = fontLight

        // Main Path
        if (!prf.contains(spStorage)) mainPath()
        updateMainPath()
        b.stMainPath.setOnClickListener { mainPath() }
    }

    private fun mainPath() {
        saveLauncher.launch(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE))
    }

    private fun updateMainPath(value: String? = null) {
        b.stMainPath.text =
            Uri.decode(value ?: prf.getString(spStorage, ""))
    }
}
