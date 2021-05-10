package com.practice.camerademo.flv

import java.nio.ByteBuffer

data class FlvTag(
        val type: Byte,
        val size: Int, // 该值不是NalUnit的长度
        val timeStamp: Int
) {
    // NalUnit
    var data: ByteBuffer? = null

    // metadata
    var duration: Double? = null
    var width: Double? = null
    var height: Double? = null
}