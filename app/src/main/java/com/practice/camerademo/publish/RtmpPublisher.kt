package com.practice.camerademo.publish

import com.simple.rtmp.amf.AmfMap
import com.simple.rtmp.io.packets.Data
import java.util.concurrent.atomic.AtomicInteger

/**
 * Created by zxf on 2021/5/28
 */
interface RtmpPublisher {
    /**
     * Issues an RTMP "connect" command and wait for the response.
     *
     * @param url specify the RTMP url
     * @return If succeeded return true else return false
     */
    fun connect(): Boolean

    /**
     * Stop and close the current RTMP streaming client.
     */
    fun close()

    fun setMetaData(map: AmfMap)

    /**
     * publish a video content packet to server
     *
     * @param data video stream byte array
     * @param size video stream byte size (not the whole length of byte array)
     * @param dts video stream decoding timestamp
     */
    fun publishVideoData(data: ByteArray, size: Int, dts: Int)

    /**
     * publish an audio content packet to server
     *
     * @param data audio stream byte array
     * @param size audio stream byte size (not the whole length of byte array)
     * @param dts audio stream decoding timestamp
     */
    fun publishAudioData(data: ByteArray, size: Int, dts: Int)

    /**
     * obtain video frame number cached in publisher
     */
    fun getVideoFrameCacheNumber(): AtomicInteger

    /**
     * obtain the IP address of the peer if any
     */
    fun getServerIpAddr(): String

    /**
     * obtain the PID of the peer if any
     */
    fun getServerPid(): Int

    /**
     * obtain the ID of the peer if any
     */
    fun getServerId(): Int

}