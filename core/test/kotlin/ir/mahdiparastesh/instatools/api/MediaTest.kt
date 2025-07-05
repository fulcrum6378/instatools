package ir.mahdiparastesh.instatools.api

import ir.mahdiparastesh.instatools.data.Download
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

object MediaTest {

    @Test
    fun martinaStoryMedia() {
        val u = "martinasebellin"
        val med: Media = Api.json.decodeFromString<Media>(
            this::class.java.getResourceAsStream("martina_story_media.json")!!
                .readBytes()
                .toString(Charsets.UTF_8)
        )

        // info
        assertEquals(false, med.isPostOrReel())

        // audio
        assertEquals(true, med.hasAudio())
        assert(med.audioUrl()!!.startsWith("https://scontent-ord5-2.cdninstagram.com/o1/"))

        // location
        assertEquals(null, med.latitude())
        assertEquals(null, med.longitude())

        // queue -> Download
        val d: Download = med.queue(owner = u)[0]
        assertEquals("3669215853501495611", d.id)
        assertEquals(1751624633L, d.date)
        assert(d.url.startsWith("https://scontent-ord5-3.cdninstagram.com/o1/"))
        assertEquals(u, d.owner)
        assertEquals(10.368f, d.dur)
    }
}
