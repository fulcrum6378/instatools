package ir.mahdiparastesh.instatools

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.StatFs
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultCallback
import androidx.appcompat.app.AlertDialog
import androidx.documentfile.provider.DocumentFile
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.gson.Gson
import ir.mahdiparastesh.instatools.data.Account
import ir.mahdiparastesh.instatools.data.StorageCache
import ir.mahdiparastesh.instatools.databinding.AlsoRevokePermBinding
import ir.mahdiparastesh.instatools.databinding.FolderAliasBinding
import ir.mahdiparastesh.instatools.databinding.ListAliasBinding
import ir.mahdiparastesh.instatools.databinding.SettingsBinding
import ir.mahdiparastesh.instatools.more.BaseActivity
import ir.mahdiparastesh.instatools.more.DbFile
import ir.mahdiparastesh.instatools.more.ForegroundService
import ir.mahdiparastesh.instatools.more.Persistent
import ir.mahdiparastesh.instatools.more.Persistent.Companion.isPathAccessible
import ir.mahdiparastesh.instatools.serv.Exporter
import ir.mahdiparastesh.instatools.view.UiTools
import ir.mahdiparastesh.instatools.view.UiTools.showBytes
import ir.mahdiparastesh.instatools.view.UiTools.vis
import ir.mahdiparastesh.instatools.view.UiTools.vish
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class Settings : BaseActivity(), ActivityResultCallback<ActivityResult> {
    private lateinit var b: SettingsBinding
    private lateinit var adBanner: AdView
    private lateinit var prf: SharedPreferences
    private var globalMode = true
    private var giveLinkBack: String? = null
    private val saveLauncher = launcher(this)
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
        const val defSpBranching = true
        const val spAutoDeleteEmptyDirs = "auto_delete_empty_dirs"
        const val defSpAutoDeleteEmptyDirs = false
        private const val spAliases = "aliases"

        // Mere-Global Preferences
        const val spCacheLimit = "cache_limit"
        private const val defSpCacheLimit = 100L * 1024L * 1024L

        // Hidden Preferences
        const val spMainPage = "main_page"
        const val defSpMainPage = 1
        const val spFollowerDelay = "follower_delay"
        const val defSpFollowerDelay = 60 * 1000L
        const val spNotifiedUnfTill = "notified_unf_till" // def: 0L
        const val spUnfLastChecked = "unf_last_checked" // def: 0L
        const val spExpOptions = "export_options"

        // Mere-Global Hidden Preferences
        const val spOpenAppCount = "open_app_count" // def: 0L
        const val spDownloadCount = "download_count" // def: 0L
        const val spUnfollowCount = "unfollow_count" // def: 0L
        const val spExportCount = "export_count" // def: 0L
        const val spDlErrorCount = "download_error_count" // def: 0L
        const val spLearntSelection = "learnt_selection" // def: false
        const val spLearntSwipeDelete = "learnt_swipe_delete" // def: false
        const val spLearntDmNotSeen = "learnt_dm_not_seen" // def: false
        const val spRatedUs = "rated_us" // def: false
        const val spUsedVersion = "used_version"


        const val EXTRA_IS_GLOBAL = "isGlobal"
        const val EXTRA_GIVE_LINK_BACK = "giveLinkBack"
        private const val MB = 1048576L
        val allSps = arrayOf(spStorage, spBranching, spMainPage, spAutoDeleteEmptyDirs)
        var recreateMain = false

        @Suppress("RedundantSuspendModifier")
        suspend fun deleteDb(id: String) {
            arrayOf(
                DbFile(id, DbFile.Triple.MAIN),
                DbFile(id, DbFile.Triple.SHARED_MEMORY),
                DbFile(id, DbFile.Triple.WRITE_AHEAD_LOG),
            ).forEach { f -> if (f.exists()) f.delete() }
        }

        @Suppress("RedundantSuspendModifier")
        suspend fun deleteSp(c: BaseActivity, acc: Account = c.m.acc!!) {
            File(c.getDir("shared_prefs", Context.MODE_PRIVATE), "${acc.id}.xml")
                .apply { if (exists()) delete() }
        }

        @Suppress("RedundantSuspendModifier")
        suspend fun Context.cacheSize() = cacheDir.walk().sumOf { it.length() } - 4096L

        private var clearingCache = false
        fun Context.clearCache() {
            clearingCache = true
            CoroutineScope(Dispatchers.IO)
                .launch { cacheDir.deleteRecursively() }
                .invokeOnCompletion { clearingCache = false }
        }

        fun defaultCacheLimit(c: Context): Long = c.getExternalFilesDir(null)?.let {
            val minie = c.resources.getInteger(R.integer.stCacheMin)
            val maxie = c.resources.getInteger(R.integer.stCacheMaxNominal) + minie
            val stat = StatFs(it.path)
            var ret = (stat.blockSizeLong * stat.availableBlocksLong) / 275L
            if (ret < minie.toBytes()) ret = minie.toBytes()
            if (ret > maxie.toBytes()) ret = maxie.toBytes()
            return ret
        } ?: defSpCacheLimit

        suspend fun Persistent.clearCacheIfNecessary() {
            if (Exporter.active.value == true) return
            if (c.cacheSize() > gsp.getLong(spCacheLimit, defaultCacheLimit(c)))
                c.clearCache()
        }

        fun Long.toMBs() = (this / MB).toInt()

        fun Int.toBytes() = this * MB

        @Suppress("RedundantSuspendModifier")
        suspend fun loadAliases(c: Context, sp: SharedPreferences): HashMap<String, String> {
            val map = sp.getString(spAliases, null)
                ?.let { Gson().fromJson<HashMap<String, String>>(it, HashMap::class.java) }
                ?: hashMapOf()
            val removal = arrayListOf<String>()
            map.forEach { (k, v) ->
                val ex = DocumentFile.fromTreeUri(c, Uri.parse(v))?.exists()
                val ax = c.isPathAccessible(v)
                if (!ax || ex != true) {
                    if (ex != true && ax) Uri.parse(v).release(c.contentResolver)
                    removal.add(k)
                }
            }
            if (removal.isNotEmpty()) {
                for (k in removal) map.remove(k)
                saveAliases(sp, map)
            }
            return map
        }

        fun saveAliases(sp: SharedPreferences, aliases: HashMap<String, String>?) {
            if (aliases == null) return
            sp.edit().putString(spAliases, Gson().toJson(aliases)).apply()
        }

        fun Uri.folderName() = path.toString().split("/").last()

        fun Uri.release(cr: ContentResolver) {
            cr.releasePersistableUriPermission(this, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            cr.releasePersistableUriPermission(this, Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }

        fun Persistent.incrementCounter(key: String) {
            gsp.edit().putLong(key, gsp.getLong(key, 0L) + 1L).apply()
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
        prf = if (globalMode || sp == null) gsp else sp!!
        intent.getStringExtra(EXTRA_GIVE_LINK_BACK)?.let { giveLinkBack = it }

        // Beauty
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            b.sv.setOnScrollChangeListener { _, _, scrollY, _, _ ->
                b.tbShadow.vish(scrollY > 0)
            }
        else b.sv.viewTreeObserver.addOnScrollChangedListener {
            b.tbShadow.vish(b.sv.scrollY > 0)
        }

        // Main Path
        if (giveLinkBack != null) selectPath()
        updateMainPath()
        b.stMainPath.setOnClickListener { selectPath() }
        b.stBranching.isChecked = prf.getBoolean(spBranching, defSpBranching)
        b.stBranching.setOnCheckedChangeListener { _, bb ->
            prf.edit().putBoolean(spBranching, bb).apply()
        }
        b.stAutoDeleteEmptyDirs.isChecked =
            prf.getBoolean(spAutoDeleteEmptyDirs, defSpAutoDeleteEmptyDirs)
        b.stAutoDeleteEmptyDirs.setOnCheckedChangeListener { _, bb ->
            prf.edit().putBoolean(spAutoDeleteEmptyDirs, bb).apply()
        }

        // Alias Paths
        CoroutineScope(Dispatchers.IO).launch {
            aliases = loadAliases(c, prf)
            withContext(Dispatchers.Main) { showAliases() }
        }
        b.stAddAlias.setOnClickListener { editAlias(null) }

        // Caching
        cacheLimit = gsp.getLong(spCacheLimit, defaultCacheLimit(c))
        b.stCacheLimit.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                cacheLimit = (progress + cacheMin).toBytes()
                updateCacheLimit()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {
                gsp.edit().putLong(spCacheLimit, cacheLimit).apply()
            }
        })
        if (!globalMode) {
            b.stCache.vis(false)
            b.stSepCache.vis(false)
        } else b.stClearCache.setOnClickListener {
            if (Exporter.active.value == true) {
                Toast.makeText(c, R.string.stClearCacheWaitExporter, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            clearCache()
            updateCacheSize()
        }

        // User Data
        if (globalMode) b.stResetData.vis(false)
        else b.stResetData.setOnClickListener {
            AlertDialog.Builder(this).apply {
                setTitle(R.string.stResetData)
                setMessage(R.string.stResetDataSure)
                setNegativeButton(R.string.no, null)
                setPositiveButton(R.string.yes) { _, _ ->
                    ForegroundService.terminateTasks(c)
                    CoroutineScope(Dispatchers.IO).launch { deleteDb(m.acc!!.id.toString()) }
                    recreateMain = true
                }
            }.show()
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
                        apply()
                    }
                    recreate()
                }
            }.show()
        }

        // Ads
        adBanner = UiTools.adaptiveBanner(this, R.string.bnrBtmSettings)
        b.root.addView(adBanner, UiTools.adaptiveBannerLp())
        adBanner.loadAd(AdRequest.Builder().build())
    }

    override fun onResume() {
        super.onResume()
        updateCacheSize()
        updateCacheLimit(true)
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.stHelp -> AlertDialog.Builder(this).apply {
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
        editingAlias = AlertDialog.Builder(this).apply {
            setTitle(R.string.stAliasing)
            setMessage(R.string.stAliasingDesc)
            bfa = FolderAliasBinding.inflate(layoutInflater)
            bfa!!.aliasProfile.setText(u)
            m.fav?.also { fav ->
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
                val uri = u?.let { aliases?.getOrElse(it) { null } }?.let { Uri.parse(it) }
                    ?: return@setNeutralButton
                val br = AlsoRevokePermBinding.inflate(layoutInflater)
                AlertDialog.Builder(this@Settings).apply {
                    setTitle(R.string.remove)
                    setView(br.root)
                    setPositiveButton(R.string.sContinue) { _, _ ->
                        aliases?.remove(u)
                        saveAliases(prf, aliases)
                        if (br.root.isChecked) {
                            CoroutineScope(Dispatchers.IO).launch {
                                StorageCache.folderRemoved(this@Settings, uri)
                                uri.release(contentResolver)
                            }
                            CoroutineScope(Dispatchers.IO).launch {
                                aliases = loadAliases(c, prf)
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
            .apply { setDropDownViewResource(R.layout.spinner_dd) }
    }

    override fun onActivityResult(result: ActivityResult) {
        val uri = result.data?.data ?: return
        contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        when (selectingPathFor) {
            0 -> {
                prf.edit().putString(spStorage, uri.toString()).apply()
                updateMainPath(uri.toString())
                if (giveLinkBack != null) {
                    Downloads.initService(this, giveLinkBack)
                    giveLinkBack = null
                    try {
                        onBackPressed()
                    } catch (e: java.lang.IllegalStateException) {
                        // by androidx.fragment.app.FragmentManager.ensureExecReady()
                        // which has multiple throwing conditions.
                        // All the 7 crashes occurred with Android 9 on five Galaxy models.
                        if (BuildConfig.DEBUG) throw e // TODO analyse this
                    }
                    goTo(Downloads::class, animate = false)
                    // If you call finish() here, Downloads will be loaded without a background
                    // corruptly over the previous Activity in an ugly way.
                }
            }
            1 -> {
                bfa?.listPaths()
                bfa?.folders?.setSelection(uriFolders!!.indexOfFirst { it.toString() == uri.toString() })
                CoroutineScope(Dispatchers.IO).launch {
                    StorageCache.folderAdded(this@Settings, uri)
                }
            }
        }
        m.files = null
        CoroutineScope(Dispatchers.IO).launch { StorageCache.saveStorageCache(this@Settings) }
    }
}
