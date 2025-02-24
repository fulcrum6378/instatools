package ir.mahdiparastesh.instatools.data

import ir.mahdiparastesh.instatools.api.Dm
import ir.mahdiparastesh.instatools.job.ExportTask.Method

data class Exportable(
    val name: String,
    val thread: Dm.DmThread,
    val method: Method,
    val image: Int?,
    val video: Int?,
    val post: Int?,
    val reel: Int?,
    val story: Int?,
    val uploadedImage: Int?,
    val uploadedVideo: Int?,
    val voice: Boolean,
    val min: Long?,
    val max: Long?,
)
