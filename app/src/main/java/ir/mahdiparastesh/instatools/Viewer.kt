package ir.mahdiparastesh.instatools

import android.os.Bundle
import android.os.Handler
import android.widget.Toast
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import ir.mahdiparastesh.instatools.databinding.ViewerBinding
import ir.mahdiparastesh.instatools.json.Api
import ir.mahdiparastesh.instatools.json.Profile
import ir.mahdiparastesh.instatools.more.BaseActivity
import ir.mahdiparastesh.instatools.more.GlideShimmer

class Viewer : BaseActivity() {
    private lateinit var b: ViewerBinding
    private var user: String? = null

    companion object {
        const val EXTRA_USER = "EXTRA_USER"
        var handler: Handler? = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        intent.extras?.getString(EXTRA_USER)?.let { user = it }
        if (user == null) {
            onBackPressed()
            return
        }
        b = ViewerBinding.inflate(layoutInflater)
        setContentView(b.root)
        toolbar(b.toolbar, R.string.vwTitle)

        Api<Profile>(
            this, Api.Type.PROFILE.url.format(user), Profile::class, handleError = handler
        ) { profile ->
            val user = profile.graphql?.user
            if (user == null) {
                Toast.makeText(c, "This page doesn\'t exist!", Toast.LENGTH_SHORT).show()
                return@Api
            }
            tbTitle?.text = user.username
            Glide.with(c)
                .load(user.profile_pic_url_hd ?: user.profile_pic_url)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .addListener(GlideShimmer(b.proPhotoShimmer, b.proPhoto))
                .into(b.proPhoto)
        }
    }

    override fun onDestroy() {
        handler = null
        super.onDestroy()
    }
}
