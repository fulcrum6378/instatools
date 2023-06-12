package ir.mahdiparastesh.instatools.data

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import com.google.gson.Gson
import ir.mahdiparastesh.instatools.R
import ir.mahdiparastesh.instatools.json.Dm
import ir.mahdiparastesh.instatools.more.Persistent
import ir.mahdiparastesh.instatools.serv.Exporter
import java.io.File

@Entity
class Exportable(
    var thread: String,
    var selection: String? = null,
    var type: Int,
    var options: String,
    var uri: String? = null,
    @Ignore @Transient var threadData: Dm.DmThread? = null,
    @Ignore @Transient var opt: Options? = null,
    @Ignore @Transient var media: HashMap<String, Exporter.Downloadable> = hashMapOf(),
    @Ignore @Transient var cacheDir: File? = null,
) {
    @PrimaryKey
    var addedAt: Long = Persistent.now()

    constructor() : this("", "", 0, "")

    fun method(): Exporter.Method = when (type) {
        0 -> Exporter.Method.HTML
        1 -> Exporter.Method.PDF
        2 -> Exporter.Method.TXT
        else -> throw IllegalArgumentException()
    }

    class Options(
        var image: Int = DEF_IMAGE, // -1=>NO; 0=>Low; 1=>Med; 2=>High
        var video: Int = DEF_VIDEO, // -1=>NO; 0=>Low; 1=>Med; 2=>High; 3=>Thumb
        @Suppress("unused") var slide: Int = DEF_SLIDE, // maximum slides
        var voice: Int = DEF_VOICE, // -1=>NO; 0=>YES
    ) {
        fun img() = image > -1
        fun vid() = video > -1
        fun voi() = voice > -1
        fun actVid() = video in 0..2

        fun toJson(): String = Gson().toJson(this)

        companion object {
            fun parse(json: String?): Options? =
                json?.let { Gson().fromJson(json, Options::class.java) }

            const val DEF_IMAGE = 1
            const val DEF_VIDEO = 0
            const val DEF_SLIDE = 3
            const val DEF_VOICE = 0
            val quaImage = arrayOf(R.id.quaImage0, R.id.quaImage1, R.id.quaImage2)
            val quaVideo = arrayOf(R.id.quaVideo0, R.id.quaVideo1, R.id.quaVideo2, R.id.quaVideo3)
        }
    }
}
