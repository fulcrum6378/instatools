package ir.mahdiparastesh.instatools

import ir.mahdiparastesh.instatools.job.DownloadTask
import ir.mahdiparastesh.instatools.job.ExportTask
import ir.mahdiparastesh.instatools.list.Direct
import ir.mahdiparastesh.instatools.list.Saved
import ir.mahdiparastesh.instatools.util.Profile
import java.util.HashMap

object Context {
    val downloadTask: DownloadTask by lazy { DownloadTask() }
    val exportTask: ExportTask by lazy { ExportTask() }
    val listSvd: Saved by lazy { Saved() }
    val listMsg: Direct by lazy { Direct() }
    val profiles: HashMap<String, Profile> = hashMapOf()
    var latestUser: String? = null
}
