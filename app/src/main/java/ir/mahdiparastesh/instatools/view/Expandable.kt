package ir.mahdiparastesh.instatools.view

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.graphics.Point
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import android.os.Handler
import android.view.View
import android.view.animation.DecelerateInterpolator
import ir.mahdiparastesh.instatools.R
import ir.mahdiparastesh.instatools.databinding.ExpandableBinding
import ir.mahdiparastesh.instatools.json.Api
import ir.mahdiparastesh.instatools.json.Media
import ir.mahdiparastesh.instatools.json.Profile
import ir.mahdiparastesh.instatools.list.ListCar
import ir.mahdiparastesh.instatools.more.BaseActivity

class Expandable(
    private val c: BaseActivity,
    private val b: ExpandableBinding,
    private val handler: Handler?,
    private val colorBg: Int = c.color(R.color.defBG),
) {
    var node: Profile.Post? = null
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
        b.buttons.setOnClickListener {
            if (node == null) return@setOnClickListener
            c.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(UiTools.POST_LINK.format(node!!.shortcode)))
                    .setPackage(UiTools.INSTA_PACKAGE)
                //.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    fun expand() {
        if (thumb == null || node == null || zoomed) return
        zoomed = true
        currentAnimator?.cancel()
        Api<Media.MediaWrapperApi>(
            c, Api.Type.POST.url.format(node!!.shortcode), Media.MediaWrapperApi::class,
            handler, cache = true
        ) { wrapper ->
            val med = wrapper.items?.getOrNull(0)
            if (med == null) {
                handler?.obtainMessage(HANDLE_EXPANDABLE_ERROR)?.sendToTarget()
                return@Api; }
            b.slider.adapter = ListCar(c, med)
            b.indicator.setViewPager2(b.slider)
            b.indicator.vis()
            b.buttons.vis()
        }
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
                }

                override fun onAnimationCancel(animation: Animator) {
                    thumb?.alpha = 1f
                    thumb = null
                    b.root.vish(false)
                    currentAnimator = null
                    zoomed = false
                }
            })
            start()
        }
    }
}
