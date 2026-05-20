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
        styleIndex: Int, // 0: Standard, 1: Retro Digital, 2: Neon Banner, 3: Classic Left
        filterIndex: Int
    ) {
        try {
            val filepath = photoFile.absolutePath

            // Proactively determine image dimensions in a memory-safe boundary
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(filepath, options)

            // Define modern memory-safe max texture dimension (2048px is very high quality but memory safe)
            val maxDimension = 2048
            var inSampleSize = 1
            if (options.outWidth > maxDimension || options.outHeight > maxDimension) {
                val halfHeight = options.outHeight / 2
                val halfWidth = options.outWidth / 2
                while ((halfHeight / inSampleSize) >= maxDimension && (halfWidth / inSampleSize) >= maxDimension) {
                    inSampleSize *= 2
                }
            }

            // Decode the actual bitmap downsampled to target size to avoid any OOM on emulators
            val decodeOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
            }
            val bitmap = BitmapFactory.decodeFile(filepath, decodeOptions) ?: return

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

            val mutableBitmap = Bitmap.createBitmap(rotatedBitmap.width, rotatedBitmap.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(mutableBitmap)
            val filterPaint = Paint().apply {
                isAntiAlias = true
                if (filterIndex > 0) {
                    val colorMatrix = when (filterIndex) {
                        1 -> { // 1: Warm Vintage (Yellowish warm saturation boost)
                            android.graphics.ColorMatrix(floatArrayOf(
                                1.15f, 0f,    0f,    0f,  20f,
                                0f,    1.05f, 0f,    0f,  10f,
                                0f,    0f,    0.85f, 0f,  -15f,
                                0f,    0f,    0f,    1f,  0f
                            ))
                        }
                        2 -> { // 2: Noir Noir / Mono (Grayscale)
                            android.graphics.ColorMatrix().apply {
                                setSaturation(0f)
                                val scale = 1.15f
                                val translate = -10f
                                postConcat(android.graphics.ColorMatrix(floatArrayOf(
                                    scale, 0f,    0f,    0f, translate,
                                    0f,    scale, 0f,    0f, translate,
                                    0f,    0f,    scale, 0f, translate,
                                    0f,    0f,    0f,    1f, 0f
                                )))
                            }
                        }
                        3 -> { // 3: Cool Neon (Blue/Violet Cyberpunk)
                            android.graphics.ColorMatrix(floatArrayOf(
                                0.75f, 0f,    0.35f, 0f, -5f,
                                0f,    1.15f, 0f,    0f,  15f,
                                0.25f, 0f,    1.4f,  0f,  30f,
                                0f,    0f,    0f,    1f,  0f
                            ))
                        }
                        4 -> { // 4: Vintage Sepia (Golden Hour)
                            android.graphics.ColorMatrix(floatArrayOf(
                                0.393f, 0.769f, 0.189f, 0f, 0f,
                                0.349f, 0.686f, 0.168f, 0f, 0f,
                                0.272f, 0.534f, 0.131f, 0f, 0f,
                                0f,     0f,     0f,     1f, 0f
                            ))
                        }
                        5 -> { // 5: High Vivid (Epic saturation)
                            android.graphics.ColorMatrix().apply {
                                setSaturation(1.4f)
                            }
                        }
                        else -> null
                    }
                    if (colorMatrix != null) {
                        colorFilter = android.graphics.ColorMatrixColorFilter(colorMatrix)
                    }
                }
            }
            canvas.drawBitmap(rotatedBitmap, 0f, 0f, filterPaint)

            if (rotatedBitmap != bitmap) {
                rotatedBitmap.recycle()
            }
            bitmap.recycle()

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
