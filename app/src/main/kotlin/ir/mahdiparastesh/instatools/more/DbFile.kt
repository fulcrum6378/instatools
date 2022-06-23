package ir.mahdiparastesh.instatools.more

import android.annotation.SuppressLint
import ir.mahdiparastesh.instatools.Main
import java.io.File

@SuppressLint("SdCardPath")
open class DbFile(name: String, which: Triple) : File(
    "/data/data/${Main::class.java.`package`!!.name}/databases/$name.db${which.s}"
) {
    enum class Triple(val s: String) {
        MAIN(""), SHARED_MEMORY("-shm"), WRITE_AHEAD_LOG("-wal")
    }
}
