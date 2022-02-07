package ir.mahdiparastesh.instatools

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.view.get
import androidx.core.view.iterator
import ir.mahdiparastesh.instatools.databinding.SettingsBinding
import ir.mahdiparastesh.instatools.more.BaseActivity
import ir.mahdiparastesh.instatools.more.DbFile
import ir.mahdiparastesh.instatools.more.ForegroundService
import java.io.File

@SuppressLint("ApplySharedPref")
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
        const val spMainPage = "main_page"
        const val spBranching = "branching"

        var recreateMain = false

        fun deleteDb(id: String) {
            arrayOf(
                DbFile(id, DbFile.Triple.MAIN),
                DbFile(id, DbFile.Triple.SHARED_MEMORY),
                DbFile(id, DbFile.Triple.WRITE_AHEAD_LOG),
            ).forEach { f -> if (f.exists()) f.delete() }
        }

        fun deleteSp(c: BaseActivity) {
            File(c.getDir("shared_prefs", Context.MODE_PRIVATE), "${c.m.acc!!.id}.xml")
        }
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
        b.stBranching.typeface = fontRegular

        // Main Path
        if (!prf.contains(spStorage)) mainPath()
        updateMainPath()
        b.stMainPath.setOnClickListener { mainPath() }
        b.stBranching.isChecked = prf.getBoolean(spBranching, true)
        b.stBranching.setOnCheckedChangeListener { _, bb ->
            prf.edit().putBoolean(spBranching, bb).commit()
        }

        // User Data
        b.stResetData.setOnClickListener {
            AlertDialog.Builder(this).apply {
                setTitle(R.string.stResetData)
                setMessage(R.string.stResetDataSure)
                setNegativeButton(R.string.no, null)
                setPositiveButton(R.string.yes) { _, _ ->
                    ForegroundService.terminateTasks(c)
                    deleteDb(m.acc!!.id.toString())
                    recreateMain = true
                }
            }.create().show()
        }
        b.stResetSettings.setOnClickListener {
            AlertDialog.Builder(this).apply {
                setTitle(R.string.stResetSettings)
                setMessage(R.string.stResetSettingsSure)
                setNegativeButton(R.string.no, null)
                setPositiveButton(R.string.yes) { _, _ ->
                    ForegroundService.terminateTasks(c)
                    deleteSp(this@Settings)
                    recreateMain = true
                }
            }.create().show()
        }
    }

    private fun mainPath() {
        saveLauncher.launch(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE))
    }

    private fun updateMainPath(value: String? = null) {
        b.stMainPath.text =
            Uri.decode(value ?: prf.getString(spStorage, ""))
    }
}
