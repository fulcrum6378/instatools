package ir.mahdiparastesh.instatools.data

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import com.google.gson.Gson
import ir.mahdiparastesh.instatools.json.Dm
import ir.mahdiparastesh.instatools.more.Persistent

@Entity
class Exportable(
    var thread: String,
    var selection: String? = null,
    var type: Byte,
    var options: String,
    var uri: String? = null,
    @Ignore @Transient var threadData: Dm.DmThread? = null
) {
    @PrimaryKey
    var addedAt: Long = Persistent.now()

    @Suppress("unused")
    constructor() : this("", "", 0, "")

    class Options(

    ) {

        fun parse(json: String): Options = Gson().fromJson(json, Options::class.java)

        fun toJson(): String = Gson().toJson(this)
    }
}
