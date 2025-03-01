package ir.mahdiparastesh.instatools.api

import java.io.FileInputStream
import java.io.FileOutputStream

class HtmlData {
    private val scheduledServerJS = "{\"require\":[[\"ScheduledServerJS\""

    init {
        downloadMainPage(false)
        stripFromHtml()
    }

    fun downloadMainPage(rewrite: Boolean) {
        if (mainPageFile.exists() && !rewrite) return
        val html = Api.html(mainPageUrl)
        FileOutputStream(mainPageFile)
            .use { it.write(html.encodeToByteArray()) }
    }

    fun stripFromHtml(): Boolean {
        val html = FileInputStream(mainPageFile)
            .use { it.readBytes() }
            .toString(Charsets.UTF_8)
        var read = html
        val jsons = arrayListOf<String>()
        while (read.contains(scheduledServerJS)) {
            read = read.substring(read.indexOf(scheduledServerJS))
            jsons.add(read.substringBefore("</script>"))
            read = read.substringAfter("</script>")
        }
        val json = jsons.find { it.contains("XIGSharedData") }
        if (json == null) return false
        else {
            FileOutputStream(configFile).use { it.write(json) }
            return true
        }
    }
}
