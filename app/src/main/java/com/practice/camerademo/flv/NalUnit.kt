package com.practice.camerademo.flv

/**
 * H.264 的nal单元
 */
data class NalUnit(val start: Int, val end: Int, val type: Int) {
    val size: Int
        get() = end - start + 1

    /**
     * save sps/pps data
     * 由于H.264的sps和pps不在同一个nalU，需要从两个nalU中取出数据再合并
     */
    lateinit var data: ByteArray
}