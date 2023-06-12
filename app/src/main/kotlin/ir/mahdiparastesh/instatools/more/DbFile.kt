package ir.mahdiparastesh.instatools.more

import android.annotation.SuppressLint
import ir.mahdiparastesh.instatools.BuildConfig
import java.io.File

/** Resolves the path to databases. */
@SuppressLint("SdCardPath")
open class DbFile(name: String, which: Triple) : File(
    "/data/data/${BuildConfig.APPLICATION_ID}/databases/$name.db${which.s}"
) {
    /** Helps resolve the file names of the triple SQLite database files. */
    enum class Triple(val s: String) {
        MAIN(""), SHARED_MEMORY("-shm"), WRITE_AHEAD_LOG("-wal")
    }
}
