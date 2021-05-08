package com.practice.camerademo.image

import android.graphics.*
import android.media.Image
import android.os.Build
import androidx.annotation.RequiresApi
import java.io.*
import kotlin.system.measureTimeMillis

/**
 * Created by zxf on 2021/4/30
 */
@RequiresApi(Build.VERSION_CODES.N)
object ImageUtils {

    const val NV21 = 0
    const val JPEG = 1

    fun getDataFromYUV(image: Image): ByteArray {
        val start = System.currentTimeMillis()
        val yBuffer = image.planes[0].buffer
        val uvBuffer = image.planes[1].buffer
        val vuBuffer = image.planes[2].buffer

        val ySize = yBuffer.remaining()
        val uvSize = uvBuffer.remaining()
        val vuSize = vuBuffer.remaining()
        log("y : $ySize, uv : $uvSize, vu : $vuSize")

        val byteArray = ByteArray(ySize + uvSize + 1).apply {
            set(lastIndex, uvBuffer.get(uvSize - 1))
        }
        yBuffer.get(byteArray, 0, ySize)
        vuBuffer.get(byteArray, ySize, vuSize)
        log("time : ${System.currentTimeMillis() - start}")
        return byteArray
    }

    fun getDataFromJEPG(image: Image): ByteArray {
        log("getDataFromJEPG")
        val buffer = image.planes[0].buffer
        val byteArr = ByteArray(buffer.remaining())
        buffer.get(byteArr)
        image.close()
        return byteArr
    }

    fun toPicture(data: ByteArray, file: File, format: Int = NV21, needAlign: Boolean = false) {
        var output: FileOutputStream? = null
        try {
            output = FileOutputStream(file).apply {
                if (format == NV21)
                    write(NV21toJPEG(data, 100, needAlign))
                else
                    write(data)
            }
        } catch (e: IOException) {
            log(e.toString())
        } finally {
            close(output)
        }
    }

    fun toGif(images: List<ByteArray>, file: File, format: Int = NV21) {
        var data: List<ByteArray> = images
        if (format == NV21)
            data = images.map { NV21toJPEG(it, 100) }
//        saveGifPicture(data, file)
        val bitmaps = ArrayList<Bitmap>(images.size)
        val options = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.RGB_565
            inSampleSize = 2
        }
        val matrix = Matrix().apply { setRotate(90f) }
        data.forEach { byteArr ->
            BitmapFactory.decodeByteArray(byteArr, 0, byteArr.size, options).run {
                Bitmap.createBitmap(this, 0, 0, width, height, matrix, true).also {
                    bitmaps.add(it)
                }
                recycle()
            }
        }
        var output: FileOutputStream? = null
        try {
            output = FileOutputStream(file)
            output.write(generateGIF(bitmaps))
            output.close()
        } catch (e: Exception) {
            log(e)
        } finally {
            close(output)
        }
    }

    private fun generateGIF(bitmaps: List<Bitmap>): ByteArray? {
        val bos = ByteArrayOutputStream()
        val encoder = AnimatedGifEncoder()
        encoder.setDelay(100)
        encoder.start(bos)
        for (bitmap in bitmaps) {
            measureTimeMillis { encoder.addFrame(bitmap) }.also {
                log("time:$it")
                bitmap.recycle()
            }
        }
        encoder.finish()
        return bos.toByteArray()
    }

    private fun saveGifPicture(images: List<ByteArray>, gifFile: File) {
        val path = gifFile.absolutePath.let {
            it.substring(0, it.lastIndexOf("/"))
        }.also { log(it) }
        for (i in images.indices) {
            val file = File(path, "my_gif_pic_$i.jpg").also {
                if (!it.exists()) it.createNewFile().also { log("create") }
            }
            toPicture(images[i], file, JPEG)
        }
    }

    private fun close(closeable: Closeable?) {
        closeable?.let {
            try {
                it.close()
            } catch (e: IOException) {
                log(e.toString())
            }
        }
    }

    private fun NV21toJPEG(nv21: ByteArray?, quality: Int, needAlign: Boolean = false): ByteArray {
        val out = ByteArrayOutputStream()
        val height = if (needAlign) 1088 else 1080
        val yuv = YuvImage(nv21, ImageFormat.NV21, 1920, height, null)
        yuv.compressToJpeg(Rect(0, 0, 1920, height), quality, out)
        return out.toByteArray()
    }

    private fun log(any: Any?) {
//        KLog.d("ImageUtils", any.toString())
    }
}