package com.example.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Typeface
import android.media.ExifInterface
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ImageWatermarker {
    private const val TAG = "ImageWatermarker"

    fun watermarkImageWithTimestamp(
        photoFile: File,
        customText: String,
        colorHex: String,
        styleIndex: Int // 0: Standard, 1: Retro Digital, 2: Neon Banner, 3: Classic Left
    ) {
        try {
            val filepath = photoFile.absolutePath
            val bitmap = BitmapFactory.decodeFile(filepath) ?: return

            // Load and parse EXIF orientation
            val exif = try {
                ExifInterface(filepath)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load EXIF information", e)
                null
            }

            val orientation = exif?.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            ) ?: ExifInterface.ORIENTATION_NORMAL

            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            }

            // Rotate the bitmap if necessary to be oriented upright
            val rotatedBitmap = if (orientation != ExifInterface.ORIENTATION_NORMAL && orientation != ExifInterface.ORIENTATION_UNDEFINED) {
                Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            } else {
                bitmap
            }

            val mutableBitmap = rotatedBitmap.copy(Bitmap.Config.ARGB_8888, true)
            if (rotatedBitmap != bitmap) {
                rotatedBitmap.recycle()
            }
            bitmap.recycle()

            val canvas = Canvas(mutableBitmap)
            val width = canvas.width
            val height = canvas.height

            // Dynamic text font sizing: make text size proportional to the image height or width
            val baseDimension = if (width < height) width else height
            val calculatedTextSize = (baseDimension * 0.040f).coerceAtLeast(36f)

            // Setup time stamp formatting
            val dateFormatPattern = when (styleIndex) {
                1 -> "yyyy’MM’dd  HH:mm"     // Classic retro LED clock style
                2 -> "yyyy.MM.dd | HH:mm:ss" // Clean separator style
                else -> "yyyy-MM-dd  HH:mm:ss"
            }
            val timeStampString = SimpleDateFormat(dateFormatPattern, Locale.getDefault()).format(Date())

            val finalStampText = if (customText.isNotBlank()) {
                "$customText  •  $timeStampString"
            } else {
                timeStampString
            }

            val paint = Paint().apply {
                isAntiAlias = true
                color = Color.parseColor(colorHex)
                this.textSize = this@ImageWatermarker.coerceTextSize(calculatedTextSize, width, height)
            }

            val marginValue = baseDimension * 0.05f

            when (styleIndex) {
                0 -> { // 0: Standard Bottom Right (classic shadow style)
                    paint.apply {
                        typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
                        textAlign = Paint.Align.RIGHT
                        setShadowLayer(8f, 4f, 4f, Color.BLACK)
                    }
                    canvas.drawText(finalStampText, width - marginValue, height - marginValue, paint)
                }
                1 -> { // 1: Retro LCD Orange
                    paint.apply {
                        color = Color.parseColor("#FF5722") // Retro digital clock orange
                        typeface = Typeface.create("serif", Typeface.BOLD)
                        textAlign = Paint.Align.RIGHT
                        setShadowLayer(10f, 0f, 0f, Color.parseColor("#FF5722"))
                    }
                    // LED panel style: draw a very subtle faint glow
                    canvas.drawText(finalStampText, width - marginValue, height - marginValue, paint)
                }
                2 -> { // 2: Modern Cyber Black Banner (centered)
                    val barHeight = paint.textSize * 2.4f
                    val bannerPaint = Paint().apply {
                        color = Color.parseColor("#B3000000") // 70% black background
                        style = Paint.Style.FILL
                    }
                    canvas.drawRect(0f, height - barHeight, width.toFloat(), height.toFloat(), bannerPaint)

                    paint.apply {
                        color = Color.parseColor("#00E676") // Green cyber-neon
                        textAlign = Paint.Align.CENTER
                        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                        setShadowLayer(6f, 0f, 0f, Color.parseColor("#00E676"))
                    }
                    canvas.drawText(finalStampText, width / 2f, height - (barHeight - paint.textSize) / 1.6f, paint)
                }
                3 -> { // 3: Classic Left Yellow/Custom
                    paint.apply {
                        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                        textAlign = Paint.Align.LEFT
                        setShadowLayer(8f, 3f, 3f, Color.BLACK)
                    }
                    canvas.drawText(finalStampText, marginValue, height - marginValue, paint)
                }
            }

            // Write output back to the file
            val fos = FileOutputStream(photoFile)
            mutableBitmap.compress(Bitmap.CompressFormat.JPEG, 92, fos)
            fos.flush()
            fos.close()
            mutableBitmap.recycle()

            // Update EXIF date/time and orientation to normal (since we pre-rotated the photo)
            try {
                val newExif = ExifInterface(filepath)
                newExif.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
                newExif.setAttribute(
                    ExifInterface.TAG_DATETIME,
                    SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.getDefault()).format(Date())
                )
                newExif.saveAttributes()
            } catch (exifExc: Exception) {
                Log.e(TAG, "Error rewriting metadata", exifExc)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error applying stamp watermark", e)
        }
    }

    private fun coerceTextSize(size: Float, w: Int, h: Int): Float {
        val minDim = if (w < h) w else h
        return size.coerceIn(24f, minDim * 0.08f)
    }
}
