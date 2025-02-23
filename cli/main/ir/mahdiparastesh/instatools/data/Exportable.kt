package ir.mahdiparastesh.instatools.data

import ir.mahdiparastesh.instatools.api.Dm
import ir.mahdiparastesh.instatools.job.ExportTask.Method

data class Exportable(
    val name: String,
    val thread: Dm.DmThread,
    val method: Method,
    val image: Float?,
    val video: Float?,
    val post: Float?,
    val reel: Float?,
    val story: Float?,
    val uploadedImage: Float?,
    val uploadedVideo: Float?,
    val voice: Boolean,
    val min: Long?,
    val max: Long?,
)
