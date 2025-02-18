package ir.mahdiparastesh.instatools.more

import ir.mahdiparastesh.instatools.Login
import java.util.Calendar

object Utils {
    val ACC_FROM_URL = arrayOf(Login.RAW_HOST, Login.HOST)

    /** Are any Activities or Services alive? */
    fun anyoneAlive() = BaseActivity.anyActive() || ForegroundService.anyRunning()

    /** Converts a timestamp to a human-readable date. */
    fun date(time: Long): String {
        val cal = time.calendar()
        return "${cal[Calendar.YEAR]}.${z(cal[Calendar.MONTH] + 1)}." +
            "${z(cal[Calendar.DAY_OF_MONTH])} - ${z(cal[Calendar.HOUR_OF_DAY])}:" +
            "${z(cal[Calendar.MINUTE])}:${z(cal[Calendar.SECOND])}"
    }

    /** Helper class for turning 1 to "01". */
    fun z(n: Int): String {
        val s = n.toString()
        return if (s.length == 1) "0$s" else s
    }

    /** Converts a timestamp to a Calendar instance. */
    fun Long.calendar(): Calendar = // needs milliseconds
        Calendar.getInstance().apply { timeInMillis = this@calendar }

    /** Converts a microseconds timestamp to a milliseconds one. */
    fun Double.xFromMicroseconds() = toLong() / 1000L

    /** Converts a seconds timestamp to a milliseconds one. */
    fun Double.xFromSeconds() = toLong() * 1000L

    /** Gets the IG user name from a link. */
    fun String.accFromUrl(host: String): String? =
        if (startsWith(host)) substringAfter(host).substringBefore("/")
            .substringBefore("?") else null

    /** @return a datetime text to be used in a file name. */
    fun fileDateTime(time: Long): String {
        val cal = Calendar.getInstance().apply { timeInMillis = time }
        return "${cal[Calendar.YEAR]}${z(cal[Calendar.MONTH] + 1)}" +
            "${z(cal[Calendar.DAY_OF_MONTH])}_${z(cal[Calendar.HOUR_OF_DAY])}" +
            "${z(cal[Calendar.MINUTE])}${z(cal[Calendar.SECOND])}"
    }

    fun <T> Map<String, T>.getOrNull(key: String): T? = if (containsKey(key)) this[key] else null
}
