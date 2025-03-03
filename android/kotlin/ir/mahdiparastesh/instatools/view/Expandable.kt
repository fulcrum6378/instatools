package ir.mahdiparastesh.instatools.view

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Point
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.annotation.ColorInt
import androidx.lifecycle.MutableLiveData
import ir.mahdiparastesh.instatools.Downloads
import ir.mahdiparastesh.instatools.R
import ir.mahdiparastesh.instatools.Viewer
import ir.mahdiparastesh.instatools.api.Media
import ir.mahdiparastesh.instatools.data.Download
import ir.mahdiparastesh.instatools.databinding.ExpandableBinding
import ir.mahdiparastesh.instatools.list.ListCar
import ir.mahdiparastesh.instatools.util.BaseActivity
import ir.mahdiparastesh.instatools.util.Utils
import ir.mahdiparastesh.instatools.view.UiTools.vis
import ir.mahdiparastesh.instatools.view.UiTools.vish
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.apache.commons.text.StringEscapeUtils

/** A ViewGroup that pops up and shows an IG post or reel. */
class Expandable(
    private val c: BaseActivity,
    val b: ExpandableBinding,
    @ColorInt private val colorBg: Int = c.color(R.color.defBG),
    private val onZoomChanged: (zoomed: Boolean) -> Unit = {}
) {
    var zoomed = false
    private var media: Media? = null
    private var mediaOwner: String? = null
    private var thumb: View? = null
    private var currentAnimator: Animator? = null
    private var startScale: Float? = null
    private var startBounds: RectF? = null
    private val muteSound = MutableLiveData(false)
    private val zoomDur = 200L

    init {
        b.volume.setOnClickListener { muteSound.value = muteSound.value != true }
        muteSound.observe(c) { bb ->
            b.volume.setImageResource(if (bb) R.drawable.volume_off else R.drawable.volume_up)
            (b.slider.adapter as ListCar?)?.sessions
                ?.forEach { it?.player?.volume = if (bb) 0f else 1f }
        }
        b.download.setOnClickListener {
            media?.also { med ->
                CoroutineScope(Dispatchers.IO).launch {
                    c.c.downloads.addAll<Download>(med.queue(owner = mediaOwner), true)
                    Downloads.initService(c)
                }
            }
        }
        b.downloadThis.setOnClickListener {
            media?.also { med ->
                CoroutineScope(Dispatchers.IO).launch {
                    c.c.downloads.addAll<Download>(
                        med.queue(onlyOneSlide = b.slider.currentItem), true
                    )
                    Downloads.initService(c)
                }
            }

        }
        b.downloadAll.setOnClickListener {
            media?.also { med ->
                CoroutineScope(Dispatchers.IO).launch {
                    c.c.downloads.addAll<Download>(med.queue(), true)
                    Downloads.initService(c)
                }
            }
        }
        b.downloadAudio.setOnClickListener {
            val car = media?.carousel_media?.getOrNull(b.slider.currentItem)
            val audioUrl = (car ?: media)?.audioUrl()?.let { StringEscapeUtils.unescapeXml(it) }
                ?: return@setOnClickListener
            media?.apply {
                CoroutineScope(Dispatchers.IO).launch {
                    @Suppress("DEPRECATION")
                    c.c.downloads.add<Download>(
                        Download(
                            id(),
                            Utils.compileSecondsTS(taken_at!!),
                            audioUrl,
                            0x3,
                            mediaOwner ?: owner().username!!,
                            caption?.text,
                            link() ?: audioUrl,
                            thumb(),
                            video_duration,
                            latitude(),
                            longitude(),
                        ), true
                    )
                    Downloads.initService(c)
                }
            }
        }
        b.viewInInsta.setOnClickListener {
            media?.link()?.also {
                try {
                    c.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(it)).apply {
                            if (it.startsWith(UiTools.IG_OPENABLE) &&
                                !it.startsWith("https://www.instagram.com/stories/highlights/")
                            ) setPackage(UiTools.INSTA_PACKAGE)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    )
                } catch (_: ActivityNotFoundException) {
                }
            }
        }
    }

    fun expand(
        media: Media?,
        thumb: View?,
        /** used only for stories and highlights */
        mediaOwner: String? = null,
        /** used only for stories and highlights */
        mediaOwnerId: String? = null
    ) {
        if (thumb == null || media == null || zoomed) return
        zoomed = true
        this.media = media
        this.mediaOwner = mediaOwner
        onZoomChanged(true)
        currentAnimator?.cancel()
        b.username.text = ""

        b.slider.adapter = ListCar(c, media, muteSound)
        b.indicator.attachTo(b.slider)
        b.buttons.vis()
        b.username.vis()
        val isSlider = media.carousel_media != null
        b.indicator.vis(isSlider)
        b.downloadAll.vis(isSlider)
        b.downloadThis.vis(isSlider)
        b.download.vis(!isSlider)
        val hasAudio = media.hasAudio() == true
        b.downloadAudio.vis(hasAudio)
        b.volume.vis(hasAudio)
        val u = media.owner()
        b.username.text = "@${u.username ?: mediaOwner}"
        b.username.setOnClickListener {
            if (!UiTools.openProfile(c, u.username ?: mediaOwner!!) && c !is Viewer)
                Viewer.comeHere(c, mediaOwnerId ?: u.id())
        }

        val startBoundsInt = Rect()
        val finalBoundsInt = Rect()
        val globalOffset = Point()

        b.root.vish()
        thumb.getGlobalVisibleRect(startBoundsInt)
        b.root.getGlobalVisibleRect(finalBoundsInt, globalOffset)
        startBoundsInt.offset(-globalOffset.x, -globalOffset.y)
        finalBoundsInt.offset(-globalOffset.x, -globalOffset.y)

        startBounds = RectF(startBoundsInt)
        val finalBounds = RectF(finalBoundsInt)

        if ((finalBounds.width() / finalBounds.height() > startBounds!!.width() / startBounds!!.height())) {
            startScale = startBounds!!.height() / finalBounds.height()
            val startWidth: Float = startScale!! * finalBounds.width()
            val deltaWidth: Float = (startWidth - startBounds!!.width()) / 2
            startBounds!!.left -= deltaWidth.toInt()
            startBounds!!.right += deltaWidth.toInt()
        } else {
            startScale = startBounds!!.width() / finalBounds.width()
            val startHeight: Float = startScale!! * finalBounds.height()
            val deltaHeight: Float = (startHeight - startBounds!!.height()) / 2f
            startBounds!!.top -= deltaHeight.toInt()
            startBounds!!.bottom += deltaHeight.toInt()
        }

        thumb.alpha = 0f
        b.slider.pivotX = 0f
        b.slider.pivotY = 0f

        currentAnimator = AnimatorSet().apply {
            play(
                ObjectAnimator.ofFloat(b.slider, View.X, startBounds!!.left, finalBounds.left)
            ).apply {
                with(
                    ObjectAnimator.ofFloat(b.slider, View.Y, startBounds!!.top, finalBounds.top)
                )
                with(ObjectAnimator.ofFloat(b.slider, View.SCALE_X, startScale!!, 1f))
                with(ObjectAnimator.ofFloat(b.slider, View.SCALE_Y, startScale!!, 1f))
                with(
                    ObjectAnimator.ofArgb(
                        b.slider, "backgroundColor", c.color(android.R.color.transparent), colorBg
                    )
                ) // IT WORKS BITCH
            }
            duration = zoomDur
            interpolator = DecelerateInterpolator()
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    currentAnimator = null
                }

                override fun onAnimationCancel(animation: Animator) {
                    currentAnimator = null
                }
            })
            start()
        }
    }

    fun collapse() {
        if (startBounds == null || startScale == null || !zoomed) return
        media = null
        mediaOwner = null
        b.indicator.vis(false)
        b.buttons.vis(false)
        b.username.vis(false)
        currentAnimator?.cancel()
        currentAnimator = AnimatorSet().apply {
            play(ObjectAnimator.ofFloat(b.slider, View.X, startBounds!!.left)).apply {
                with(ObjectAnimator.ofFloat(b.slider, View.Y, startBounds!!.top))
                with(ObjectAnimator.ofFloat(b.slider, View.SCALE_X, startScale!!))
                with(ObjectAnimator.ofFloat(b.slider, View.SCALE_Y, startScale!!))
            }
            duration = zoomDur
            interpolator = DecelerateInterpolator()
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    zoomedOut()
                }

                override fun onAnimationCancel(animation: Animator) {
                    zoomedOut()
                }
            })
            start()
        }
    }

    private fun zoomedOut() {
        thumb?.alpha = 1f
        thumb = null
        b.root.vish(false)
        b.slider.adapter = null
        currentAnimator = null
        zoomed = false
        onZoomChanged(false)
    }
}
