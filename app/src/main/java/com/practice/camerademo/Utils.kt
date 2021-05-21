package com.practice.camerademo

import java.nio.ByteBuffer

/**
 * Created by zxf on 2021/5/10
 */
object Utils {

    fun ByteBuffer.toString2(): String {
        return "position = ${position()}, limit = ${limit()}, capacity = ${capacity()}"
    }
}