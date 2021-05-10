package com.practice.camerademo

import java.nio.ByteBuffer

/**
 * Created by zxf on 2021/5/10
 */
object Utils {

    fun bufferToString(byteBuffer: ByteBuffer): String {
        return "position = ${byteBuffer.position()}, limit = ${byteBuffer.limit()}, capacity = ${byteBuffer.capacity()}"
    }
}