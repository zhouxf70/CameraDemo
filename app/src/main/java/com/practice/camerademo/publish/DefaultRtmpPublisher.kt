package com.practice.camerademo.publish

import com.simple.rtmp.amf.AmfMap
import com.simple.rtmp.io.packets.Data
import java.util.concurrent.atomic.AtomicInteger
import java.util.regex.Pattern

/**
 * Created by zxf on 2021/5/28
 */
class DefaultRtmpPublisher(private val url: String) : RtmpPublisher {

    private val urlPattern = Pattern.compile("^rtmp://([^/:]+)(:(\\d+))*/([^?]+)(\\?(.*))*$")
    private lateinit var publisherImpl: PublisherImpl

    override fun connect(): Boolean {
        val matcher = urlPattern.matcher(url)
        return if (matcher.matches()) {
            val host = matcher.group(1) ?: ""
            val port = matcher.group(3)?.toInt() ?: 1935
            val appName = matcher.group(4) ?: ""
            val streamName = matcher.group(6) ?: ""
            publisherImpl = PublisherImpl(host, port, appName, streamName)
            publisherImpl.connect()
        } else false
    }

    override fun close() {
        publisherImpl.close()
    }

    override fun publishVideoData(data: ByteArray, size: Int, dts: Int) {
        publisherImpl.publishVideoData(data, size, dts)
    }

    override fun publishAudioData(data: ByteArray, size: Int, dts: Int) {
        publisherImpl.publishAudioData(data, size, dts)
    }

    override fun getVideoFrameCacheNumber(): AtomicInteger {
        return publisherImpl.getVideoFrameCacheNumber()
    }

    override fun getServerIpAddr(): String {
        return publisherImpl.getServerIpAddr()
    }

    override fun getServerPid(): Int {
        return publisherImpl.getServerPid()
    }

    override fun getServerId(): Int {
        return publisherImpl.getServerId()
    }

    override fun setMetaData(map: AmfMap) {
        return publisherImpl.setMetaData(map)
    }

}