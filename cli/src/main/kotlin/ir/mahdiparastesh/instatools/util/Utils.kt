package ir.mahdiparastesh.instatools.util

import java.util.*

@Suppress("MemberVisibilityCanBePrivate")
object Utils {
    const val APP_NAME = "InstaTools"
    const val MAHDI = "https://mahdiparastesh.ir/"
    const val PROFILE = "https://www.instagram.com/%s/"
    const val POST_LINK = "https://www.instagram.com/p/%s/"
    const val REEL_LINK = "https://www.instagram.com/reel/%s/"
    const val STORY_LINK = "https://www.instagram.com/stories/%1\$s/%2\$s/"

    /** Helper class for turning 1 to "01". */
    fun z(n: Int): String {
        val s = n.toString()
        return if (s.length == 1) "0$s" else s
    }

    fun now() = Calendar.getInstance().timeInMillis

    fun calendar(time: Long): Calendar =
        Calendar.getInstance().apply { timeInMillis = time }

    /** @return a datetime text to be used in a file name. */
    fun fileDateTime(time: Long): String {
        val cal = calendar(time)
        return "${cal[Calendar.YEAR]}${z(cal[Calendar.MONTH] + 1)}" +
                "${z(cal[Calendar.DAY_OF_MONTH])}_${z(cal[Calendar.HOUR_OF_DAY])}" +
                "${z(cal[Calendar.MINUTE])}${z(cal[Calendar.SECOND])}"
    }

    /** Converts a timestamp of seconds to a timestamp of millisecond. */
    fun compileSecondsTS(seconds: Double) = seconds.toLong() * 1000L

    /** Converts a timestamp of microseconds to a timestamp of millisecond. */
    fun compileMicrosecondsTS(microseconds: Double) = microseconds.toLong() / 1000L

    interface InstaToolsException
}
