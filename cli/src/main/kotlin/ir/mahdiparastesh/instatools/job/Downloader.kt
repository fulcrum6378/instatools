package ir.mahdiparastesh.instatools.job

import ir.mahdiparastesh.instatools.Context.api
import ir.mahdiparastesh.instatools.api.Media
import ir.mahdiparastesh.instatools.util.Queuer
import ir.mahdiparastesh.instatools.util.Utils
import org.apache.commons.imaging.Imaging
import org.apache.commons.imaging.formats.jpeg.JpegImageMetadata
import org.apache.commons.imaging.formats.jpeg.exif.ExifRewriter
import org.apache.commons.imaging.formats.tiff.constants.ExifTagConstants
import org.apache.commons.imaging.formats.tiff.constants.TiffTagConstants
import org.apache.commons.imaging.formats.tiff.write.TiffOutputSet
import java.io.*
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.URI
import java.net.UnknownHostException
import javax.net.ssl.HttpsURLConnection

/** Downloads media and saves them. */
class Downloader : Queuer<Downloader.Queued>() {
    override val outputDir = File("./Downloads/")

    fun download(
        med: Media, idealSize: Float, link: String? = null, owner: String? = null
    ) {
        val u = med.owner()
        if (med.carousel_media != null) for (car in med.carousel_media) enqueue(
            Queued(
                car.pk(),
                Utils.compileSecondsTS(car.taken_at),
                car.nearest(idealSize)!!,
                car.media_type.toInt().toByte(),
                u.username ?: owner!!,
                med.caption?.text,
                link ?: med.link(),
                //car.thumb()
            )
        ) else enqueue(
            Queued(
                med.pk!!,
                Utils.compileSecondsTS(med.taken_at),
                med.nearest(idealSize)!!,
                med.media_type.toInt().toByte(),
                u.username ?: owner!!,
                med.caption?.text,
                link ?: med.link(),
                //med.thumb()
            )
        )
        start()
    }

    /** Contains CLI-specific codes! */
    override fun handle(q: Queued) {
        // prepare the path
        val extension = q.extension()
        val fileName = q.fileName(extension)
        val file = File(outputDir, fileName)
        if (file.exists()) {
            println("File `${fileName}` already exists! Overwrite? (y / any)")
            if (readlnOrNull() !in arrayOf("y", "Y", "yes")) return
        }

        // download the file
        var stream: InputStream? = null
        var retry = -1
        while (stream == null) {
            retry++
            if (retry > 0) {
                if (retry > 5) throw FailureException()
                else println("Retrying for ${q.link}")
            }

            val con = URI(q.url).toURL().openConnection(api.proxy) as HttpsURLConnection
            con.requestMethod = "GET"
            con.useCaches = false
            con.connectTimeout = api.connectTimeout
            con.doInput = true
            con.readTimeout = when (q.type) {
                Media.Type.IMAGE.num -> 15000
                else -> 2 * 60000
            }
            try {
                con.connect()
            } catch (_: UnknownHostException) {
                continue
            } catch (_: ConnectException) {
                continue
            } catch (_: SocketTimeoutException) {
                continue
            }

            if (con.responseCode == 200) try {
                stream = con.inputStream
            } catch (_: IOException) {
            }
        }

        // save the file
        val fos = FileOutputStream(file)
        try {
            when (extension) {
                "jpg" -> writeJpeg(q, stream.readAllBytes(), fos)
                else -> stream.copyTo(fos)
                // TODO metadata for MP4 and WEBP?
            }
        } catch (_: IOException) {
            throw FailureException()
        }
        stream.close()
        fos.close()
        println("Downloaded $fileName")
    }

    @Throws(IOException::class)
    private fun writeJpeg(q: Queued, `in`: ByteArray, out: OutputStream) {
        val outputSet = (Imaging.getMetadata(`in`) as JpegImageMetadata?)?.exif?.outputSet
            ?: TiffOutputSet()
        outputSet.orCreateRootDirectory.apply {
            removeField(TiffTagConstants.TIFF_TAG_IMAGE_DESCRIPTION) // Title + Subject
            if (q.link != null) add(TiffTagConstants.TIFF_TAG_IMAGE_DESCRIPTION, q.link)
            removeField(ExifTagConstants.EXIF_TAG_SOFTWARE)
            add(ExifTagConstants.EXIF_TAG_SOFTWARE, Utils.APP_NAME)
            removeField(TiffTagConstants.TIFF_TAG_ARTIST) // Authors
            add(TiffTagConstants.TIFF_TAG_ARTIST, q.owner)
            removeField(TiffTagConstants.TIFF_TAG_COPYRIGHT)
            add(TiffTagConstants.TIFF_TAG_COPYRIGHT, "IG: @${q.owner}")
        }
        outputSet.orCreateExifDirectory.apply {
            q.caption?.also {
                removeField(ExifTagConstants.EXIF_TAG_USER_COMMENT)
                add(ExifTagConstants.EXIF_TAG_USER_COMMENT, it)
            }
            removeField(ExifTagConstants.EXIF_TAG_SITE)
            add(ExifTagConstants.EXIF_TAG_SITE, q.link)
        }
        // TODO location data?
        ExifRewriter().updateExifMetadataLossless(`in`, out, outputSet)
    }

    /** Data structure for information of a media. */
    data class Queued(
        val id: String,
        val date: Long,
        val url: String,
        val type: Byte,
        val owner: String,
        val caption: String?,
        val link: String?,
        //val thumb: String?,
    ) {
        fun fileName(ext: String) = "${owner}_${Utils.fileDateTime(date)}_$id.$ext"

        fun extension() = URI(url).path.split(".").last()
    }

    inner class FailureException :
        IllegalStateException("Couldn't download from Instagram!"),
        Utils.InstaToolsException
}
