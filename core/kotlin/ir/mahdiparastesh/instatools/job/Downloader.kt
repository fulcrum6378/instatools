package ir.mahdiparastesh.instatools.job

import com.ashampoo.kim.Kim
import com.ashampoo.kim.common.ImageReadException
import com.ashampoo.kim.format.jpeg.JpegRewriter
import com.ashampoo.kim.format.tiff.constant.ExifTag
import com.ashampoo.kim.format.tiff.constant.TiffTag
import com.ashampoo.kim.format.tiff.write.TiffOutputSet
import com.ashampoo.kim.format.tiff.write.TiffWriterBase
import com.ashampoo.kim.format.webp.WebPImageParser
import com.ashampoo.kim.format.webp.WebPWriter
import com.ashampoo.kim.input.ByteArrayByteReader
import com.ashampoo.kim.output.ByteArrayByteWriter
import com.ashampoo.kim.output.OutputStreamByteWriter
import ir.mahdiparastesh.instatools.api.Api
import ir.mahdiparastesh.instatools.api.Media
import ir.mahdiparastesh.instatools.data.Queued
import ir.mahdiparastesh.instatools.util.Utils
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.SocketTimeoutException
import java.net.URI
import java.net.UnknownHostException
import javax.net.ssl.HttpsURLConnection
import kotlin.io.copyTo

interface Downloader : Queuer<Queued> {

    fun prepareOutput(q: Queued): FileOutputStream?

    override fun handle(q: Queued): Boolean {
        val fos = prepareOutput(q) ?: return true

        // prepare to download the file
        var stream: InputStream? = null
        var retry = -1
        while (stream == null) {
            retry++
            if (retry > 0) {
                if (retry > 5) throw FailureException()
                else onRetry(q)
            }

            val con = URI(q.url).toURL().openConnection(Api.proxy) as HttpsURLConnection
            con.requestMethod = "GET"
            con.useCaches = false
            con.connectTimeout = Api.connectTimeout
            con.doInput = true
            con.readTimeout = when (q.type) {
                Media.Type.IMAGE.num -> 15000
                else -> q.dur?.let { (it * 2000f).toInt() } ?: (2 * 60000)
            }
            try {
                con.connect()
            } catch (_: UnknownHostException) {
                throw Api.FailureException(-1)
            } catch (_: SocketTimeoutException) {
                throw Api.FailureException(-2)
            }

            if (con.responseCode == 200) try {
                stream = con.inputStream
            } catch (_: IOException) {
            }
        }

        // download and save the file
        try {
            when (q.ext) {
                "jpg" -> writeJpeg(q, stream.readBytes(), fos)
                "png" -> stream.copyTo(fos)
                "webp" -> writeWebP(q, stream.readBytes(), fos)
                else -> stream.copyTo(fos)
                // TODO metadata for MP4?
            }
        } catch (_: IOException) {
            throw FailureException()
        }
        stream.close()
        fos.close()
        return true
    }

    fun onRetry(q: Queued)

    @Throws(IOException::class)
    private fun writeJpeg(q: Queued, `in`: ByteArray, out: OutputStream) {
        val reader = ByteArrayByteReader(`in`)
        val outputSet: TiffOutputSet =
            Kim.readMetadata(reader)?.exif?.createOutputSet() ?: TiffOutputSet()

        outputSet.getOrCreateRootDirectory().apply {
            removeField(TiffTag.TIFF_TAG_ARTIST) // Authors
            add(TiffTag.TIFF_TAG_ARTIST, q.owner)
            removeField(TiffTag.TIFF_TAG_COPYRIGHT)
            add(TiffTag.TIFF_TAG_COPYRIGHT, "IG: @${q.owner}")
            removeField(TiffTag.TIFF_TAG_IMAGE_DESCRIPTION) // Title + Subject
            add(TiffTag.TIFF_TAG_IMAGE_DESCRIPTION, q.link)
        }
        outputSet.getOrCreateExifDirectory().apply {
            removeField(ExifTag.EXIF_TAG_SITE)
            add(ExifTag.EXIF_TAG_SITE, q.link)
            removeField(ExifTag.EXIF_TAG_SOFTWARE)
            add(ExifTag.EXIF_TAG_SOFTWARE, Utils.INSTATOOLS)
            q.caption?.also {
                removeField(ExifTag.EXIF_TAG_USER_COMMENT)
                add(ExifTag.EXIF_TAG_USER_COMMENT, it)
            }
        }
        // TODO outputSet.getOrCreateGPSDirectory()
        try {
            JpegRewriter.updateExifMetadataLossless(
                reader, OutputStreamByteWriter(out), outputSet
            )
        } catch (_: ImageReadException) {
            out.write(`in`)
        }
    }

    @Throws(IOException::class)
    private fun writeWebP(q: Queued, `in`: ByteArray, out: OutputStream) {
        val reader = ByteArrayByteReader(`in`)
        val chunks = try {
            WebPImageParser.readChunks(reader, true)
        } catch (_: ImageReadException) {
            out.write(`in`)
            return
        }
        val metadata = WebPImageParser.parseMetadataFromChunks(chunks)
        val outputSet: TiffOutputSet =
            metadata.exif?.createOutputSet() ?: TiffOutputSet()


        outputSet.getOrCreateRootDirectory().apply {
            removeField(TiffTag.TIFF_TAG_ARTIST) // Authors
            add(TiffTag.TIFF_TAG_ARTIST, q.owner)
            removeField(TiffTag.TIFF_TAG_COPYRIGHT)
            add(TiffTag.TIFF_TAG_COPYRIGHT, "IG: @${q.owner}")
            removeField(TiffTag.TIFF_TAG_IMAGE_DESCRIPTION) // Title + Subject
            add(TiffTag.TIFF_TAG_IMAGE_DESCRIPTION, q.link)
        }
        outputSet.getOrCreateExifDirectory().apply {
            removeField(ExifTag.EXIF_TAG_SITE)
            add(ExifTag.EXIF_TAG_SITE, q.link)
            removeField(ExifTag.EXIF_TAG_SOFTWARE)
            add(ExifTag.EXIF_TAG_SOFTWARE, Utils.INSTATOOLS)
            q.caption?.also {
                removeField(ExifTag.EXIF_TAG_USER_COMMENT)
                add(ExifTag.EXIF_TAG_USER_COMMENT, it)
            }
        }

        val exifBytesWriter = ByteArrayByteWriter()
        TiffWriterBase
            .createTiffWriter(outputSet.byteOrder, metadata.exifBytes)
            .write(exifBytesWriter, outputSet)
        WebPWriter.writeImage(
            chunks, OutputStreamByteWriter(out), exifBytesWriter.toByteArray(), null
        )
    }

    class FailureException :
        IllegalStateException("Couldn't download from Instagram!"),
        Utils.InstaToolsException
}
