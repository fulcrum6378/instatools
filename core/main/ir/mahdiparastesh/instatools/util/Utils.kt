package ir.mahdiparastesh.instatools.util

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoField

object Utils {
    const val PROFILE = "https://www.instagram.com/%s/"
    const val POST_LINK = "https://www.instagram.com/p/%s/"
    const val POST_STARTER = "https://www.instagram.com/p/"
    const val REEL_LINK = "https://www.instagram.com/reel/%s/"
    const val IGTV_LINK = "https://www.instagram.com/tv/%s/"
    const val STORY_LINK = "https://www.instagram.com/stories/%1\$s/%2\$s/"
    const val STORY_HL_STARTER = "https://www.instagram.com/stories/highlights/"
    const val PROFILE_PHOTO = "profile_photo"


    /** Helper class for turning 1 to "01" */
    fun z(n: Int): String {
        val s = n.toString()
        return if (s.length == 1) "0$s" else s
    }

    fun now(): Long = System.currentTimeMillis() / 1000L

    private fun zonedDateTime(time: Long): ZonedDateTime =
        Instant.ofEpochSecond(time).atZone(ZoneId.systemDefault())

    /** Converts a seconds timestamp to a human-readable date. */
    fun date(time: Long): String {
        val dt = zonedDateTime(time)
        return "${dt[ChronoField.YEAR]}.${z(dt[ChronoField.MONTH_OF_YEAR])}." +
            "${z(dt[ChronoField.DAY_OF_MONTH])} - ${z(dt[ChronoField.HOUR_OF_DAY])}:" +
            "${z(dt[ChronoField.MINUTE_OF_HOUR])}:${z(dt[ChronoField.SECOND_OF_MINUTE])}"
    }

    /**
     * @param time a seconds timestamp
     * @return a datetime text to be used in a file name
     */
    fun fileDateTime(time: Long): String {
        val dt = zonedDateTime(time)
        return "${dt[ChronoField.YEAR]}${z(dt[ChronoField.MONTH_OF_YEAR])}" +
            "${z(dt[ChronoField.DAY_OF_MONTH])}_${z(dt[ChronoField.HOUR_OF_DAY])}" +
            "${z(dt[ChronoField.MINUTE_OF_HOUR])}${z(dt[ChronoField.SECOND_OF_MINUTE])}"
    }

    fun <T> Map<String, T>.getOrNull(key: String): T? =
        if (containsKey(key)) this[key] else null

    interface InstaToolsException
}
