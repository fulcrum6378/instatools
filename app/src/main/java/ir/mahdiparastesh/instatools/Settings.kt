package ir.mahdiparastesh.instatools

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultCallback
import androidx.appcompat.app.AlertDialog
import androidx.core.view.get
import androidx.core.view.iterator
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import ir.mahdiparastesh.instatools.data.Account
import ir.mahdiparastesh.instatools.databinding.SettingsBinding
import ir.mahdiparastesh.instatools.more.BaseActivity
import ir.mahdiparastesh.instatools.more.DbFile
import ir.mahdiparastesh.instatools.more.ForegroundService
import ir.mahdiparastesh.instatools.view.UiTools
import ir.mahdiparastesh.instatools.view.UiTools.Companion.stylise
import ir.mahdiparastesh.instatools.view.UiTools.Companion.vish
import java.io.File

class Settings : BaseActivity(), ActivityResultCallback<ActivityResult> {
    private lateinit var b: SettingsBinding
    private lateinit var adBanner: AdView
    private lateinit var prf: SharedPreferences
    private var globalMode = false
    private var giveLinkBack: String? = null
    private val saveLauncher = launcher(this)

    override val menuRes = R.menu.settings_tlb
    override val com: ActivityCompanion get() = Companion

    companion object : ActivityCompanion() {
        // Preferences
        const val spStorage = "storage"
        const val spBranching = "branching"
        const val defSpBranching = true
        const val spAutoDeleteEmptyDirs = "auto_delete_empty_dirs"
        const val defSpAutoDeleteEmptyDirs = false

        // Hidden Preferences
        const val spMainPage = "main_page"
        const val defSpMainPage = 1
        const val spFollowerDelay = "follower_delay"
        const val defSpFollowerDelay = 60 * 1000L
        const val spNotifiedUnfTill = "notified_unf_till" // def: 0L
        const val spUnfLastChecked = "unf_last_checked" // def: 0L
        const val spDownloadCount = "download_count" // def: 0L


        const val EXTRA_IS_GLOBAL = "isGlobal"
        const val EXTRA_GIVE_LINK_BACK = "giveLinkBack"
        val allSps = arrayOf(spStorage, spBranching, spMainPage, spAutoDeleteEmptyDirs)
        var recreateMain = false

        fun deleteDb(id: String) {
            arrayOf(
                DbFile(id, DbFile.Triple.MAIN),
                DbFile(id, DbFile.Triple.SHARED_MEMORY),
                DbFile(id, DbFile.Triple.WRITE_AHEAD_LOG),
            ).forEach { f -> if (f.exists()) f.delete() }
        }

        @Suppress("unused")
        fun deleteSp(c: BaseActivity, acc: Account = c.m.acc!!) {
            File(c.getDir("shared_prefs", Context.MODE_PRIVATE), "${acc.id}.xml")
                .apply { if (exists()) delete() }
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
        intent.getStringExtra(EXTRA_GIVE_LINK_BACK)?.let { giveLinkBack = it }

        // Beauty
        for (l in b.ll.iterator())
            if (l is LinearLayout) (l[0] as TextView).typeface = fontRegular
        b.stMainPath.typeface = fontLight
        arrayOf(b.stBranching, b.stAutoDeleteEmptyDirs, b.stResetData, b.stResetSettings)
            .forEach { it.typeface = fontRegular }
        b.sv.viewTreeObserver.addOnScrollChangedListener {
            b.tbShadow.vish(b.sv.scrollY > 0)
        }

        // Main Path
        if (giveLinkBack != null) selectMainPath()
        updateMainPath()
        b.stMainPath.setOnClickListener { selectMainPath() }
        b.stBranching.isChecked = prf.getBoolean(spBranching, defSpBranching)
        b.stBranching.setOnCheckedChangeListener { _, bb ->
            prf.edit().putBoolean(spBranching, bb).commit()
        }
        b.stAutoDeleteEmptyDirs.isChecked =
            prf.getBoolean(spAutoDeleteEmptyDirs, defSpAutoDeleteEmptyDirs)
        b.stAutoDeleteEmptyDirs.setOnCheckedChangeListener { _, bb ->
            prf.edit().putBoolean(spAutoDeleteEmptyDirs, bb).commit()
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
            }.show().stylise(this)
        }
        b.stResetSettings.setOnClickListener {
            AlertDialog.Builder(this).apply {
                setTitle(R.string.stResetSettings)
                setMessage(R.string.stResetSettingsSure)
                setNegativeButton(R.string.no, null)
                setPositiveButton(R.string.yes) { _, _ ->
                    ForegroundService.terminateTasks(c)
                    prf.edit().apply {
                        allSps.forEach { remove(it) }
                        commit()
                    }
                    recreate()
                }
            }.show().stylise(this)
        }

        // Ads
        adBanner = UiTools.adaptiveBanner(this, "ca-app-pub-9457309151954418/9910778917")
        b.root.addView(adBanner, UiTools.adaptiveBannerLp())
        adBanner.loadAd(AdRequest.Builder().build())
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.stHelp -> AlertDialog.Builder(this).apply {
                setTitle(R.string.stHelp)
                setMessage(R.string.stHelpMessage)
                setNeutralButton(R.string.ok, null)
            }.show().stylise(this)
        }
        return super.onMenuItemClick(item)
    }

    private fun selectMainPath() {
        saveLauncher.launch(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE))
    }

    private fun updateMainPath(value: String? = null) {
        b.stMainPath.text = Uri.decode(value ?: prf.getString(spStorage, ""))
    }

    override fun onActivityResult(result: ActivityResult) {
        if (result.data?.data == null) return
        val uri = result.data!!.data!!
        contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        prf.edit().putString(spStorage, uri.toString()).commit()
        updateMainPath(uri.toString())
        if (giveLinkBack != null) {
            Downloads.initService(this, giveLinkBack)
            giveLinkBack = null
            goTo(Downloads::class, true)
        }
    }
}
