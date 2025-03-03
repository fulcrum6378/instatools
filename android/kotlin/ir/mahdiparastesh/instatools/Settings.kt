package ir.mahdiparastesh.instatools

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.os.StatFs
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.SeekBar
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.edit
import androidx.documentfile.provider.DocumentFile
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import ir.mahdiparastesh.instatools.InstaTools.Companion.isPathAccessible
import ir.mahdiparastesh.instatools.data.Account
import ir.mahdiparastesh.instatools.data.DownloadHistory
import ir.mahdiparastesh.instatools.databinding.AlsoRevokePermBinding
import ir.mahdiparastesh.instatools.databinding.FolderAliasBinding
import ir.mahdiparastesh.instatools.databinding.ListAliasBinding
import ir.mahdiparastesh.instatools.databinding.SettingsBinding
import ir.mahdiparastesh.instatools.util.BaseActivity
import ir.mahdiparastesh.instatools.util.DbFile
import ir.mahdiparastesh.instatools.util.ForegroundService
import ir.mahdiparastesh.instatools.util.Utils.getOrNull
import ir.mahdiparastesh.instatools.view.MaterialMenu
import ir.mahdiparastesh.instatools.view.UiTools.showBytes
import ir.mahdiparastesh.instatools.view.UiTools.vis
import ir.mahdiparastesh.instatools.view.UiTools.vish
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileInputStream

class Settings : BaseActivity(), ActivityResultCallback<ActivityResult> {
    private lateinit var b: SettingsBinding
    private lateinit var prf: SharedPreferences
    private var globalMode = true
    private val saveLauncher = launcherForResult(this)
    private var aliases: HashMap<String, String>? = null
    private var cacheLimit: Long = defSpCacheLimit
    private val cacheMin: Int by lazy { resources.getInteger(R.integer.stCacheMin) }
    private val cacheMax: Int by lazy { cacheMin + resources.getInteger(R.integer.stCacheMaxNominal) }
    private var uriFolders: ArrayList<Uri>? = null

    override val menuRes = R.menu.settings_tlb
    override val com: ActivityCompanion get() = Companion

    companion object : ActivityCompanion() {
        // Preferences
        const val spStorage = "storage"
        const val spBranching = "branching"
        const val spBranchingCb = "branching_checked"
        const val defSpBranching = true
        const val defSpBranchingCb = true
        const val spAutoDeleteEmptyDirs = "auto_delete_empty_dirs"
        const val spAutoDeleteEmptyDirsCb = "auto_delete_empty_dirs_checked"
        const val defSpAutoDeleteEmptyDirs = false
        const val defSpAutoDeleteEmptyDirsCb = false
        private const val spAliases = "aliases"

        // Mere-Global Preferences
        const val spCacheLimit = "cache_limit"
        private const val defSpCacheLimit = 100L * 1024L * 1024L

        // Hidden Preferences
        const val spMainPage = "main_page"
        const val defSpMainPage = 1
        const val spExpOptions = "export_options"

        // Mere-Global Hidden Preferences
        const val spFirstOpenApp = "first_open_app" // def: null
        const val spOpenAppCount = "open_app_count" // def: 0L
        const val spDownloadCount = "download_count" // def: 0L
        const val spDlErrorCount = "download_error_count" // def: 0L
        const val spUnsaveCount = "unsave_count" // def: 0L
        const val spShortcutCount = "shortcut_count" // def: 0L
        const val spLearntSelection = "learnt_selection" // def: false
        const val spLearntSwipeDelete = "learnt_swipe_delete" // def: false
        const val spUsedVersion = "used_version"


        const val EXTRA_IS_GLOBAL = "isGlobal"
        const val EXTRA_SELECT_PATH = "selectPath"
        private const val MB = 1048576L
        val allSps = arrayOf(
            spStorage, spBranching, spBranchingCb, spAutoDeleteEmptyDirs, spAutoDeleteEmptyDirsCb,
            spAliases, spCacheLimit, spMainPage, spExpOptions
        )
        var recreateMain = false

        fun deleteDb(id: String) {
            arrayOf(
                DbFile(id, DbFile.Triple.MAIN),
                DbFile(id, DbFile.Triple.SHARED_MEMORY),
                DbFile(id, DbFile.Triple.WRITE_AHEAD_LOG),
            ).forEach { f -> if (f.exists()) f.delete() }
        }

        fun deleteSp(c: BaseActivity, acc: Account = c.c.acc!!) {
            File(c.getDir("shared_prefs", MODE_PRIVATE), "${acc.id}.xml")
                .apply { if (exists()) delete() }
        }

        fun Context.cacheSize() = cacheDir.walk().sumOf { it.length() } - 4096L

        fun defaultCacheLimit(c: Context): Long = c.getExternalFilesDir(null)?.let {
            val minie = c.resources.getInteger(R.integer.stCacheMin)
            val maxie = c.resources.getInteger(R.integer.stCacheMaxNominal) + minie
            val stat = StatFs(it.path)
            var ret = (stat.blockSizeLong * stat.availableBlocksLong) / 275L
            if (ret < minie.toBytes()) ret = minie.toBytes()
            if (ret > maxie.toBytes()) ret = maxie.toBytes()
            return ret
        } ?: defSpCacheLimit

        fun Long.toMBs() = (this / MB).toInt()

        fun Int.toBytes() = this * MB

        suspend fun loadAliases(c: InstaTools, global: Boolean): HashMap<String, String> {
            val map = (if (global) c.gsp else c.sp!!).getString(spAliases, null)
                ?.let { Json.decodeFromString<HashMap<String, String>>(it) }
                ?: hashMapOf()
            val removal = arrayListOf<String>()
            map.forEach { (k, v) ->
                val ex = DocumentFile.fromTreeUri(c, Uri.parse(v))?.exists()
                val ax = c.isPathAccessible(v)
                if (!ax || ex != true) {
                    if (ex != true && ax) Uri.parse(v).release(c, global)
                    removal.add(k)
                }
            }
            if (removal.isNotEmpty()) {
                for (k in removal) map.remove(k)
                saveAliases(if (global) c.gsp else c.sp!!, map)
            }
            return map
        }

        fun saveAliases(sp: SharedPreferences, aliases: HashMap<String, String>?) {
            if (aliases == null) return
            sp.edit { putString(spAliases, Json.encodeToString(aliases)) }
        }

        fun Uri.folderName() = path.toString().split("/").last()

        @SuppressLint("SdCardPath")
        @Suppress("RedundantSuspendModifier")
        suspend fun Uri.release(c: InstaTools, global: Boolean) {
            val exc = arrayOf(
                "${if (global) InstaTools.GSP else c.acc!!.id}.xml",
                "AwOriginVisitLoggerPrefs.xml", "WebViewChromiumPrefs.xml"
            )
            val f0 = ">$this<"
            val f1 = "&quot;$this&quot;"
            File(
                "/data/data/${BuildConfig.APPLICATION_ID}/shared_prefs"
            ).listFiles()?.filter { it.name !in exc }?.forEach { sp ->
                val raw = FileInputStream(sp).use { it.readBytes().toString(Charsets.UTF_8) }
                /*Log.println(
                    Log.ASSERT, "AIMI", sp.name + " : " + (f0 in raw) + " or " + (f1 in raw)
                )*/
                if (f0 in raw || f1 in raw) return
            }
            try {
                c.contentResolver.releasePersistableUriPermission(
                    this,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (_: SecurityException) {
            } // No permission grants found for UID XXX and Uri content://...
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        intent.extras?.getBoolean(EXTRA_IS_GLOBAL)?.let { globalMode = it }
        b = SettingsBinding.inflate(layoutInflater)
        setContentView(b.root)
        initToolbar(
            b.toolbar, R.string.settings,
            changeTitleTo = getString(if (globalMode) R.string.gSettings else R.string.aSettings)
        )
        prf = if (globalMode || c.sp == null) c.gsp else c.sp!!

        // beauty
        b.sv.setOnScrollChangeListener { _, _, scrollY, _, _ ->
            b.tbShadow.vish(scrollY > 0)
        }

        // main path
        if (intent.hasExtra(EXTRA_SELECT_PATH)) selectPath()
        updateMainPath()
        b.stMainPath.setOnClickListener { v ->
            if ((v as AppCompatTextView).text.isEmpty())
                selectPath()
            else MaterialMenu(this@Settings, v, R.menu.settings_main_path,
                R.id.smpChange to { selectPath() },
                R.id.smpRemove to {
                    val uri = prf.getString(spStorage, null)?.let { Uri.parse(it) }
                    if (uri != null) {
                        prf.edit { remove(spStorage) }
                        updateMainPath("")
                        CoroutineScope(Dispatchers.IO).launch {
                            DownloadHistory.folderRemoved(c, uri)
                            uri.release(c, globalMode)
                        }
                    }
                }
            ).show()
        }
        if (!globalMode) {
            arrayOf(b.stBranchingCb, b.stAutoDeleteEmptyDirsCb).forEach { it.vis(true) }
            arrayOf(b.stBranching, b.stAutoDeleteEmptyDirs).forEach {
                it.setPaddingRelative(
                    resources.getDimension(R.dimen.stSwitchPad).toInt(), 0, 0, 0
                )
            }
            b.stBranchingCb.isChecked = prf.getBoolean(spBranchingCb, defSpBranchingCb)
            b.stBranching.isEnabled = b.stBranchingCb.isChecked
            b.stBranchingCb.setOnCheckedChangeListener { _, bb ->
                prf.edit { putBoolean(spBranchingCb, bb) }
                b.stBranching.isEnabled = bb
            }
            b.stAutoDeleteEmptyDirsCb.isChecked =
                prf.getBoolean(spAutoDeleteEmptyDirsCb, defSpAutoDeleteEmptyDirsCb)
            b.stAutoDeleteEmptyDirs.isEnabled = b.stAutoDeleteEmptyDirsCb.isChecked
            b.stAutoDeleteEmptyDirsCb.setOnCheckedChangeListener { _, bb ->
                prf.edit { putBoolean(spAutoDeleteEmptyDirsCb, bb) }
                b.stAutoDeleteEmptyDirs.isEnabled = bb
            }
        }
        b.stBranching.isChecked = prf.getBoolean(spBranching, defSpBranching)
        b.stBranching.setOnCheckedChangeListener { _, bb ->
            prf.edit { putBoolean(spBranching, bb) }
        }
        b.stAutoDeleteEmptyDirs.isChecked =
            prf.getBoolean(spAutoDeleteEmptyDirs, defSpAutoDeleteEmptyDirs)
        b.stAutoDeleteEmptyDirs.setOnCheckedChangeListener { _, bb ->
            prf.edit { putBoolean(spAutoDeleteEmptyDirs, bb) }
        }

        // alias paths
        CoroutineScope(Dispatchers.IO).launch {
            aliases = loadAliases(c, globalMode)
            withContext(Dispatchers.Main) { showAliases() }
        }
        b.stAddAlias.setOnClickListener { editAlias(null) }

        // caching
        cacheLimit = c.gsp.getLong(spCacheLimit, defaultCacheLimit(c))
        b.stCacheLimit.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                cacheLimit = (progress + cacheMin).toBytes()
                updateCacheLimit()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {
                c.gsp.edit { putLong(spCacheLimit, cacheLimit) }
            }
        })
        if (!globalMode) {
            b.stCache.vis(false)
            b.stSepCache.vis(false)
        } else b.stClearCache.setOnClickListener {
            clearCache()
            updateCacheSize()
        }

        // user data
        if (globalMode) b.stResetData.vis(false)
        else b.stResetData.setOnClickListener {
            MaterialAlertDialogBuilder(this).apply {
                setTitle(R.string.stResetData)
                setMessage(R.string.stResetDataSure)
                setNegativeButton(R.string.no, null)
                setPositiveButton(R.string.yes) { _, _ ->
                    ForegroundService.terminateTasks(c)
                    CoroutineScope(Dispatchers.IO).launch { deleteDb(c.acc!!.id.toString()) }
                    recreateMain = true
                }
            }.show()
        }
        b.stResetSettings.setOnClickListener {
            MaterialAlertDialogBuilder(this).apply {
                setTitle(R.string.stResetSettings)
                setMessage(R.string.stResetSettingsSure)
                setNegativeButton(R.string.no, null)
                setPositiveButton(R.string.yes) { _, _ ->
                    ForegroundService.terminateTasks(c)
                    prf.edit { allSps.forEach { remove(it) } }
                    recreate()
                }
            }.show()
        }
    }

    override fun onResume() {
        super.onResume()
        updateCacheSize()
        updateCacheLimit(true)
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.stHelp -> MaterialAlertDialogBuilder(this).apply {
                setTitle(R.string.stHelp)
                setMessage(R.string.stHelpMessage)
                setNeutralButton(R.string.ok, null)
            }.show()
        }
        return super.onMenuItemClick(item)
    }

    private var selectingPathFor: Int = 0 // 0=>Main, 1=>Specific
    private fun selectPath(selectingPathFor: Int = 0) {
        this.selectingPathFor = selectingPathFor
        saveLauncher.launch(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE))
    }

    private fun updateMainPath(value: String? = null) {
        b.stMainPath.text = Uri.decode(value ?: prf.getString(spStorage, ""))
    }

    private var clearingCache = false
    fun Context.clearCache() {
        clearingCache = true
        CoroutineScope(Dispatchers.IO)
            .launch { cacheDir.deleteRecursively() }
            .invokeOnCompletion { clearingCache = false }
    }

    private fun updateCacheSize() {
        CoroutineScope(Dispatchers.IO).launch {
            val cacheSize = getString(R.string.stCache, c.showBytes(c.cacheSize()))
            withContext(Dispatchers.Main) { b.stCaching.text = cacheSize }
        }
    }

    private fun updateCacheLimit(updateSb: Boolean = false) {
        val limitInMbs = cacheLimit.toMBs()
        b.stCacheLimitTv.text = getString(
            R.string.maximum, if (limitInMbs != cacheMax)
                resources.getStringArray(R.array.bytes)[2].format(limitInMbs)
            else getString(R.string.unlimited)
        )
        if (updateSb) b.stCacheLimit.progress = limitInMbs - cacheMin
    }

    private fun showAliases() {
        b.stAliases.adapter = aliases?.let { aliases ->
            object : ArrayAdapter<Map.Entry<String, String>>(
                this, 0, aliases.toSortedMap().entries.toList()
            ) {
                override fun getView(i: Int, convertView: View?, parent: ViewGroup): View {
                    val ba = convertView?.let { ListAliasBinding.bind(it) }
                        ?: ListAliasBinding.inflate(layoutInflater, parent, false)
                    ba.profile.text = getItem(i)?.key
                    ba.path.text = getItem(i)?.value?.let { Uri.parse(it) }?.folderName()
                    ba.root.setOnClickListener { editAlias(getItem(i)?.key) }
                    return ba.root
                }
            }
        }
    }

    private var editingAlias: AlertDialog? = null
    private var bfa: FolderAliasBinding? = null
    private fun editAlias(u: String? = null) {
        editingAlias = MaterialAlertDialogBuilder(this).apply {
            setTitle(R.string.stAliasing)
            setMessage(R.string.stAliasingDesc)
            bfa = FolderAliasBinding.inflate(layoutInflater)
            bfa!!.aliasProfile.setText(u)
            c.fav?.also { fav ->
                bfa!!.aliasProfile.setAdapter(
                    ArrayAdapter(this@Settings, android.R.layout.simple_dropdown_item_1line,
                        fav.map { it.user })
                )
            }
            bfa!!.listPaths()
            if (aliases != null) bfa!!.folders
                .setSelection(uriFolders?.indexOfFirst { it.toString() == aliases!![u] } ?: 0)
            bfa!!.aliasAddFolder.setOnClickListener { selectPath(1) }
            setView(bfa!!.root)
            setPositiveButton(R.string.save) { _, _ ->
                val newU = bfa!!.aliasProfile.text.toString()
                if (newU.isBlank()) return@setPositiveButton
                if (newU != u) aliases?.remove(u)
                uriFolders?.getOrNull(bfa!!.folders.selectedItemPosition)?.also {
                    aliases?.set(newU, it.toString())
                }
                saveAliases(prf, aliases)
                showAliases()
            }
            setNegativeButton(R.string.cancel, null)
            setNeutralButton(R.string.remove) { _, _ ->
                val uri = u?.let { aliases?.getOrNull(it) }?.let { Uri.parse(it) }
                    ?: return@setNeutralButton
                val br = AlsoRevokePermBinding.inflate(layoutInflater)
                MaterialAlertDialogBuilder(this@Settings).apply {
                    setTitle(R.string.remove)
                    setView(br.root)
                    setPositiveButton(R.string.sContinue) { _, _ ->
                        aliases?.remove(u)
                        saveAliases(prf, aliases)
                        if (br.root.isChecked) {
                            CoroutineScope(Dispatchers.IO).launch {
                                DownloadHistory.folderRemoved(c, uri)
                                uri.release(c, globalMode)
                            }
                            CoroutineScope(Dispatchers.IO).launch {
                                aliases = loadAliases(c, globalMode)
                                withContext(Dispatchers.Main) { showAliases() }
                            }
                        } else showAliases()
                    }
                    setNegativeButton(R.string.cancel, null)
                }.show()
            }
            setOnDismissListener { bfa = null }
        }.show()
    }

    private fun FolderAliasBinding.listPaths() {
        uriFolders = ArrayList(contentResolver.persistedUriPermissions.map { it.uri }
            .sortedBy { it.folderName() })
        uriFolders?.removeAll { it.toString() == prf.getString(spStorage, "null") }
        folders.adapter = ArrayAdapter(
            this@Settings, R.layout.spinner, uriFolders!!.map { it.folderName() })
            .apply { setDropDownViewResource(R.layout.spinner_dd_tertiary) }
    }

    override fun onActivityResult(result: ActivityResult) {
        val uri = result.data?.data ?: return
        contentResolver.takePersistableUriPermission(
            uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
        when (selectingPathFor) {
            0 -> {
                // Remove the previous path if existed
                val prevUri = prf.getString(spStorage, null)?.let { Uri.parse(it) }
                if (prevUri != null) CoroutineScope(Dispatchers.IO).launch {
                    DownloadHistory.folderRemoved(c, uri)
                    uri.release(c, globalMode)
                }

                // Set the new path
                prf.edit { putString(spStorage, uri.toString()) }
                updateMainPath(uri.toString())
                if (intent.hasExtra(EXTRA_SELECT_PATH)) {
                    Downloads.initService(this)
                    try {
                        @Suppress("DEPRECATION") onBackPressed()
                    } catch (_: java.lang.IllegalStateException) {
                        // FragmentManager is already executing transactions.
                    }
                    goTo(Downloads::class, animate = false)
                    // if you call finish() here, Downloads will be loaded without a background
                    // corruptly over the previous Activity in an ugly way.
                }
            }
            1 -> {
                bfa?.listPaths()
                bfa?.folders?.setSelection(uriFolders!!.indexOfFirst { it.toString() == uri.toString() })
                CoroutineScope(Dispatchers.IO).launch {
                    DownloadHistory.folderAdded(c, uri)
                }
            }
        }
        c.downloadHistory = null
        CoroutineScope(Dispatchers.IO).launch { DownloadHistory.saveCache(c) }
        // this doesn't update the cache, it just clears it, it'll get updated automatically later.
    }
}
