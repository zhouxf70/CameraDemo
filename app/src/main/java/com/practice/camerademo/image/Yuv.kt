package com.practice.camerademo.image

import android.media.Image
import android.os.Build
import androidx.annotation.RequiresApi
import com.simple.rtmp.KLog
import java.nio.ByteBuffer

/**
 * Created by zxf on 2021/5/19
 */
@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
class Yuv(val width: Int, val height: Int) {

    private val pixel = width * height

    var yData: ByteBuffer = ByteBuffer.allocate(pixel)
    var uData: ByteBuffer = ByteBuffer.allocate(pixel / 4)
    var vData: ByteBuffer = ByteBuffer.allocate(pixel / 4)

    private var inUse = false

    fun setImage(image: Image) {
        val start = System.currentTimeMillis()

        if (inUse) throw Exception("in use")
        inUse = true

        val cropRect = image.cropRect
        if (image.planes.size != 3 || cropRect.width() != width || cropRect.height() != height)
            throw Exception("cant resolve image data")

        for (i in 0..2) {
            val des = when (i) {
                0 -> yData
                1 -> uData
                else -> vData
            }
            val plane = image.planes[i]
            val buffer = plane.buffer
            val pixelStride = plane.pixelStride
            val rowStride = plane.rowStride

            var rowNum = height
            if (i > 0) rowNum /= 2

            if (cropRect.top > 0) buffer.position(cropRect.top * rowStride)
            for (row in 0 until rowNum) {
                // 每一行跳过前面被剪切的
                if (cropRect.left > 0) buffer.position(buffer.position() + cropRect.left)
                if (i == 0) {
                    buffer.limit(buffer.position() + width)
                    des.put(buffer)
                    buffer.limit(buffer.capacity())
                } else {
                    repeat(width / pixelStride) {
                        des.put(buffer.get())
                        // 行内间隔超过1时
                        if (pixelStride > 1 && buffer.remaining() > 0) buffer.position(buffer.position() + pixelStride - 1)
                    }
                }
                // 每一行再跳过后面无效的
                // 最后一行不需要跳过，否则可能导致越界
                if (rowStride > cropRect.right && row < rowNum - 1)
                    buffer.position(buffer.position() + rowStride - cropRect.right)
            }
            des.position(0)
        }
        KLog.d("time : ${System.currentTimeMillis() - start}")
    }

    fun release() {
        inUse = false
        yData.position(0)
        uData.position(0)
        vData.position(0)
    }
}