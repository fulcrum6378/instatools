package ir.mahdiparastesh.instatools

import ir.mahdiparastesh.instatools.Context.downloadTask
import ir.mahdiparastesh.instatools.Context.latestUser
import ir.mahdiparastesh.instatools.Context.listSvd
import ir.mahdiparastesh.instatools.Context.profiles
import ir.mahdiparastesh.instatools.api.Api
import ir.mahdiparastesh.instatools.api.GraphQlQuery
import ir.mahdiparastesh.instatools.job.SimpleJobs
import ir.mahdiparastesh.instatools.job.SimpleTasks
import ir.mahdiparastesh.instatools.util.Option
import ir.mahdiparastesh.instatools.util.Profile
import ir.mahdiparastesh.instatools.util.Utils
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy

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
set cookies {PATH}             Loads the required cookies from a path. (default: `./cookies.txt`)
set proxy {URL}                Sets an HTTP proxy (e.g. `set proxy http://127.0.0.1:8580/`)

>> List of commands: (type `-h` after each command to see a detailed guide)
d, download <LINK> {OPTIONS}   Downloads a post or a reel via a link.  help: `d -h`
s, saved                       Lists your saved posts.                 help: `s -h`
u, user <@USERNAME|REST_ID>    Shows details about an IG account.      e.g. `u 8337021434`
p, posts <@USERNAME>           Lists main posts of a profile.          help: `p -h`
t, tagged <@USERNAME>          Lists tagged posts of a profile.        help: `t -h`
r, story <@USERNAME>           Lists daily story of a profile.         help: `r -h`
h, highlight <@USERNAME>       Lists highlighted stories of a profile. help: `h -h`
y, tray                        Lists users which have stories in your feed.
q, quit                        Quits the application.

    """.trimIndent()
    )

    val numbersGuide = """

>> Numeric patterns for selecting items:
- `1-5` means 1 up to 5.
- `1,5` means 1 and 5.
- `1-10,15` means 1 up to 10 plus 15 (total 11 items).
- `-35` means since the beginning of the list up to 35.
- `5-` means 5 until the end of the list.
- `all`
    """.trimIndent()

    val qualitiesGuide = """

>> List of qualities:
h, high                        Highest available quality (original)
m, medium                      Medium quality
l, low                         Lowest available quality (often thumbnail for images)
x<NUMBER>                      Ideal width (e.g. x1000) (do NOT separate the number)
y<NUMBER>                      Ideal height (e.g. y1000) (do NOT separate the number)
    """.trimIndent()

    // preparations
    if (!Api.loadCookiesFromFile())
        System.err.println("No cookies found; insert cookies in `cookies.txt` right beside this JAR...")
    if (InetAddress.getLocalHost().hostName in arrayOf("CHIMAERA", "ANGELDUST"))
        Api.proxy = Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", 8580))

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

            /* ---------- BEGIN SETTINGS ---------- */
            "set" -> when (a.getOrNull(1)) {
                "cookies" -> {
                    if (if (a.size > 2) Api.loadCookiesFromFile(a[2]) else Api.loadCookiesFromFile())
                        println("Cookies loaded!")
                    else
                        throw InvalidCommandException("Such a file doesn't exist!")
                }

                "proxy" -> Api.setProxy(a[2])

                null -> throw InvalidCommandException("Invalid setting!")
            }
            /* ----------- END SETTINGS ----------- */


            /* ---------- BEGIN DOWNLOAD ---------- */
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
                downloadTask.download(
                    SimpleJobs.handlePostLink(a[1]),
                    Option.quality(opt?.get(Option.QUALITY.key)),
                    a[1]
                )
            } else if (a[1] in helpOptions)
                println(
                    """
d, download <LINK> {OPTIONS}   Download only a post or reel via its official link.
    -q, --quality=<QUALITY>              A valid quality value (e.g. `-q=high`) (default: `high`)
$qualitiesGuide
                """.trimIndent()
                )
            else
                throw InvalidCommandException("Only links to Instagram posts and reels are supported!")
            /* ----------- END DOWNLOAD ----------- */


            /* ---------- BEGIN SAVED ---------- */
            "s", "saved" -> if (a.size == 1)
                listSvd.fetchSome()
            else when (a[1]) {
                helpOptions[0], helpOptions[1] -> println(
                    """
s, saved                       Continuously list your saved posts.
  s reset                      Forget previously loaded saved posts and load them again.
  s <NUMBER(s)> {OPTIONS}      Download the post in that position.
    -q, --quality=<QUALITY>              A valid quality value (e.g. `-q=high`) (default: `high`)
    -u, --unsave                         Additionally unsave the post.
    -l, --like                           Ensure that the post is liked.
  s [u|unsave] <N> {OPTIONS}   Unsave the post in that position.
    --unlike                             Ensure that the post is unliked.
    -l, --like                           Ensure that the post is liked.
  s [r|resave] <N> {OPTIONS}   Save the post in that position AGAIN.
    --unlike                             Ensure that the post is unliked.
    -l, --like                           Ensure that the post is liked.
$numbersGuide
$qualitiesGuide
                """.trimIndent()
                )

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
                        SimpleTasks.actionMedia(
                            med, if (unsave) GraphQlQuery.UNSAVE else GraphQlQuery.SAVE
                        )

                        // like / unlike
                        if (opt?.contains(Option.LIKE.key) == true)
                            SimpleTasks.actionMedia(med, GraphQlQuery.LIKE_POST)
                        else if (opt?.contains(Option.UNLIKE.key) == true)
                            SimpleTasks.actionMedia(med, GraphQlQuery.UNLIKE_POST)
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
                        downloadTask.download(med, Option.quality(opt?.get(Option.QUALITY.key)))
                        if (opt?.contains(Option.UNSAVE.key) == true)
                            SimpleTasks.actionMedia(med, GraphQlQuery.UNSAVE)
                        if (opt?.contains(Option.LIKE.key) == true)
                            SimpleTasks.actionMedia(med, GraphQlQuery.LIKE_POST)
                    }
                }
            }
            /* ----------- END SAVED ----------- */


            /* ---------- BEGIN USER ---------- */
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
Picture:          ${u.originalPicture()}
Is private?       ${if (u.is_private == true) "Yes" else "No"}
Pronouns:         ${u.pronouns?.joinToString(", ")}
Bio:
${u.biography}

                """.trimIndent()
                )
                latestUser = u.username
                profiles[u.username]?.userId = u.id()
            }
            /* ----------- END USER ----------- */


            /* ---------- BEGIN POSTS ---------- */
            "p", "posts" -> profileCommand(
                a, """
p, posts <@USERNAME>           List main posts of a profile. (e.g. `p @fulcrum6378`)
  p, posts                     Load more posts from the latest user.
  p <@USERNAME> reset          Forget previously loaded main posts of a user and load them again.
  p reset                      Forget previously loaded main posts of the latest user and load them again.
  p <NUMBER(s)> {OPTIONS}      Download the post in that position.
    -q, --quality=<QUALITY>              A valid quality value (e.g. `-q=high`) (default: `high`)
    -l, --like                           Ensure that the post is liked.
$numbersGuide
$qualitiesGuide
            """.trimIndent()
            ) { profile -> profile.posts }
            /* ----------- END POSTS ----------- */


            /* ---------- BEGIN TAGGED ---------- */
            "t", "tagged" -> profileCommand(
                a, """
t, tagged <@USERNAME>          List tagged posts of a profile. (e.g. `t fulcrum6378`)
  t, tagged                    Load more tagged posts from the latest user.
  t <@USERNAME> reset          Forget previously loaded tagged posts of the latest user and load them again.
  t reset                      Forget previously loaded tagged posts of the latest user and load them again.
  t <NUMBERS> {OPTIONS}        Download the tagged post in that position.
    -q, --quality=<QUALITY>              A valid quality value (e.g. `-q=high`) (default: `high`)
    -l, --like                           Ensure that the tagged post is liked.
            """.trimIndent()
            ) { profile -> profile.tagged }
            /* ----------- END TAGGED ----------- */


            /* ---------- BEGIN STORY ---------- */
            "r", "story" -> profileCommand(
                a, """
r, story <@USERNAME>           List daily story of a profile. (e.g. `r @fulcrum6378`)
  r <NUMBER(s)> {OPTIONS}      Download the story item in that position.
    -q, --quality=<QUALITY>              A valid quality value (e.g. `-q=high`) (default: `high`)
    -l, --like                           Ensure that the story is liked.
$numbersGuide
$qualitiesGuide
            """.trimIndent()
            ) { profile -> profile.story }
            /* ----------- END STORY ----------- */


            /* ---------- BEGIN HIGHLIGHTS ---------- */
            "h", "highlight" -> profileCommand(
                a, """
h, highlight <@USERNAME>       List highlighted stories of a profile. (e.g. `h @fulcrum6378`)
  h <HL-ID> <NUMBERS> {OPTIONS}Download the highlight story item in that position.
    -q, --quality=<QUALITY>              A valid quality value (e.g. `-q=high`) (default: `high`)
    -l, --like                           Ensure that the highlighted story is liked.
$numbersGuide
$qualitiesGuide
            """.trimIndent()
            ) { profile -> profile.highlights }
            /* ----------- END HIGHLIGHTS ----------- */


            "y", "tray" -> SimpleTasks.feedTray()

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

fun profileCommand(a: Array<String>, guide: String, lister: (Profile) -> Profile.Section) {
    if (a.size == 1) {
        if (latestUser == null)
            throw InvalidCommandException("Please enter a username.")
        else
            lister(profiles[latestUser]!!).fetch(false)
    } else {
        if (a[1] in helpOptions) {
            println(guide)
            return; }

        val a1UN = when {
            a[1].startsWith("@") -> a[1].substring(1)
            a[1] == "reset" -> latestUser
            a[1].isNotEmpty() && a[1][0].isLetter() -> a[1]
            else -> null
        }
        val un = a1UN ?: latestUser ?: throw InvalidCommandException("Please enter a username.")
        if (un !in profiles) profiles[un] = Profile(un)
        val p = profiles[un]!!
        latestUser = un

        val nextParam = if (a1UN != null) 2 else 1
        when (a.getOrNull(nextParam)) {
            null -> lister(p).fetch(false)

            "reset" -> lister(p).fetch(true)

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

val helpOptions = arrayOf("-h", "--help")

class InvalidCommandException(msg: String = "Invalid command!") :
    IllegalArgumentException(msg), Utils.InstaToolsException
