package com.facedetector.camera

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ImageSaver(private val context: Context) {

    private val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US)
    private val folderName = "FaceDetector"

    fun saveFrame(jpegBytes: ByteArray, rotationDegrees: Int) {
        val filename = "face_${dateFormat.format(Date())}.jpg"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveViaMediaStore(jpegBytes, filename)
        } else {
            saveViaFile(jpegBytes, filename)
        }
    }

    private fun saveViaMediaStore(jpegBytes: ByteArray, filename: String) {
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$folderName")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues) ?: return

        try {
            resolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(jpegBytes)
            }
            contentValues.clear()
            contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, contentValues, null, null)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
        }
    }

    private fun saveViaFile(jpegBytes: ByteArray, filename: String) {
        val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        val detectorDir = File(picturesDir, folderName).also { it.mkdirs() }
        val file = File(detectorDir, filename)

        try {
            FileOutputStream(file).use { fos ->
                fos.write(jpegBytes)
            }
            MediaScannerConnection.scanFile(
                context,
                arrayOf(file.absolutePath),
                arrayOf("image/jpeg"),
                null
            )
        } catch (e: Exception) {
            // Handle silently — do not crash the camera pipeline
        }
    }
}
