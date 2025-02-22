package ir.mahdiparastesh.instatools

import ir.mahdiparastesh.instatools.Context.api
import ir.mahdiparastesh.instatools.Context.downloader
import ir.mahdiparastesh.instatools.Context.exporter
import ir.mahdiparastesh.instatools.api.GraphQlQuery
import ir.mahdiparastesh.instatools.api.Media
import ir.mahdiparastesh.instatools.job.Exporter.Exportable
import ir.mahdiparastesh.instatools.job.Exporter.Method
import ir.mahdiparastesh.instatools.job.SimpleJobs
import ir.mahdiparastesh.instatools.list.Direct
import ir.mahdiparastesh.instatools.list.Saved
import ir.mahdiparastesh.instatools.util.Option
import ir.mahdiparastesh.instatools.util.Profile
import ir.mahdiparastesh.instatools.util.SimpleActions
import ir.mahdiparastesh.instatools.util.Utils
import java.util.*

val listSvd: Saved by lazy { Saved() }
val listMsg: Direct by lazy { Direct() }
val profiles: HashMap<String, Profile> = hashMapOf()
var latestUser: String? = null

fun main(args: Array<String>) {
    val interactive = args.isEmpty()
    println(
        """
InstaTools ${if (interactive) "interactive " else ""}command-line interface
Copyright © Mahdi Parastesh - All Rights Reserved.

    """.trimIndent()
    )

    if (interactive) println(
        """
>> List of settings:
set cookies {PATH}           Load the required cookies from a path. (default: `./cookies.txt`)
set proxy {URL}              Set an HTTP proxy (e.g. `set proxy http://127.0.0.1:8580/`)

>> List of commands:
d, download <LINK> {OPTIONS}   Download only a post or reel via its official link.
    -q, --quality=<QUALITY>              A valid quality value (e.g. `-q=high`) (default: `high`)
s, saved                       Continuously list your saved posts.
  s <NUMBER(s)> {OPTIONS}      Download the post in that position.
    -q, --quality=<QUALITY>              A valid quality value (e.g. `-q=high`) (default: `high`)
    -u, --unsave                         Additionally unsave the post.
    -l, --like                           Ensure that the post is liked.
  s reset                      Forget previously loaded saved posts and load them again.
  s [u|unsave] <N> {OPTIONS}   Unsave the post in that position.
    --unlike                             Ensure that the post is unliked.
    -l, --like                           Ensure that the post is liked.
  s [r|resave] <N> {OPTIONS}   Save the post in that position AGAIN.
    --unlike                             Ensure that the post is unliked.
    -l, --like                           Ensure that the post is liked.
  m reset                      Forget the previously loaded threads and load them again.
u, user <@USERNAME|REST_ID>    Show details about an IG account. (e.g. `u 8337021434`)
p, posts <@USERNAME>           List main posts of a profile. (e.g. `p @fulcrum6378`)
  p, posts                     Load more posts from the latest user.
  p <@USERNAME> reset          Forget previously loaded main posts of a user and load them again.
  p reset                      Forget previously loaded main posts of the latest user and load them again.
  p <NUMBER(s)> {OPTIONS}      Download the post in that position.
    -q, --quality=<QUALITY>              A valid quality value (e.g. `-q=high`) (default: `high`)
    -l, --like                           Ensure that the post is liked.
t, tagged <@USERNAME>          List tagged posts of a profile. (e.g. `t fulcrum6378`)
  t, tagged                    Load more tagged posts from the latest user.
  t <@USERNAME> reset          Forget previously loaded tagged posts of the latest user and load them again.
  t reset                      Forget previously loaded tagged posts of the latest user and load them again.
  t <NUMBERS> {OPTIONS}        Download the tagged post in that position.
    -q, --quality=<QUALITY>              A valid quality value (e.g. `-q=high`) (default: `high`)
    -l, --like                           Ensure that the tagged post is liked.
r, story <@USERNAME>           List daily story of a profile. (e.g. `r @fulcrum6378`)
  r <NUMBER(s)> {OPTIONS}      Download the story item in that position.
    -q, --quality=<QUALITY>              A valid quality value (e.g. `-q=high`) (default: `high`)
    -l, --like                           Ensure that the story is liked.
h, highlight <@USERNAME>       List highlighted stories of a profile. (e.g. `h @fulcrum6378`)
  h <HL-ID> <NUMBERS> {OPTIONS}Download the highlight story item in that position.
    -q, --quality=<QUALITY>              A valid quality value (e.g. `-q=high`) (default: `high`)
    -l, --like                           Ensure that the highlighted story is liked.
m, messages                    List your direct message threads.
  m <NUMBER(s)> {OPTIONS}      Export the thread in that position.
    -t, --type=<HTML,TXT>                File type of the output export
    --all-media=<no|QUALITY>             Default settings for all media (e.g. `--all-media=low`)
    --images=<no|QUALITY>                Default settings for all images (e.g. `--images=low`)
    --videos=<no|thumb|QUALITY>          Default settings for all videos (e.g. `--videos=thumb`)
    --posts=<no|QUALITY>                 Settings for shared posts (e.g. `--posts=no`)
    --reels=<no|thumb|QUALITY>           Settings for shared reels (e.g. `--reels=no`)
    --story=<no|thumb|QUALITY>           Settings for shared stories and highlights (e.g. `--story=no`)
    --uploaded-images=<no|QUALITY>       Settings for directly uploaded images  (e.g. `--uploaded-images=high`)
    --uploaded-videos=<no|thumb|QUALITY> Settings for directly uploaded videos  (e.g. `--uploaded-videos=high`)
    --voice=<no|yes>                     Whether voice messages should be downloaded (e.g. `--voice=yes`)
    --min-date=<DATETIME>                Minimum date for messages to be exported (e.g. `--min-date=2025-01-21`)
    --max-date=<DATETIME>                Minimum date for messages to be exported (e.g. `--min-date=2024`)
q, quit                        Quit the program.

>> Numeric patterns for selecting items:
- `1-5` means 1 up to 5.
- `1,5` means 1 and 5.
- `1-10,15` means 1 up to 10 plus 15 (total 11 items).
- `-35` means since the beginning of the list up to 35.
- `5-` means 5 until the end of the list.
- `all`

>> List of qualities:
h, high                        Highest available quality (original)
m, medium                      Medium quality
l, low                         Lowest available quality (often thumbnail for images)
x<NUMBER>                      Ideal width (e.g. x1000) (do NOT separate the number)
y<NUMBER>                      Ideal height (e.g. y1000) (do NOT separate the number)

    """.trimIndent()
    )

    // preparations
    if (!api.loadCookies())
        System.err.println("No cookies found; insert cookies in `cookies.txt` right beside this JAR...")

    // execute commands
    var repeat = true
    while (repeat) try {
        val a: Array<String>
        if (interactive) {
            println("Type a command: ")
            a = readlnOrNull()?.trim()?.split(" ")?.toTypedArray() ?: continue
            if (a.isEmpty()) continue
        } else {
            a = args
            repeat = false
        }

        when (a[0]) {

            "set" -> when (a.getOrNull(1)) {
                "cookies" -> {
                    if (if (a.size > 2) api.loadCookies(a[2]) else api.loadCookies())
                        println("Cookies loaded!")
                    else
                        throw InvalidCommandException("Such a file doesn't exist!")
                }

                "proxy" -> api.setProxy(a[2])

                null -> throw InvalidCommandException("Invalid setting!")
            }

            "d", "download" -> if (a.size == 1)
                throw InvalidCommandException(
                    "Please enter a link after \"${a[0]}\"; like \"${a[0]} https://\"..."
                )
            else if ("/p/" in a[1] || "/reel/" in a[1]) {
                val opt = if (a.size > 2) Option.parse(a.slice(2..<a.size)) { key ->
                    when (key) {
                        "-q", "q", "--quality", "-quality", "quality" -> Option.QUALITY
                        else -> null
                    }
                } else null
                SimpleJobs.handlePostLink(a[1], Option.quality(opt?.get(Option.QUALITY.key)))
            } else
                throw InvalidCommandException("Only links to Instagram posts and reels are supported!")

            "s", "saved" -> if (a.size == 1)
                listSvd.fetchSome()
            else when (a[1]) {
                "reset" -> listSvd.fetchSome(true)

                "u", "unsave", "r", "resave" -> if (a.size < 3)
                    throw InvalidCommandException("Please enter some numbers.")
                else {
                    val opt = if (a.size > 3) Option.parse(a.slice(3..<a.size)) { key ->
                        when (key) {
                            "-l", "l", "--like", "-like", "like" -> Option.LIKE
                            "--unlike", "-unlike", "unlike" -> Option.UNLIKE
                            else -> null
                        }
                    } else null
                    listSvd[a[2]].forEach { med ->
                        // unsave / resave
                        val unsave = a[1] == "u" || a[1] == "unsave"
                        SimpleActions.actionMedia(med, if (unsave) GraphQlQuery.UNSAVE else GraphQlQuery.SAVE)

                        // like / unlike
                        if (opt?.contains(Option.LIKE.key) == true)
                            SimpleActions.actionMedia(med, GraphQlQuery.LIKE_POST)
                        else if (opt?.contains(Option.UNLIKE.key) == true)
                            SimpleActions.actionMedia(med, GraphQlQuery.UNLIKE_POST)
                    }
                }

                else -> {
                    val opt = if (a.size > 2) Option.parse(a.slice(2..<a.size)) { key ->
                        when (key) {
                            "-q", "q", "--quality", "-quality", "quality" -> Option.QUALITY
                            "-u", "u", "--unsave", "-unsave", "unsave" -> Option.UNSAVE
                            "-l", "l", "--like", "-like", "like" -> Option.LIKE
                            else -> null
                        }
                    } else null
                    listSvd[a[1]].forEach { med ->
                        downloader.download(med, Option.quality(opt?.get(Option.QUALITY.key)))
                        if (opt?.contains(Option.UNSAVE.key) == true)
                            SimpleActions.actionMedia(med, GraphQlQuery.UNSAVE)
                        if (opt?.contains(Option.LIKE.key) == true)
                            SimpleActions.actionMedia(med, GraphQlQuery.LIKE_POST)
                    }
                }
            }

            "u", "user" -> if (a.size != 2)
                throw InvalidCommandException("Please enter a username or the REST ID of a user.")
            else (if (a[1].startsWith("@")) SimpleJobs.profileInfo(a[1].substring(1))
            else try {
                a[1].toLong()
                SimpleJobs.userInfo(a[1])
            } catch (_: NumberFormatException) {
                SimpleJobs.profileInfo(a[1])
            }).also { u ->
                println(
                    """
Full name:        ${u.full_name}
Username:         @${u.username}
REST ID:          ${u.id()}
Picture:          ${u.picture()}
Is private?       ${if (u.is_private == true) "Yes" else "No"}
Pronouns:         ${u.pronouns?.joinToString(", ")}
Bio:
${u.biography}

                """.trimIndent()
                )
                latestUser = u.username
                profiles[u.username]?.userId = u.id()
            }

            "p", "posts" -> profileCommand(a) { profile -> profile.posts }

            "t", "tagged" -> profileCommand(a) { profile -> profile.tagged }

            "r", "story" -> profileCommand(a) { profile -> profile.story }

            "h", "highlight" -> profileCommand(a) { profile -> profile.highlights }

            "m", "messages" -> if (a.size == 1)
                listMsg.fetchSome()
            else when (a[1]) {
                "reset" -> listMsg.fetchSome(true)

                else -> {
                    if (a.size == 2) throw InvalidCommandException("Please specify options for the export.")
                    val opt = Option.parse(a.slice(2..<a.size)) { expOptionSelector(it) }
                    listMsg[a[1]].forEach { thread ->
                        val allMedia = opt[Option.EXP_ALL_MEDIA.key]
                        val exp = Exportable(
                            "Exported ${thread.title()}_${Utils.fileDateTime(Utils.now())}",
                            thread,
                            when (opt[Option.EXP_TYPE.key]) {
                                "HTML", "html", "htm", "web" -> Method.HTML
                                "TXT", "txt", "TEXT", "text" -> Method.TEXT
                                else -> throw InvalidCommandException(
                                    "Unsupported export method: ${opt[Option.EXP_TYPE.key]}"
                                )
                            },
                            expSetting(allMedia ?: opt[Option.EXP_IMAGES.key]),
                            expSetting(allMedia ?: opt[Option.EXP_VIDEOS.key]),
                            expSetting(allMedia ?: opt[Option.EXP_POSTS.key]),
                            expSetting(allMedia ?: opt[Option.EXP_REELS.key]),
                            expSetting(allMedia ?: opt[Option.EXP_STORY.key]),
                            expSetting(allMedia ?: opt[Option.EXP_UPLOADED_IMAGES.key]),
                            expSetting(allMedia ?: opt[Option.EXP_UPLOADED_VIDEOS.key]),
                            when (opt[Option.EXP_VOICE.key]) {
                                "yes", "y", "1" -> true
                                "no", "n", "none" -> false
                                else -> throw InvalidCommandException("Please set `yes` or `no` for voice.")
                            },
                            dateTime(opt[Option.EXP_MIN_DATE.key]),
                            dateTime(opt[Option.EXP_MAX_DATE.key]),
                        )
                        exporter.enqueue(exp)
                    }
                }
            }

            "q", "quit" -> repeat = false

            else -> throw InvalidCommandException("Unknown command: ${a[0]}")
        }

    } catch (e: Exception) {
        if (e is Utils.InstaToolsException)
            System.err.println(e.message)
        else throw e
    }

    println("Good luck!")
}

private fun expOptionSelector(key: String) = when (key) {
    "-u", "u", "--unsave", "-unsave", "unsave" -> Option.UNSAVE
    "-q", "q", "--quality", "-quality", "quality" -> Option.QUALITY
    "-t", "t", "--type", "-type", "type" -> Option.EXP_TYPE
    "--all-media", "-all-media", "all-media" -> Option.EXP_ALL_MEDIA
    "--images", "-images", "images", "--image", "-image", "image" -> Option.EXP_IMAGES
    "--videos", "-videos", "videos", "--video", "-video", "video" -> Option.EXP_VIDEOS
    "--posts", "-posts", "posts", "--post", "-post", "post" -> Option.EXP_POSTS
    "--reels", "-reels", "reels", "--reel", "-reel", "reel" -> Option.EXP_REELS
    "--story", "-story", "story", "--stories", "-stories", "stories" -> Option.EXP_STORY
    "--uploaded-images", "-uploaded-images", "uploaded-images" -> Option.EXP_UPLOADED_IMAGES
    "--uploaded-videos", "-uploaded-videos", "uploaded-videos" -> Option.EXP_UPLOADED_VIDEOS
    "--voice", "-voice", "voice" -> Option.EXP_VOICE
    "--min-date", "-min-date", "min-date" -> Option.EXP_MIN_DATE
    "--max-date", "-max-date", "max-date" -> Option.EXP_MAX_DATE
    else -> null
}

private fun expSetting(value: String?): Float? {
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

fun profileCommand(a: Array<String>, lister: (Profile) -> Profile.Section) {
    if (a.size == 1) {
        if (latestUser == null)
            throw InvalidCommandException("Please enter a username.")
        else
            lister(profiles[latestUser]!!).fetch(false)
    } else {
        val a1UN = when {
            a[1].startsWith("@") -> a[1].substring(1)
            a[1].isNotEmpty() && a[1][0].isLetter() -> a[1]
            else -> null
        }
        val un = a1UN ?: latestUser
        ?: throw InvalidCommandException("Please enter a username.")
        if (un !in profiles) profiles[un] = Profile(un)
        val p = profiles[un]!!
        latestUser = un

        val nextParam = if (a1UN != null) 2 else 1
        when (a.getOrNull(nextParam)) {
            null -> lister(p).fetch(false)
            "reset" -> lister(p).fetch(true) // reset can be mistakenly called OneTimeListers

            else -> {
                val sect = lister(p)
                val optIndex = nextParam + sect.numberOfClauses
                val opt = if (a.size > optIndex)
                    Option.parse(a.slice(optIndex..<a.size)) { key ->
                        when (key) {
                            "-q", "q", "--quality", "-quality", "quality" -> Option.QUALITY
                            "-l", "l", "--like", "-like", "like" -> Option.LIKE
                            else -> null
                        }
                    } else null

                lister(p).download(a, nextParam, opt)
            }
        }
    }
}

class InvalidCommandException(msg: String = "Invalid command!") :
    IllegalArgumentException(msg), Utils.InstaToolsException
