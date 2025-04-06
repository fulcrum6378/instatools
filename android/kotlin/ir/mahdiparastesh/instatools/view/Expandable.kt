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
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import androidx.annotation.ColorInt
import androidx.core.net.toUri
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

/** A ViewGroup that pops up and shows an IG post or story */
class Expandable(
    val c: BaseActivity,
    val b: ExpandableBinding,
    @ColorInt private val colorBg: Int = c.color(R.color.defBG),
    private val onZoomChanged: (zoomed: Boolean) -> Unit = {}
) {
    var zoomed = false
    var media: Media? = null
    private var mediaOwner: String? = null
    var currentAnimator: Animator? = null
    private var startScale: Float? = null
    private var startBounds: RectF? = null
    val muteSound = MutableLiveData(false)
    private val zoomDur = 400L

    init {
        b.volume.setOnClickListener { muteSound.value = muteSound.value != true }
        muteSound.observe(c) { bb ->
            b.volume.setImageResource(if (bb) R.drawable.volume_off else R.drawable.volume_up)
            (b.slider.adapter as ListCar?)?.sessions
                ?.forEach { it?.player?.volume = if (bb) 0f else 1f }
        }
        b.download.setOnClickListener {
            val med = media ?: return@setOnClickListener
            CoroutineScope(Dispatchers.IO).launch {
                c.c.downloads.addAll<Download>(med.queue(owner = mediaOwner), true)
                Downloads.initService(c)
            }
        }
        b.downloadThis.setOnClickListener {
            val med = media ?: return@setOnClickListener
            CoroutineScope(Dispatchers.IO).launch {
                c.c.downloads.addAll<Download>(med.queue(onlyOneSlide = b.slider.currentItem), true)
                Downloads.initService(c)
            }
        }
        b.downloadAll.setOnClickListener {
            val med = media ?: return@setOnClickListener
            CoroutineScope(Dispatchers.IO).launch {
                c.c.downloads.addAll<Download>(med.queue(), true)
                Downloads.initService(c)
            }
        }
        b.downloadAudio.setOnClickListener {
            val med = media ?: return@setOnClickListener
            val car = med.carousel_media?.getOrNull(b.slider.currentItem)
            val audioUrl = (car ?: media)?.audioUrl()?.let { StringEscapeUtils.unescapeXml(it) }
                ?: return@setOnClickListener

            CoroutineScope(Dispatchers.IO).launch {
                c.c.downloads.add<Download>(
                    Download(
                        med.id(),
                        Utils.compileSecondsTS(med.taken_at!!),
                        audioUrl,
                        0x3,
                        mediaOwner ?: med.owner().username!!,
                        med.caption?.text,
                        med.link() ?: audioUrl,
                        med.thumb(),
                        med.video_duration,
                        med.latitude(),
                        med.longitude(),
                    ), true
                )
                Downloads.initService(c)
            }
        }
        b.viewInInsta.setOnClickListener {
            val link = media?.link() ?: return@setOnClickListener
            try {
                c.startActivity(
                    Intent(Intent.ACTION_VIEW, link.toUri()).apply {
                        if (link.startsWith(UiTools.IG_OPENABLE) &&
                            !link.startsWith("https://www.instagram.com/stories/highlights/")
                        ) setPackage(UiTools.INSTA_PACKAGE)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                )
            } catch (_: ActivityNotFoundException) {
            }
        }
    }

    fun expand(
        media: Media,
        thumb: ImageView,
        /** used only for stories and highlights */
        mediaOwner: String? = null,
        /** used only for stories and highlights */
        mediaOwnerId: String? = null
    ) {
        if (zoomed) return
        zoomed = true

        // initial configurations
        this.media = media
        this.mediaOwner = mediaOwner
        onZoomChanged(true)
        currentAnimator?.cancel()

        // thumbnail expansion
        b.thumb.setImageDrawable(thumb.drawable)
        b.thumb.vish()
        b.root.vish()

        // main layouts
        b.slider.adapter = ListCar(this)
        b.indicator.attachTo(b.slider)
        b.panel.vis()

        // media details
        val u = media.owner()
        b.username.text = "@${u.username ?: mediaOwner}"
        b.username.setOnClickListener {
            if (c !is Viewer)
                Viewer.comeHere(c, mediaOwnerId ?: u.id())
            else
                UiTools.openProfile(c, u.username ?: mediaOwner!!)
        }

        // buttons
        val isSlider = media.carousel_media != null
        b.indicator.vis(isSlider)
        b.downloadAll.vis(isSlider)
        b.downloadThis.vis(isSlider)
        b.download.vis(!isSlider)
        val hasAudio = media.hasAudio() == true
        b.downloadAudio.vis(hasAudio)
        b.volume.vis(hasAudio)

        /* --- beginning of coordination --- */

        val startBoundsInt = Rect()
        val finalBoundsInt = Rect()
        val globalOffset = Point()

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

        /* --- end of coordination --- */

        /* --- beginning of animation --- */

        val firstWave = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(b.thumb, View.X, startBounds!!.left, finalBounds.left),
                ObjectAnimator.ofFloat(b.thumb, View.Y, startBounds!!.top, finalBounds.top),
                ObjectAnimator.ofFloat(b.thumb, View.SCALE_X, startScale!!, 1f),
                ObjectAnimator.ofFloat(b.thumb, View.SCALE_Y, startScale!!, 1f),
                ObjectAnimator.ofArgb(
                    b.root, "backgroundColor", c.color(android.R.color.transparent), colorBg
                ),
            )
        }
        val secondWave = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(b.panel, View.ALPHA, 0f, 1f),
            )
        }
        currentAnimator = AnimatorSet().apply {
            playSequentially(firstWave, secondWave)
            duration = zoomDur
            interpolator = DecelerateInterpolator()
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    currentAnimator = null

                    b.slider.vish(true)
                    if ((b.slider.adapter as ListCar?)?.loading == false)
                        b.thumb.vish(false)
                }

                override fun onAnimationCancel(animation: Animator) {
                    currentAnimator = null
                }
            })
            start()
        }

        /* --- end of animation --- */
    }

    fun collapse() {
        if (startBounds == null || startScale == null || !zoomed) return
        currentAnimator?.cancel()
        media = null
        mediaOwner = null
        b.slider.adapter = null
        b.thumb.vish()

        // fade + shrink animation
        currentAnimator = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(b.thumb, View.X, startBounds!!.left),
                ObjectAnimator.ofFloat(b.thumb, View.Y, startBounds!!.top),
                ObjectAnimator.ofFloat(b.thumb, View.SCALE_X, startScale!!),
                ObjectAnimator.ofFloat(b.thumb, View.SCALE_Y, startScale!!),
                ObjectAnimator.ofArgb(
                    b.root, "backgroundColor", colorBg, c.color(android.R.color.transparent)
                ),
                ObjectAnimator.ofFloat(b.panel, View.ALPHA, 1f, 0f),
            )
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
        b.root.vish(false)
        b.panel.vis(false)
        b.slider.vish(false)
        currentAnimator = null
        zoomed = false
        onZoomChanged(false)
    }
}
