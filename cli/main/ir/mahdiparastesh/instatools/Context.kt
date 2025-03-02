package ir.mahdiparastesh.instatools

import ir.mahdiparastesh.instatools.job.DownloadTask
import ir.mahdiparastesh.instatools.list.Saved
import ir.mahdiparastesh.instatools.util.Profile
import java.util.HashMap

object Context {
    val downloadTask: DownloadTask by lazy { DownloadTask() }
    val listSvd: Saved by lazy { Saved() }
    val profiles: HashMap<String, Profile> = hashMapOf()
    var latestUser: String? = null
}
