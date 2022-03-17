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
import android.os.Handler
import android.view.View
import android.view.animation.DecelerateInterpolator
import ir.mahdiparastesh.instatools.Downloads
import ir.mahdiparastesh.instatools.R
import ir.mahdiparastesh.instatools.data.Queued
import ir.mahdiparastesh.instatools.databinding.ExpandableBinding
import ir.mahdiparastesh.instatools.json.Api
import ir.mahdiparastesh.instatools.json.Media
import ir.mahdiparastesh.instatools.json.Profile
import ir.mahdiparastesh.instatools.list.ListCar
import ir.mahdiparastesh.instatools.more.BaseActivity
import ir.mahdiparastesh.instatools.more.Persistent
import ir.mahdiparastesh.instatools.more.Versioned
import ir.mahdiparastesh.instatools.view.UiTools.Companion.vis
import ir.mahdiparastesh.instatools.view.UiTools.Companion.vish
import ir.mahdiparastesh.instatools.view.UiTools.Companion.xFromSeconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class Expandable(
    private val c: BaseActivity,
    private val b: ExpandableBinding,
    private val handler: Handler?,
    private val colorBg: Int = c.color(R.color.defBG),
    private val onZoomChanged: (zoomed: Boolean) -> Unit = {}
) {
    var node: Profile.Post? = null
    var media: Media? = null
    var thumb: View? = null
    var zoomed = false
    private var currentAnimator: Animator? = null
    private var startScale: Float? = null
    private var startBounds: RectF? = null

    companion object {
        const val HANDLE_EXPANDABLE_ERROR = 55
        const val zoomDur = 200L
    }

    init {
        b.download.setOnClickListener {
            media?.apply {
                CoroutineScope(Dispatchers.IO).launch {
                    c.dao.addQueued(
                        Queued(
                            Persistent.now(), link() ?: "",
                            if (taken_at > 0.0) taken_at.xFromSeconds() else Persistent.now(),
                            user.pk, user.username, pk ?: id, nearest(Versioned.BEST),
                            thumb(), media_type.toInt().toByte()
                        )
                    )
                    withContext(Dispatchers.Main) { Downloads.initService(c) }
                }
            }
        }
        b.downloadThis.setOnClickListener {
            if (media == null) return@setOnClickListener
            val car = media?.carousel_media?.getOrNull(b.slider.currentItem)
                ?: return@setOnClickListener
            media?.apply {
                CoroutineScope(Dispatchers.IO).launch {
                    c.dao.addQueued(
                        Queued(
                            Persistent.now(), link() ?: "",
                            if (taken_at > 0.0) taken_at.xFromSeconds() else Persistent.now(),
                            user.pk, user.username, car.pk, car.nearest(Versioned.BEST),
                            thumb(car), car.media_type.toInt().toByte()
                        )
                    )
                    withContext(Dispatchers.Main) { Downloads.initService(c) }
                }
            }
        }
        b.downloadAll.setOnClickListener {
            if (media == null) return@setOnClickListener
            val cars = media?.carousel_media ?: return@setOnClickListener
            media?.apply {
                CoroutineScope(Dispatchers.IO).launch {
                    for (car in cars) c.dao.addQueued(
                        Queued(
                            Persistent.now(), link() ?: "",
                            if (taken_at > 0.0) taken_at.xFromSeconds() else Persistent.now(),
                            user.pk, user.username, car.pk, car.nearest(Versioned.BEST),
                            thumb(car), car.media_type.toInt().toByte()
                        )
                    )
                    withContext(Dispatchers.Main) { Downloads.initService(c) }
                }
            }
        }
        b.viewInInsta.setOnClickListener {
            link()?.let {
                try {
                    c.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(it)).apply {
                            if (it.startsWith(UiTools.IG_OPENABLE) &&
                                !it.startsWith("https://www.instagram.com/stories/highlights/")
                            ) setPackage(UiTools.INSTA_PACKAGE)
                            else addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    )
                } catch (e: ActivityNotFoundException) {
                }
            }
        }
    }

    private fun loaded() {
        if (media == null) return
        b.slider.adapter = ListCar(c, media!!)
        b.indicator.setViewPager2(b.slider)
        b.buttons.vis()
        val isSlider = media?.carousel_media != null
        b.indicator.vis(isSlider)
        b.downloadAll.vis(isSlider)
        b.downloadThis.vis(isSlider)
        b.download.vis(!isSlider)
    }

    fun link() = media?.let {
        return@let when (it.product_type) {
            "feed" -> UiTools.POST_LINK.format(it.code)
            "story" ->
                if (it.mahdi_reel_type == "highlight_reel" || it.expiring_at == null)
                    UiTools.HIGHLIGHT_LINK.format((it.mahdi_reel_id ?: it.id).substringAfter(":"))
                // Instagram cannot open such an above link
                else UiTools.STORY_LINK.format(it.user.username, it.pk)
            null -> it.nearest(Versioned.BEST)
            else -> null
        }
    }

    fun expand() {
        if (thumb == null || (node == null && media == null) || zoomed) return
        zoomed = true
        onZoomChanged(zoomed)
        currentAnimator?.cancel()
        if (media == null) Api<Media.MediaWrapperApi>(
            c, Api.Type.POST_ITEM.url.format(node!!.shortcode), Media.MediaWrapperApi::class,
            handler, cache = true
        ) { wrapper ->
            media = wrapper.items?.getOrNull(0)
            if (media == null)
                handler?.obtainMessage(HANDLE_EXPANDABLE_ERROR)?.sendToTarget()
            else loaded()
        } else loaded()
        val startBoundsInt = Rect()
        val finalBoundsInt = Rect()
        val globalOffset = Point()

        b.root.vish()
        thumb?.getGlobalVisibleRect(startBoundsInt)
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

        thumb!!.alpha = 0f
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
                        b.slider, "backgroundColor", c.color(R.color.tp), colorBg
                    )
                )
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
        b.indicator.vis(false)
        b.buttons.vis(false)
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
                    thumb?.alpha = 1f
                    thumb = null
                    b.root.vish(false)
                    b.slider.adapter = null
                    currentAnimator = null
                    zoomed = false
                    onZoomChanged(zoomed)
                }

                override fun onAnimationCancel(animation: Animator) {
                    thumb?.alpha = 1f
                    thumb = null
                    b.root.vish(false)
                    currentAnimator = null
                    zoomed = false
                    onZoomChanged(zoomed)
                }
            })
            start()
        }
    }
}
