### Customising [**KIM**](https://github.com/Ashampoo/kim) (Kotlin Image Manipulation library)

In order to directly implement  inside your source, you'll have to:

1. Import all of src/**commonMain**, excluding `.format.xmp`
2. from src/**jvmMain**, import `.common.ZLib.jvm.kt` and rename it to `.common.ZLib.kt`.
   Then remove the `actual` modifiers inside it.
3. Remove all XMP associations and for your comfort set XMP function parameters to null.
4. From src/**jsMain** (or wasmMain), include `.common.Latin1EncodingExtensions.js.kt`,
   remove the ".js" suffix, replace it and remove its `actual` modifiers.

#### Helper utilities (optional)

- From src/**jvmMain**, you can import `.output.OutputStreamByteWriter.kt`.
- From src/**androidMain**, you can import `.input.AndroidInputStreamByteReader.kt`.

#### Getting rid of [*kotlinx.datetime*]((https://kotlinlang.org/api/kotlinx-datetime/)) (optional)

Until now this is the only dependency this library requires.
Optionally you can get rid of it because of its limited use in this library.

1. Go to `.common.LocalDateTimeExtensions.kt`, remove `YEAR_LENGTH` and `toExifDateString()` and add this:
   ```kotlin
   val exifDateFormat = SimpleDateFormat("yyyy:MM:dd kk:mm:ss", Locale.US)
   ```
2. Go to `.format.tiff.write.TiffOutputSet.kt`; you should see some error in lines 117~ (`applyUpdate()`).
   Replace them with these:
   ```kotlin
   if (update.takenDate != null) {
   
       val exifDateString = exifDateFormat.format(update.takenDate)
   
       exifDirectory.add(ExifTag.EXIF_TAG_DATE_TIME_ORIGINAL, exifDateString)
       exifDirectory.add(ExifTag.EXIF_TAG_DATE_TIME_DIGITIZED, exifDateString)
   }
   ```
3. Go to `.common.PhotoMetadataConverter.kt` at lines 170~ (`extractTakenDateMillisFromExif()`).
   Replace them with these:
   ```kotlin
   var time = exifDateFormat.parse(takenDate).time
   if (takenDateSubSecond != 0) {
       time = Calendar.getInstance().apply {
           timeInMillis = time
           this[Calendar.MILLISECOND] = takenDateSubSecond
       }.timeInMillis
   }
   return time
   ```
4. Remove the field `underUnitTesting` from `.Kim.kt`.
