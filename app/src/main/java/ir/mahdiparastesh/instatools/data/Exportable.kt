package ir.mahdiparastesh.instatools.data

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import com.google.gson.Gson
import ir.mahdiparastesh.instatools.R
import ir.mahdiparastesh.instatools.json.Dm
import ir.mahdiparastesh.instatools.more.Persistent
import ir.mahdiparastesh.instatools.serv.Exporter

@Entity
class Exportable(
    var thread: String,
    var selection: String? = null,
    var type: Int,
    var options: String,
    var uri: String? = null,
    @Ignore @Transient var threadData: Dm.DmThread? = null,
    @Ignore @Transient var opt: Options? = null,
    @Ignore @Transient var media: HashMap<String, Exporter.Downloadable> = hashMapOf()
) {
    @PrimaryKey
    var addedAt: Long = Persistent.now()

    @Suppress("unused")
    constructor() : this("", "", 0, "")

    class Options(
        var image: Int = 1, // -1=>NO; 0=>Low; 1=>Med; 2=>High
        var video: Int = 0, // -1=>NO; 0=>Low; 1=>Med; 2=>High; 3=>Thumb
        var slide: Int = 10, // maximum slides
        var voice: Int = 0, // -1=>NO; 0=>YES
    ) {
        fun img() = image > -1
        fun vid() = video > -1
        fun voi() = voice > -1

        fun toJson(): String = Gson().toJson(this)

        companion object {
            fun parse(json: String?): Options? =
                json?.let { Gson().fromJson(json, Options::class.java) }

            val quaImage = arrayOf(R.id.quaImage0, R.id.quaImage1, R.id.quaImage2)
            val quaVideo = arrayOf(R.id.quaVideo0, R.id.quaVideo1, R.id.quaVideo2, R.id.quaVideo3)
        }
    }
}
