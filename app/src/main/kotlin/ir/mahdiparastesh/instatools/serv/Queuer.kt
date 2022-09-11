package ir.mahdiparastesh.instatools.serv

import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.Message
import androidx.documentfile.provider.DocumentFile
import com.android.volley.DefaultRetryPolicy
import com.android.volley.NetworkResponse
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.toolbox.HttpHeaderParser
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.coremedia.iso.IsoFile
import com.coremedia.iso.boxes.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.googlecode.mp4parser.MemoryDataSourceImpl
import com.googlecode.mp4parser.util.Path
import ir.mahdiparastesh.instatools.BuildConfig
import ir.mahdiparastesh.instatools.Downloads
import ir.mahdiparastesh.instatools.R
import ir.mahdiparastesh.instatools.Settings
import ir.mahdiparastesh.instatools.Settings.Companion.clearCacheIfNecessary
import ir.mahdiparastesh.instatools.Settings.Companion.incrementCounter
import ir.mahdiparastesh.instatools.data.Queued
import ir.mahdiparastesh.instatools.data.StorageCache
import ir.mahdiparastesh.instatools.json.*
import ir.mahdiparastesh.instatools.json.Api.Companion.adder
import ir.mahdiparastesh.instatools.more.BaseThread
import ir.mahdiparastesh.instatools.more.ForegroundService
import ir.mahdiparastesh.instatools.more.Persistent
import ir.mahdiparastesh.instatools.more.ServiceOwnerActivity
import ir.mahdiparastesh.instatools.view.Notify
import ir.mahdiparastesh.instatools.view.UiTools
import ir.mahdiparastesh.instatools.view.UiTools.xFromSeconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.apache.commons.imaging.Imaging
import org.apache.commons.imaging.formats.jpeg.JpegImageMetadata
import org.apache.commons.imaging.formats.jpeg.exif.ExifRewriter
import org.apache.commons.imaging.formats.tiff.constants.ExifTagConstants
import org.apache.commons.imaging.formats.tiff.constants.TiffTagConstants
import org.apache.commons.imaging.formats.tiff.write.TiffOutputSet
import java.io.*
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList


class Queuer : ForegroundService() {
    private var dest: String? = null
    private var handlingLinks = CopyOnWriteArrayList<Link>()
    private var handlingLink = false
    private var download: BaseThread? = null
    private val stem by lazy { DocumentFile.fromTreeUri(c, Uri.parse(dest))!! }
    private val aliases = HashMap<String, String>()
    private val reqQueue by lazy { Volley.newRequestQueue(c) }
    private val tiffDate = SimpleDateFormat("yyyy:MM:dd kk:mm:ss", Locale.getDefault())

    override val requiresHandling = false
    override val com: ForegroundServiceCompanion get() = Companion

    companion object : ForegroundServiceCompanion() {
        override val klass = Queuer::class.java
        override val channel = Notify.Channel.QUEUER
        override val ntfId = Notify.ID_QUEUER
        override val ntfTitle = R.string.queuerTitle
        override val ntfActions: Array<Pair<String, Int>> = arrayOf(
            ACTION_STOP to R.string.stop
        )

        const val HANDLE_LINK = 0
        const val EXTRA_LINK = "link"
    }

    override fun resolveIntent(intent: Intent) {
        intent.getStringExtra(EXTRA_LINK)?.let {
            handlingLinks.add(Link(it))
            handleLinks()
        }
    }

    override fun onCreate() {
        super.onCreate()
        dest = sPreference(Settings.spStorage)
        CoroutineScope(Dispatchers.IO).launch {
            Settings.loadAliases(c, gsp).forEach { (k, v) -> aliases[k] = v }
            sp?.let { sp -> Settings.loadAliases(c, sp).forEach { (k, v) -> aliases[k] = v } }
        }
        if (m.acc == null || dest == null) {
            finish(false); return; }
        initialNotification(Companion, Downloads::class)
        handler = object : Handler(Looper.getMainLooper()) {
            override fun handleMessage(msg: Message) {
                when (msg.what) {
                    HANDLE_LINK -> {
                        handlingLinks.add(Link(msg.obj as String))
                        handleLinks()
                    }
                    Api.HANDLE_ERROR -> handlingLinks.getOrNull(0)?.apply {
                        qud!!.status = 1.toByte()
                        CoroutineScope(Dispatchers.IO).launch { dao.updateQueued(qud!!) }
                        incrementCounter(Settings.spDlErrorCount)
                        Downloads.handler?.obtainMessage(ServiceOwnerActivity.HANDLE_CHANGED, qud)
                            ?.sendToTarget()
                        linkHandled()
                    }
                }
            }
        }
        if (download?.active != true) download = Download().also { it.start() }
    }

    @Suppress("LABEL_NAME_CLASH")
    private fun handleLinks() {
        if (handlingLink) return
        val cur = handlingLinks.getOrNull(0)
        if (cur == null) {
            if (download?.active != true) finish(false)
            return; }
        handlingLink = true

        if (cur.qud == null) Thread {
            cur.qud = Queued(Persistent.now(), cur.link)
            cur.qud!!.id = dao.addQueued(cur.qud!!)
            Downloads.handler?.obtainMessage(ServiceOwnerActivity.HANDLE_INSERTED, cur.qud!!)
                ?.sendToTarget()
        }.start()

        reqQueue.adder = object : StringRequest(cur.link, { html ->
            PageConfig.findConfigWrapper(
                html, false, { Api.gotError(handler, null, null) }) { cnfWrapper ->
                @Suppress("UNCHECKED_CAST")
                val root =
                    (cnfWrapper.require.find { it.getOrNull(0) == "CometPlatformRootClient" }
                        ?.getOrNull(3) as List<Any>?)?.getOrNull(3)?.let {
                        //throw Exception(Gson().toJson(it))
                        Gson().fromJson(Gson().toJson(it), PageConfig.PolarisRoot::class.java)
                    }
                if (root == null) {
                    Api.gotError(handler, null, null); return@findConfigWrapper; }

                when (root.rootView.resource.__dr) {
                    "PolarisPostRoot.react" -> reqQueue.adder = Api<Media.MediaWrapperApi>(
                        this, Api.Endpoint.MEDIA_ITEM.url.format(root.rootView.props.media_id),
                        Media.MediaWrapperApi::class, handler, autoQueue = false
                    ) { wrapper ->
                        val med = wrapper.items?.getOrNull(0)
                        if (med == null) {
                            handler?.obtainMessage(Api.HANDLE_ERROR)?.sendToTarget(); return@Api; }
                        var found = true
                        val addOns = arrayListOf<Queued>()
                        when {
                            med.carousel_media != null -> for (car in med.carousel_media!!)
                                if (cur.qud!!.url == null) cur.qud!!.apply {
                                    date = med.taken_at.xFromSeconds()
                                    userId = med.user.pk
                                    userName = med.user.username
                                    itemId = car.pk
                                    url = car.nearest(Versioned.BEST)
                                    thumb = med.thumb()
                                    mediaType = car.media_type.toInt().toByte()
                                } else addOns.add(
                                    Queued(
                                        cur.qud!!.addedAt, cur.qud!!.link, cur.qud!!.date,
                                        med.user.pk, med.user.username,
                                        car.pk, car.nearest(Versioned.BEST),
                                        car.thumb(), car.media_type.toInt().toByte()
                                    )
                                )
                            med.image_versions2 != null -> cur.qud!!.apply {
                                date = med.taken_at.xFromSeconds()
                                userId = med.user.pk
                                userName = med.user.username
                                itemId = med.pk
                                url = med.nearest(Versioned.BEST)
                                thumb = med.thumb()
                                mediaType = med.media_type.toInt().toByte()
                            }
                            else -> found = false
                        }
                        if (found) handleQueued(cur.qud!!, addOns)
                        else {
                            linkHandled()
                            if (download?.active != true) finish(false)
                        }
                    }
                    "PolarisStoriesMediaRoot.react" -> reqQueue.adder =
                        Api<Rest.Reels<Rest.StoryReel>>(
                            this, Api.Endpoint.REEL_ITEM.url.format(root.rootView.props.user.id),
                            Rest.Reels::class, handler, autoQueue = false, cache = true,
                            typeToken = object : TypeToken<Rest.Reels<Rest.StoryReel>>() {}.type
                        ) { reels ->
                            val rel = reels.reels.getOrDefault(root.rootView.props.user.id, null)
                            val med = rel?.items?.find { it.pk == root.params.initial_media_id }
                            if (med == null) {
                                handler?.obtainMessage(Api.HANDLE_ERROR)
                                    ?.sendToTarget(); return@Api; }
                            cur.qud!!.apply {
                                date = med.taken_at.xFromSeconds()
                                userId = rel.user.pk
                                userName = rel.user.username
                                itemId = med.pk
                                url = med.nearest(Versioned.BEST)
                                thumb = med.thumb()
                                mediaType = med.media_type.toInt().toByte()
                            }
                            handleQueued(cur.qud!!, null)
                        }
                    "PolarisStoriesHighlightsRoot.react" -> reqQueue.adder =
                        Api<Rest.Reels<Rest.HighlightReel>>(
                            this, Api.Endpoint.REEL_ITEM.url.format(
                                "highlight%3A${root.params.highlight_reel_id}"
                            ), Rest.Reels::class, handler, autoQueue = false, cache = true,
                            typeToken = object : TypeToken<Rest.Reels<Rest.HighlightReel>>() {}.type
                        ) { reels ->
                            val rel = reels.reels.getOrDefault(
                                "highlight:${root.params.highlight_reel_id}", null
                            )
                            val med = rel?.items?.find {
                                it.id == cur.link.substringAfter("story_media_id=")
                                    .substringBefore("&")
                            }
                            if (med == null) {
                                handler?.obtainMessage(Api.HANDLE_ERROR)
                                    ?.sendToTarget(); return@Api; }
                            cur.qud!!.apply {
                                date = med.taken_at.xFromSeconds()
                                userId = rel.user.pk
                                userName = rel.user.username
                                itemId = med.pk
                                url = med.nearest(Versioned.BEST)
                                thumb = med.thumb()
                                mediaType = med.media_type.toInt().toByte()
                            }
                            handleQueued(cur.qud!!, null)
                        }
                    else -> {
                        Api.gotError(handler, null, null)
                        if (BuildConfig.DEBUG) throw Exception(root.rootView.resource.__dr)
                    }
                }
            }
        }, { Api.gotError(handler, null, it) }) {
            override fun getHeaders(): Map<String, String> = Api.Headers(m.acc!!, false)
        }
    }

    private fun handleQueued(qud: Queued, addOns: ArrayList<Queued>?) {
        CoroutineScope(Dispatchers.IO).launch {
            dao.updateQueued(qud)
            Downloads.handler?.obtainMessage(ServiceOwnerActivity.HANDLE_CHANGED, qud)
                ?.sendToTarget()
            addOns?.forEach { qud ->
                qud.id = dao.addQueued(qud)
                Downloads.handler?.obtainMessage(ServiceOwnerActivity.HANDLE_INSERTED, qud)
                    ?.sendToTarget()
            }
        }.invokeOnCompletion { linkHandled() }
    }

    private fun linkHandled() {
        handlingLinks.removeAt(0)
        handlingLink = false
        if (!active.value!!) return
        if (handlingLinks.isNotEmpty()) handleLinks()
        if (download?.active != true) download = Download().also { it.start() }
    }

    inner class Download : BaseThread() {
        override fun run() {
            val queue = ArrayList(dao.readyQueueds().sortedBy { it.addedAt })
            if (queue.isEmpty()) {
                if (!handlingLink) this@Queuer.finish(false)
                else interrupt()
                return; }

            super.run()
            var q = 0
            while (queue[q].url == null) {
                if (handlingLinks.all { it.link != queue[q].link }) {
                    handlingLinks.add(Link(queue[q].link, queue[q]))
                    handleLinks()
                }
                q++
                if (q >= queue.size) {
                    interrupt(); return; }
            }

            ntfSmallText = queue[q].userName
            updateNotification()
            reqQueue.add(
                object : Request<ByteArray>(Method.GET, queue[q].url, Response.ErrorListener {
                    queue[q].status = 1.toByte()
                    Downloads.handler?.obtainMessage(ServiceOwnerActivity.HANDLE_CHANGED, queue[q])
                        ?.sendToTarget()
                    CoroutineScope(Dispatchers.IO).launch {
                        dao.updateQueued(queue[q])
                    }.invokeOnCompletion { downloaded() }
                    incrementCounter(Settings.spDlErrorCount)
                }) {
                    override fun getHeaders(): Map<String, String> = Api.Headers(m.acc!!)

                    override fun parseNetworkResponse(response: NetworkResponse): Response<ByteArray> =
                        Response.success(
                            response.data, HttpHeaderParser.parseCacheHeaders(response)
                        )

                    override fun deliverResponse(response: ByteArray) {
                        Downloads.handler?.obtainMessage(
                            ServiceOwnerActivity.HANDLE_DELETED, queue[q]
                        )?.sendToTarget()
                        CoroutineScope(Dispatchers.IO).launch {
                            runCatching {
                                save(queue[q], response)
                                dao.deleteQueued(queue[q])
                            }.onSuccess {
                                downloaded()
                            }.onFailure {
                                if (BuildConfig.DEBUG) throw it else downloaded()
                            }
                        }
                    }
                }.apply {
                    setShouldCache(false)
                    tag = queue[q].itemId
                    retryPolicy = DefaultRetryPolicy(
                        20000, 2, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
                    )
                }
            )
        }
    }

    private fun downloaded() {
        download?.interrupt()
        if (!active.value!!) return
        download = Download().also { it.start() }
    }

    private fun save(q: Queued, ba: ByteArray) {
        val branch: DocumentFile = when {
            q.userName in aliases && DocumentFile.fromTreeUri(c, Uri.parse(aliases[q.userName]))
                ?.exists() == true ->
                DocumentFile.fromTreeUri(c, Uri.parse(aliases[q.userName]))
            !q.isMainFile() -> stem
            bPreference(Settings.spBranching, Settings.defSpBranching) ->
                stem.findFile(q.userName!!) ?: stem.createDirectory(q.userName!!)
            else -> stem
        } ?: return
        val type = MediaType.values().find { it.inDb == q.mediaType }!!
        val fName = q.fName(type.ext)
        var leaf = branch.findFile(fName)
        // Never check existence from StorageCache because the file might be deleted anytime and
        // the user might want to re-download it!
        if (leaf != null) return
        leaf = branch.createFile(type.mime, fName) ?: return
        c.contentResolver.openFileDescriptor(leaf.uri, "w")?.use { des ->
            FileOutputStream(des.fileDescriptor).use { fos ->
                when (q.mediaType) {
                    1.toByte() -> ExifRewriter().updateExifMetadataLossless(ba,
                        BufferedOutputStream(fos),
                        ((Imaging.getMetadata(ba) as JpegImageMetadata?)?.exif?.outputSet
                            ?: TiffOutputSet()).apply {
                            orCreateRootDirectory.apply {
                                removeField(TiffTagConstants.TIFF_TAG_IMAGE_DESCRIPTION) // Title + Subject
                                add(TiffTagConstants.TIFF_TAG_IMAGE_DESCRIPTION, q.link)

                                removeField(ExifTagConstants.EXIF_TAG_SOFTWARE)
                                add(ExifTagConstants.EXIF_TAG_SOFTWARE, UiTools.APP_NAME)

                                removeField(TiffTagConstants.TIFF_TAG_ARTIST) // Authors
                                add(TiffTagConstants.TIFF_TAG_ARTIST, q.userName)

                                removeField(TiffTagConstants.TIFF_TAG_COPYRIGHT)
                                add(TiffTagConstants.TIFF_TAG_COPYRIGHT, "IG: @${q.userName}")
                            }
                            orCreateExifDirectory.apply {
                                removeField(ExifTagConstants.EXIF_TAG_DATE_TIME_ORIGINAL)
                                add( // Date taken
                                    ExifTagConstants.EXIF_TAG_DATE_TIME_ORIGINAL,
                                    tiffDate.format(q.addedAt)
                                )

                                /*removeField(ExifTagConstants.EXIF_TAG_USER_COMMENT)
                                add(ExifTagConstants.EXIF_TAG_USER_COMMENT, )*/

                                removeField(ExifTagConstants.EXIF_TAG_SITE)
                                add(ExifTagConstants.EXIF_TAG_SITE, q.link)
                            }
                        }) // location data is currently not possible with edge post location.
                    2.toByte() -> {
                        val isoFile = IsoFile(MemoryDataSourceImpl(ba))
                        val movie = isoFile.getBoxes(MovieBox::class.java)[0]
                        var freeBox = findFreeBox(movie)

                        val correctOffset = needsOffsetCorrection(isoFile)
                        val sizeBefore = movie.size
                        var offset = 0L
                        for (box in isoFile.boxes) {
                            if ("moov" == box.type) break
                            offset += box.size
                        }

                        // Create structure or just navigate to Apple List Box.
                        var userDataBox: UserDataBox?
                        if (Path.getPath<UserDataBox>(movie, "udta")
                                .also { userDataBox = it } == null
                        ) {
                            userDataBox = UserDataBox()
                            movie.addBox(userDataBox)
                        }
                        var metaBox: MetaBox?
                        if (Path.getPath<MetaBox>(userDataBox, "meta")
                                .also { metaBox = it } == null
                        ) {
                            metaBox = MetaBox()
                            val hdlr = HandlerBox()
                            hdlr.handlerType = "mdir"
                            metaBox!!.addBox(hdlr)
                            userDataBox!!.addBox(metaBox)
                        }
                        /*if (freeBox == null) {
                            freeBox = FreeBox(480 * 854)
                            metaBox!!.addBox(freeBox)
                        }*/
                        /*var ilst: AppleItemListBox?
                        if (Path.getPath<AppleItemListBox>(metaBox, "ilst")
                                .also { ilst = it } == null
                        ) {
                            ilst = AppleItemListBox()
                            metaBox!!.addBox(ilst)
                        }

                        // Got Apple List Box
                        var nam: AppleNameBox?
                        if (Path.getPath<AppleNameBox>(ilst, "©nam").also { nam = it } == null)
                            nam = AppleNameBox()
                        nam!!.dataCountry = 0
                        nam!!.dataLanguage = 0
                        nam!!.value = "InstaTools"
                        ilst!!.addBox(nam)*/

                        val copyrightBox = CopyrightBox()
                        copyrightBox.copyright = "All Rights Reserved, me, myself and I, 2015"
                        copyrightBox.language = "eng"
                        userDataBox!!.addBox(copyrightBox)

                        /*var sizeAfter = movie.size
                        var diff = sizeAfter - sizeBefore
                        // can we compensate by resizing a Free Box we have found?
                        // This is the difference of before/after

                        if (freeBox.data.limit() > diff) { // either shrink or grow!
                            freeBox.data =
                                ByteBuffer.allocate((freeBox.data.limit() - diff).toInt())
                            sizeAfter = movie.size
                            diff = sizeAfter - sizeBefore
                        }
                        if (correctOffset && diff != 0L) correctChunkOffsets(movie, diff)*/
                        //val baos = BetterByteArrayOutputStream()
                        movie.getBox(fos.channel) // Channels.newChannel(baos)
                        isoFile.close()
                        //baos.close()
                        /*val fc = if (diff != 0L) {
                            // this is not good: We have to insert bytes in the middle of the file
                            // and this costs time as it requires re-writing most of the file's data
                            splitFileAndInsert(fos, offset, sizeAfter - sizeBefore)
                        } else fos.channel // simple overwrite of something with the file
                        fc.position(offset)
                        fc.write(ByteBuffer.wrap(baos.buffer, 0, baos.size()))
                        fc.close()*/
                    }
                    else -> fos.write(ba)
                }
            }
        }
        if (q.isMainFile()) m.files?.add(fName)
        incrementCounter(Settings.spDownloadCount)
    }

    override fun finish(cancelled: Boolean) {
        // if (!cancelled) Downloads.handler?.obtainMessage(Downloads.SHOW_AD)?.sendToTarget()
        destroy()
    }

    override fun destroy() {
        download?.interrupt()
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                clearCacheIfNecessary()
                if (dest == null ||
                    !bPreference(Settings.spAutoDeleteEmptyDirs, Settings.defSpAutoDeleteEmptyDirs)
                ) return@runCatching
                val stem = DocumentFile.fromTreeUri(c, Uri.parse(dest))!!
                for (branch in stem.listFiles())
                    if (branch.isDirectory && branch.listFiles().isEmpty())
                        branch.delete()
                StorageCache.saveStorageCache(this@Queuer)
            }.onSuccess {
                super.destroy()
            }.onFailure {
                if (BuildConfig.DEBUG) throw it
                else super.destroy()
            }
        }
    }

    override fun onDestroy() {
        Downloads.handler?.obtainMessage(ServiceOwnerActivity.HANDLE_RESET)?.sendToTarget()
        super.onDestroy()
    }

    @Throws(IOException::class)
    fun splitFileAndInsert(fos: FileOutputStream, pos: Long, length: Long): FileChannel {
        val read = fos.channel
        val tmp = File.createTempFile("ChangeMetaData", "splitFileAndInsert")
        val tmpWrite = RandomAccessFile(tmp, "rw").channel
        read.position(pos)
        tmpWrite.transferFrom(read, 0, read.size() - pos)
        read.close()
        val write = fos.channel
        write.position(pos + length)
        tmpWrite.position(0)
        var transferred = 0L
        while (tmpWrite.transferTo(0, tmpWrite.size() - transferred, write)
                .let { transferred += it; transferred } != tmpWrite.size()
        ) {
        }
        tmpWrite.close()
        tmp.delete()
        return write
    }

    private fun needsOffsetCorrection(isoFile: IsoFile): Boolean {
        return if (Path.getPath<MovieBox>(isoFile, "moov[0]/mvex[0]") != null) {
            false // Fragmented files don't need a correction
        } else {
            // no correction needed if mdat is before moov as insert into moov want change the offsets of mdat
            for (box in isoFile.boxes) {
                if ("moov" == box.type) return true
                if ("mdat" == box.type) return false
            }
            throw RuntimeException("I need moov or mdat. Otherwise all this doesn't make sense")
        }
    }

    private fun findFreeBox(c: Container): FreeBox? {
        for (box in c.boxes) {
            System.err.println(box.type)
            if (box is FreeBox) return box
            if (box is Container) {
                val freeBox = findFreeBox(box as Container)
                if (freeBox != null) return freeBox
            }
        }
        return null
    }

    private fun correctChunkOffsets(movieBox: MovieBox, correction: Long) {
        var chunkOffsetBoxes: List<ChunkOffsetBox> =
            Path.getPaths(movieBox as Box, "trak/mdia[0]/minf[0]/stbl[0]/stco[0]")
        if (chunkOffsetBoxes.isEmpty()) {
            chunkOffsetBoxes =
                Path.getPaths(movieBox as Box, "trak/mdia[0]/minf[0]/stbl[0]/st64[0]")
        }
        for (chunkOffsetBox in chunkOffsetBoxes) {
            val cOffsets = chunkOffsetBox.chunkOffsets
            for (i in cOffsets.indices) {
                cOffsets[i] += correction
            }
        }
    }

    /*private class BetterByteArrayOutputStream : ByteArrayOutputStream() {
        val buffer: ByteArray get() = buf
    }*/

    enum class MediaType(val mime: String, val ext: String, val inDb: Byte) {
        PHOTO("image/jpg", "jpg", 1),
        VIDEO("video/mp4", "mp4", 2),
        AUDIO("audio/mp4", "m4a", 3),
    }

    data class Link(val link: String, var qud: Queued? = null)
}
