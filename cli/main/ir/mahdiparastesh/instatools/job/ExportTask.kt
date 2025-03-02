package ir.mahdiparastesh.instatools.job

import ir.mahdiparastesh.instatools.InvalidCommandException
import ir.mahdiparastesh.instatools.api.Dm
import ir.mahdiparastesh.instatools.api.Media
import ir.mahdiparastesh.instatools.data.Export
import ir.mahdiparastesh.instatools.data.Export.Filters
import ir.mahdiparastesh.instatools.util.Option
import ir.mahdiparastesh.instatools.util.Queue
import ir.mahdiparastesh.instatools.util.Utils
import java.io.File
import java.util.Calendar
import java.util.GregorianCalendar

class ExportTask : Exporter {
    val outputDir = File("./Messages/")
    override val cacheRoot: File = File(".\\AppData\\Roaming\\InstaTools")
    override val queue: Queue<Export> = Queue()
    override var handledItems: Int = 0
    override var proceed: Boolean = true

    companion object {
        const val USER_PROFILE_IMG = "user_%s"
    }

    fun export(thread: Dm.DmThread, cmdOptions: List<String>) {

        // parse options
        var method = Export.METHOD_TEXT
        val filters = Filters()
        var allMedia: Int? = null
        for (kv in cmdOptions) {
            val kvSplit = if ("=" in kv) kv.split("=") else null
            val k = kvSplit?.get(0) ?: kv
            val v = kvSplit?.getOrNull(1)

            when (k) {
                "-t", "t", "--type", "-type", "type" -> when (v) {
                    "JSON", "json" -> Export.METHOD_JSON
                    "TXT", "txt", "TEXT", "text" -> Export.METHOD_TEXT
                    "HTML", "html", "htm", "web" -> Export.METHOD_HTML
                    else -> throw InvalidCommandException("Unsupported export method: $v")
                }
                "--all-media", "-all-media", "all-media" ->
                    allMedia = expSetting(v)
                "--images", "-images", "images", "--image", "-image", "image" ->
                    filters.image = expSetting(v)
                "--videos", "-videos", "videos", "--video", "-video", "video" ->
                    filters.video = expSetting(v)
                "--posts", "-posts", "posts", "--post", "-post", "post" ->
                    filters.post = expSetting(v)!!
                "--reels", "-reels", "reels", "--reel", "-reel", "reel" ->
                    filters.reel = expSetting(v)!!
                "--story", "-story", "story", "--stories", "-stories", "stories" ->
                    filters.story = expSetting(v)!!
                "--uploaded-images", "-uploaded-images", "uploaded-images" ->
                    filters.uploadedImage = expSetting(v)!!
                "--uploaded-videos", "-uploaded-videos", "uploaded-videos" ->
                    filters.video = expSetting(v)
                "--voice", "-voice", "voice" -> filters.voice = when (v) {
                    "yes", "y", "1" -> true
                    "no", "n", "none" -> false
                    else -> throw InvalidCommandException("Please set `yes` or `no` for voice.")
                }
                "--min-date", "-min-date", "min-date" ->
                    filters.minDate = dateTime(v)
                "--max-date", "-max-date", "max-date" ->
                    filters.maxDate = dateTime(v)
                else -> throw InvalidCommandException("Unknown option \"$k\"!")
            }
        }

        // enqueue the item and start the task
        queue.add(Export(thread, method, Utils.now(), filters))
        start()
    }

    private fun expSetting(value: String?): Int? {
        if (value in arrayOf("no", "n", "none")) return null
        if (value in arrayOf("thumb", "thumbnail")) return Media.Version.THUMB
        return Option.quality(value)
    }

    private fun dateTime(value: String?): Long? {
        if (value == null) return null
        val cal = GregorianCalendar(1970, 1, 1, 0, 0, 0)
        cal[Calendar.MILLISECOND] = 0
        val spl = value.split("-")
        for (i in 0..5) cal[when (i) {
            0 -> Calendar.YEAR
            1 -> Calendar.MONTH
            2 -> Calendar.DAY_OF_MONTH
            3 -> Calendar.HOUR_OF_DAY
            4 -> Calendar.MINUTE
            5 -> Calendar.SECOND
            else -> throw InvalidCommandException("Date/time arguments exceeded!")
        }] = try {
            spl[i].toInt() + (if (i == 1) 1 else 0)
        } catch (_: NumberFormatException) {
            throw InvalidCommandException("Something in date-time is Not-A-Number!")
        }
        return cal.timeInMillis
    }

    override fun onHandled(q: Export, success: Boolean) {
    }

    override fun onFinished(fatalError: Exception?) {
    }
}
