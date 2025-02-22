package ir.mahdiparastesh.instatools.util

import ir.mahdiparastesh.instatools.InvalidCommandException
import ir.mahdiparastesh.instatools.api.Media

enum class Option(val key: String, val value: Any? = null) {
    QUALITY("q"),
    UNSAVE("us"),
    LIKE("l"),
    UNLIKE("ul"),

    // exporting
    EXP_TYPE("t"),
    EXP_ALL_MEDIA("all-media"),
    EXP_IMAGES("images"),
    EXP_VIDEOS("videos"),
    EXP_POSTS("posts"),
    EXP_REELS("reels"),
    EXP_STORY("story"),
    EXP_UPLOADED_IMAGES("uploaded-images"),
    EXP_UPLOADED_VIDEOS("uploaded-videos"),
    EXP_VOICE("voice"),
    EXP_MIN_DATE("min-date"),
    EXP_MAX_DATE("max-date");


    companion object {
        fun parse(
            options: List<String>, selector: ((key: String) -> Option?)
        ): HashMap<String, String?> {
            val opt = hashMapOf<String, String?>()
            for (kv in options) {
                val kvSplit = if ("=" in kv) kv.split("=") else null
                val k = kvSplit?.get(0) ?: kv
                val addable: Option = selector(k)
                    ?: throw InvalidCommandException("Unknown option \"$k\"!")
                opt[addable.key] = kvSplit?.getOrNull(1)
            }
            return opt
        }

        fun quality(value: String? = null): Float {
            if (value == null) return Media.Version.BEST
            return when (value) {
                "h", "high", "original" -> Media.Version.BEST
                "m", "medium", "med" -> Media.Version.MEDIUM
                "l", "low" -> Media.Version.WORST
                "x" -> try {
                    value.substring(1).toFloat()
                } catch (_: NumberFormatException) {
                    throw InvalidCommandException("\"$value\" is not a valid number!")
                }

                "y" -> try {
                    -value.substring(1).toFloat()
                } catch (_: NumberFormatException) {
                    throw InvalidCommandException("\"$value\" is not a valid number!")
                }

                else -> throw InvalidCommandException("Unknown quality \"$value\"!")
            }
        }
    }
}
