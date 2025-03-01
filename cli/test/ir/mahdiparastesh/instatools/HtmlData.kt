package ir.mahdiparastesh.instatools

import ir.mahdiparastesh.instatools.api.Api
import ir.mahdiparastesh.instatools.api.User
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class HtmlData(private val rewrite: Boolean) {
    val mainPageFile = File("Downloads\\instagram.html")
    val configFile = File("Downloads\\config.json")

    init {
        downloadMainPage()
        stripFromHtml()
        parseJson()
    }

    fun downloadMainPage() {
        if (mainPageFile.exists() && !rewrite) return
        val html = Api.html("https://www.instagram.com/")
        FileOutputStream(mainPageFile)
            .use { it.write(html.encodeToByteArray()) }
    }

    /** From the `ScheduledServerJS` script find the one with `XIGSharedData`. */
    fun stripFromHtml() {
        if (configFile.exists() && !rewrite) return
        val html = FileInputStream(mainPageFile)
            .use { it.readBytes() }
            .toString(Charsets.UTF_8)
        var read = html
        val jsons = arrayListOf<String>()
        val scheduledServerJS = "{\"require\":[[\"ScheduledServerJS\""
        while (read.contains(scheduledServerJS)) {
            read = read.substring(read.indexOf(scheduledServerJS))
            jsons.add(read.substringBefore("</script>"))
            read = read.substringAfter("</script>")
        }
        val json = jsons.find { it.contains("XIGSharedData") }
        // we do not need "XIGSharedData" itself; we just need that JSON which uniquely contains it.
        if (json != null)
            FileOutputStream(configFile).use { it.write(json.encodeToByteArray()) }
        else
            throw Exception("Haven't you been logged out?!")
    }

    fun parseJson() {
        val json = FileInputStream(configFile)
            .use { it.readBytes() }
            .toString(Charsets.UTF_8)
        val scheduledServerJS =
            ((Json.decodeFromString<JsonObject>(json)["require"] as JsonArray)[0] as JsonArray)[3]
                as JsonArray // only the first element of `scheduledServerJS` is useful.
        val define = ((scheduledServerJS[0] as JsonObject)["__bbox"] as JsonObject)["define"]
            as JsonArray // everything is in `define`, it contains ~300 elements!
        var head: JsonPrimitive
        val polarisViewer = (define.find {
            head = (it as JsonArray)[0] as JsonPrimitive
            head.isString && head.content == "PolarisViewer"
            // "XIGSharedData" is its brother, but we don't need it.
        } as JsonArray)[2] as JsonObject
        val user = // resembles that of PROFILE_INFO
            Api.json.decodeFromJsonElement<User>(polarisViewer["data"] as JsonObject)
        println(user.biography)
    }
}
