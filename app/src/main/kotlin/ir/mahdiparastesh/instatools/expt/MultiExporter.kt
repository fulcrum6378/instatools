package ir.mahdiparastesh.instatools.expt

import android.annotation.SuppressLint
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import ir.mahdiparastesh.instatools.Settings
import ir.mahdiparastesh.instatools.data.Exportable
import ir.mahdiparastesh.instatools.json.Api
import ir.mahdiparastesh.instatools.serv.Exporter
import java.io.FileInputStream
import java.io.FileOutputStream

abstract class MultiExporter(c: Exporter, exp: Exportable) : BaseExporter(c, exp) {
    protected lateinit var folder: DocumentFile
    private val dwnFolder by lazy {
        DocumentFile.fromTreeUri(c.c, Uri.parse(c.sPreference(Settings.spStorage)))!!
    }
    protected val tmpDir by lazy {
        dwnFolder.findFile(TEMP_DIR) ?: dwnFolder.createDirectory(TEMP_DIR)!!
    }
    protected val canCreateDirSelf = Exporter.canCreateDirSelf(c)
    private val subFolders = Array<DocumentFile?>(3) { null }
    protected val subFolderNames = arrayOf("image", "video", "audio")
    protected var rescueFolder: DocumentFile? = null

    companion object {
        const val TEMP_DIR = ".export_temp"
    }

    @SuppressLint("NewApi")
    override fun run() {
        val myUri = Uri.parse(Api.encode(exp.uri))
        (if (canCreateDirSelf) DocumentFile.fromSingleUri(c.c, myUri)
        else DocumentFile.fromTreeUri(c.c, myUri)?.createDirectory(exp.threadData!!.exported()))
            .apply {
                if (this == null) {
                    progress(0f, false)
                    return@run
                } else folder = this
            }
        if (canCreateDirSelf) tmpDir.listFiles().forEach { it.delete() }

        // You cannot manage files in internal storage using DocumentsContract!
        for (dwn in exp.media.entries) {
            val t = dwn.value.type.toInt()
            if (t == 3) continue
            if (subFolders[t] == null) subFolders[t] =
                (if (canCreateDirSelf) tmpDir else folder).createDirectory(subFolderNames[t])!!
            val ft = Exporter.fileTypes[t]
            if (!dwn.value.cache.exists()) continue
            subFolders[t]!!.createFile(ft.first, dwn.value.fileName(dwn.key))!!.apply {
                c.c.contentResolver.openFileDescriptor(uri, "w")?.use { des ->
                    FileOutputStream(des.fileDescriptor).use { fos ->
                        FileInputStream(dwn.value.cache).use { fis ->
                            fos.write(fis.readBytes())
                        }
                    }
                }
            }
        }
        exp.media.clear()
        if (canCreateDirSelf) {
            tmpDir.createFile("application/octet-stream", ".test")?.also {
                try {
                    DocumentsContract.moveDocument(
                        c.c.contentResolver, it.uri, tmpDir.uri, folder.uri
                    ) // returned a malfunctioned Uri
                    // Uri authorities must be the same in order for this code to work;
                    // otherwise it'll throw SecurityException.
                    // It's too difficult or perhaps impossible to delete that test file!
                } catch (e: Exception) {
                    // IllegalStateException when moving from Internal Storage to SD Card!!
                    val folderName = folder.name ?: exp.threadData!!.exported()
                    rescueFolder = DocumentFile.fromTreeUri(
                        c.c, Uri.parse(c.sPreference(Settings.spStorage))
                    )?.createDirectory(folderName)
                }
            }
            subFolders.forEach {
                if (it != null) DocumentsContract.moveDocument(
                    c.c.contentResolver, it.uri, tmpDir.uri, rescueFolder?.uri ?: folder.uri
                )
            }
        }
    }
}
