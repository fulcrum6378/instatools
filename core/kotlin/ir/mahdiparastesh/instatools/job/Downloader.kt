package ir.mahdiparastesh.instatools.job

import com.ashampoo.kim.common.ImageReadException
import com.ashampoo.kim.common.exifDateFormat
import com.ashampoo.kim.format.ImageMetadata
import com.ashampoo.kim.format.jpeg.JpegImageParser
import com.ashampoo.kim.format.jpeg.JpegRewriter
import com.ashampoo.kim.format.png.PngImageParser
import com.ashampoo.kim.format.png.PngWriter
import com.ashampoo.kim.format.tiff.constant.ExifTag
import com.ashampoo.kim.format.tiff.constant.TiffConstants
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
import ir.mahdiparastesh.instatools.data.Download
import ir.mahdiparastesh.instatools.util.LazyFile
import ir.mahdiparastesh.instatools.util.Utils
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.util.concurrent.CancellationException
import javax.net.ssl.HttpsURLConnection

interface Downloader : Queuer<Download> {

    override fun shouldSkipForNow(q: Download): Boolean = q.status != 0.toByte()

    fun prepareOutput(q: Download): LazyFile<FileOutputStream>?

    override fun handle(q: Download, remaining: Int): Boolean {
        val output = prepareOutput(q) ?: return true

        // prepare to download the file
        var stream: InputStream? = null
        var retry = -1
        while (stream == null) {
            if (!proceed) throw CancellationException()

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

            val responseCode = try {
                con.responseCode
            } catch (e: IOException) {
                val failure = Api.FailureException(e)
                if (failure.code == Api.ERR_BROKEN_CON) continue
                else throw failure
            }

            if (responseCode == 200) try {
                stream = con.inputStream
            } catch (_: IOException) {
            }
        }

        // download and save the file
        try {
            when (q.ext) {
                "jpg" -> writeJpeg(q, stream.readBytes(), output)
                "png" -> writePng(q, stream.readBytes(), output)
                "webp" -> writeWebP(q, stream.readBytes(), output)
                else -> stream.copyTo(output.open())
                // TODO metadata for HEIC and MP4?
            }
        } catch (_: IOException) {
            return false
        } catch (_: ImageReadException) {
            return false
        }
        stream.close()
        output.close()
        return true
    }

    fun onRetry(q: Download)

    override fun onHandled(q: Download, success: Boolean) {
        if (!success) q.status = 1
    }

    private fun writeJpeg(q: Download, ba: ByteArray, out: LazyFile<FileOutputStream>) {
        val outputSet: TiffOutputSet =
            JpegImageParser.parseMetadata(ByteArrayByteReader(ba))
                .exif?.createOutputSet() ?: TiffOutputSet()
        instilExif(q, outputSet)
        JpegRewriter.updateExifMetadataLossless(
            ByteArrayByteReader(ba), OutputStreamByteWriter(out.open()), outputSet
        )
        // NEVER reuse a ByteReader
    }

    private fun writePng(q: Download, ba: ByteArray, out: LazyFile<FileOutputStream>) {
        val chunks = PngImageParser.readChunks(ByteArrayByteReader(ba), null) // NEVER filter
        val metadata = PngImageParser.parseMetadataFromChunks(chunks)
        val outputSet: TiffOutputSet = metadata.exif?.createOutputSet() ?: TiffOutputSet()
        instilExif(q, outputSet)
        PngWriter.writeImage(
            chunks, OutputStreamByteWriter(out.open()), exifBytes(metadata, outputSet), null, null
        )
    }

    private fun writeWebP(q: Download, ba: ByteArray, out: LazyFile<FileOutputStream>) {
        val chunks =
            WebPImageParser.readChunks(ByteArrayByteReader(ba), false) // NEVER set it to true
        val metadata = WebPImageParser.parseMetadataFromChunks(chunks)
        val outputSet = metadata.exif?.createOutputSet() ?: TiffOutputSet()
        instilExif(q, outputSet)
        WebPWriter.writeImage(
            chunks, OutputStreamByteWriter(out.open()), exifBytes(metadata, outputSet), null
        )
    }

    private fun instilExif(q: Download, outputSet: TiffOutputSet) {
        val lacksGps = outputSet
            .getDirectories().none { it.type == TiffConstants.TIFF_DIRECTORY_GPS }
        outputSet.getOrCreateRootDirectory().apply { // directory IFD0
            removeField(TiffTag.TIFF_TAG_ARTIST) // Authors
            add(TiffTag.TIFF_TAG_ARTIST, q.owner)
            removeField(TiffTag.TIFF_TAG_COPYRIGHT)
            add(TiffTag.TIFF_TAG_COPYRIGHT, "IG: @${q.owner}")
            q.caption?.also {
                removeField(TiffTag.TIFF_TAG_IMAGE_DESCRIPTION) // Title + Subject
                add(TiffTag.TIFF_TAG_IMAGE_DESCRIPTION, it)
            }
            removeField(ExifTag.EXIF_TAG_PROCESSING_SOFTWARE) // belongs in the root dir
            add(ExifTag.EXIF_TAG_PROCESSING_SOFTWARE, "Instagram")
            removeField(ExifTag.EXIF_TAG_SOFTWARE) // belongs in the root dir
            add(ExifTag.EXIF_TAG_SOFTWARE, "InstaTools")
        }
        outputSet.getOrCreateExifDirectory().apply { // directory ExifIFD
            removeField(ExifTag.EXIF_TAG_SITE)
            add(ExifTag.EXIF_TAG_SITE, q.link)
            removeField(ExifTag.EXIF_TAG_USER_COMMENT)
            add(ExifTag.EXIF_TAG_USER_COMMENT, q.link)
            removeField(ExifTag.EXIF_TAG_DATE_TIME_DIGITIZED)
            add(ExifTag.EXIF_TAG_DATE_TIME_DIGITIZED, exifDateFormat.format(q.date))
        }
        if (q.lat != null && lacksGps)
            outputSet.setGpsCoordinates(q.coordinates())
    }

    private fun exifBytes(metadata: ImageMetadata, outputSet: TiffOutputSet) =
        ByteArrayByteWriter().apply {
            TiffWriterBase
                .createTiffWriter(outputSet.byteOrder, metadata.exifBytes)
                .write(this, outputSet)
        }.toByteArray()

    class FailureException :
        IllegalStateException("Cannot download from Instagram!"),
        Utils.InstaToolsException
}
