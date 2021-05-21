package com.practice.camerademo.image

import android.graphics.*
import android.media.Image
import android.os.Build
import androidx.annotation.RequiresApi
import com.simple.rtmp.KLog
import java.io.*
import java.nio.ByteBuffer
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
//        log("y : $ySize, uv : $uvSize, vu : $vuSize")

        val byteArray = ByteArray(ySize + uvSize + 1).apply {
            set(lastIndex, uvBuffer.get(uvSize - 1))
        }
        yBuffer.get(byteArray, 0, ySize)
        vuBuffer.get(byteArray, ySize, vuSize)
        val rotate = rotate(byteArray, image.width, image.height)
        KLog.d("time : ${System.currentTimeMillis() - start}")
        return rotate
    }

    /**
     * YUV420_888 to NV21
     */
    fun getNV21FromYUV(image: Image): ByteBuffer {
        val start = System.currentTimeMillis()
        val cropRect = image.cropRect
        val width = image.width
        val height = image.height
        val format = image.format
        val bitsPerPixel = ImageFormat.getBitsPerPixel(format)
        val pixel = width * height
        val resultBuffer = ByteBuffer.allocate(pixel * bitsPerPixel / 8)
        if (image.planes.size != 3 || cropRect.width() != width || cropRect.height() != height)
            throw Exception("cant resolve image data")

        for (i in 0..2) {
            val plane = image.planes[i]
            val buffer = plane.buffer
            val pixelStride = plane.pixelStride
            val rowStride = plane.rowStride
            //YUV420_888 : plane#1 -- UU/UV; plane#2 -- VV/VU
            // NV21 :  Y分量之后是VU
            if (i == 1) resultBuffer.position(pixel + 1)
            else if (i == 2) resultBuffer.position(pixel)
            var rowNum = height
            if (i > 0) rowNum = height / 2

            if (cropRect.top > 0) buffer.position(cropRect.top * rowStride)
            for (row in 0 until rowNum) {
                // 每一行跳过前面被剪切的
                if (cropRect.left > 0) buffer.position(buffer.position() + cropRect.left)
                repeat(width / pixelStride) {
                    resultBuffer.put(buffer.get())
                    // VU分量间隔放入
                    if (i > 0 && resultBuffer.remaining() > 0) resultBuffer.position(resultBuffer.position() + 1)
                    // 行内间隔超过1时
                    if (pixelStride > 1 && buffer.remaining() > 0) buffer.position(buffer.position() + pixelStride - 1)
                }
                // 每一行再跳过后面无效的
                // 最后一行不需要跳过，否则可能导致越界
                if (rowStride > cropRect.right && row < rowNum - 1)
                    buffer.position(buffer.position() + rowStride - cropRect.right)
            }
        }
        resultBuffer.position(0)
        KLog.d("time : ${System.currentTimeMillis() - start}")
        return resultBuffer
    }


    fun getDataFromJEPG(image: Image): ByteArray {
        log("getDataFromJEPG")
        val buffer = image.planes[0].buffer
        val byteArr = ByteArray(buffer.remaining())
        buffer.get(byteArr)
        image.close()
        return byteArr
    }

    fun toPicture(data: ByteArray, file: File, width: Int = 1080, height: Int = 1920, format: Int = NV21) {
        var output: FileOutputStream? = null
        try {
            output = FileOutputStream(file).apply {
                if (format == NV21)
                    write(NV21toJPEG(data, width, height))
                else
                    write(data)
            }
        } catch (e: IOException) {
            log(e.toString())
        } finally {
            close(output)
        }
    }

    fun toGif(images: List<ByteArray>, file: File, width: Int = 1080, height: Int = 1920, format: Int = NV21) {
        var data: List<ByteArray> = images
        if (format == NV21)
            data = images.map { NV21toJPEG(it, width, height) }
//        saveGifPicture(data, file)
        val bitmaps = ArrayList<Bitmap>(images.size)
        val options = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.RGB_565
            inSampleSize = 2
        }
        data.forEach { byteArr ->
            val bitmap = BitmapFactory.decodeByteArray(byteArr, 0, byteArr.size, options)
            bitmaps.add(bitmap)
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

    private fun NV21toJPEG(nv21: ByteArray?, width: Int, height: Int, quality: Int = 100): ByteArray {
        val out = ByteArrayOutputStream()
        val yuv = YuvImage(nv21, ImageFormat.NV21, width, height, null)
        yuv.compressToJpeg(Rect(0, 0, width, height), quality, out)
        return out.toByteArray()
    }

    private fun rotate(byteArray: ByteArray, originWidth: Int, originHeight: Int): ByteArray {
        val size = byteArray.size
        val ySize = originWidth * originHeight
        if (size != (ySize * 3 / 2)) throw Exception("illegal w:$originWidth, h:$originHeight, size:$size")
        val newArr = ByteArray(size)
        for (i in 0 until ySize) {
            val originRow = i / originWidth
            val originColumn = i % originWidth
            val newRow = originColumn
            val newColumn = originHeight - originRow - 1
            newArr[newRow * originHeight + newColumn] = byteArray[i]
        }
        for (i in ySize until size step 2) {
            val uIndex = i - ySize
            val originRow = uIndex / originWidth
            val originColumn = uIndex % originWidth / 2
            val newRow = originColumn
            val newColumn = originHeight - originRow * 2 - 2
            newArr[newRow * originHeight + newColumn + ySize] = byteArray[i]
            newArr[newRow * originHeight + newColumn + ySize + 1] = byteArray[i + 1]
        }
        return newArr
    }

    private fun log(any: Any?) {
        KLog.d("ImageUtils", any.toString())
    }
}