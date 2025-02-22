package ir.mahdiparastesh.instatools.job

import ir.mahdiparastesh.instatools.Context.api
import ir.mahdiparastesh.instatools.api.Api
import ir.mahdiparastesh.instatools.api.Message
import ir.mahdiparastesh.instatools.api.Rest
import ir.mahdiparastesh.instatools.exp.HtmlExporter
import ir.mahdiparastesh.instatools.util.Queuer
import java.io.File

/** Exports direct messages. */
class Exporter : Queuer<Exporter.Exportable>() {
    override val outputDir = File("./Messages/")

    companion object {
        const val USER_PROFILE_IMG = "user_%s"
    }

    override fun handle(q: Exportable) {
        // fetch all messages
        while (q.thread.has_older) api.call<Rest.InboxThread>(
            Api.Endpoint.DIRECT.url.format(
                q.thread.thread_id, q.thread.items.first().item_id, "20"
            ),
            Rest.InboxThread::class,
        ).thread.also { newThread ->
            q.thread.has_older = newThread.has_older
            (q.thread.items as ArrayList).apply {
                // TODO remove duplicates?
                addAll(newThread.items)
                sortBy { it.timestamp }
            }
        }

        // prepare the path
        val branch = File(outputDir, q.name)
        branch.mkdir()

        // write messages
        when (q.method) {
            Method.HTML -> HtmlExporter(q)
            Method.TEXT -> {}//TextExporter(q)
            Method.JSON -> {}//JsonExporter(q)
        }

        // download the media
        /*media = hashMapOf()
        if (opt?.img() == false && opt?.vid() == false && opt?.voi() == false) {
            fetchMedium(); return; }

        q.thread.thread_id.also {
            cacheDir = File(Cache(c), it)
            if (!cacheDir!!.exists()) cacheDir!!.mkdir()
        }
        val img = opt?.img() == true
        val vid = opt?.vid() == true
        val actVid = opt?.actVid() == true
        if (img) for (user in q.thread.users) {
            val key = USER_PROFILE_IMG.format(user.pk)
            media[key] = Downloadable(user.profile_pic_url, 0, cacheDir!!, key, 0)
        }
        for (dm in q.thread.items) {
            if (actVid && dm.animated_media != null) continue
            // HTML gets GIFs dynamically, PDF cannot show them properly, and TXT...
            if (dm.voice_media != null) {
                if (opt?.voice == 0 && dm.voice_media.media != null)
                    media[dm.item_id] = Downloadable(
                        dm.voice_media.media.audio.audio_src, 2, cacheDir!!, dm.item_id, -2
                    )
                continue; }
            if (opt?.img() == true || opt?.vid() == true) (when {
                vid && dm.clip != null -> dm.clip.clip
                dm.direct_media_share != null -> when (dm.direct_media_share.media.media_type) {
                    1f, 2f -> if (img || vid) dm.direct_media_share.media else null
                    8f -> dm.direct_media_share.media.carousel_media
                        ?.let { if (img || vid) it[0] else null }

                    else -> null
                }
                vid && dm.felix_share != null -> dm.felix_share.video
                dm.media != null -> if (img || vid) dm.media else null
                dm.media_share != null -> when (dm.media_share.media_type) {
                    1f, 2f -> if (img || vid) dm.media_share else null
                    8f -> dm.media_share.carousel_media?.let { if (img || vid) it[0] else null }
                    else -> null
                }
                img && dm.raven_media != null -> dm.raven_media
                dm.reel_share != null -> if (img || vid) dm.reel_share.media else null
                dm.story_share != null -> if (img || vid) dm.story_share.media else null
                else -> null
            })?.apply {
                if (carousel_media == null && image_versions2 == null) return@apply
                val theVer = carousel_media?.getOrNull(0) ?: this
                val quality = when {
                    theVer.video_versions != null && opt!!.video == 3 ->
                        if (img) -opt!!.image.toFloat() else Media.Version.MEDIUM
                    theVer.video_versions != null -> -opt!!.video.toFloat()
                    else -> -opt!!.image.toFloat()
                }
                val url = theVer.nearest(quality, justImage = opt?.actVid() != true) ?: return@apply
                media[dm.item_id] = Downloadable(
                    url, if (opt?.actVid() == true && video_versions != null) 1 else 0,
                    cacheDir!!, dm.item_id, quality.toInt()
                )
            }
        }*/
    }

    data class Exportable(
        val name: String,
        val thread: Message.DmThread,
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

    enum class Method(val ext: String) {
        HTML("html"),
        TEXT("txt"),
        JSON("json"),
    }
}
