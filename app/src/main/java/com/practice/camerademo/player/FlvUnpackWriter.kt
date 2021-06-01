package com.practice.camerademo.player

import com.practice.camerademo.flv.FlvConst
import com.practice.camerademo.flv.FlvTag
import com.simple.rtmp.KLog
import com.simple.rtmp.amf.*
import com.simple.rtmp.io.packets.ContentData
import com.simple.rtmp.io.packets.Data
import com.simple.rtmp.io.packets.RtmpHeader
import com.simple.rtmp.output.RtmpStreamWriter
import java.nio.ByteBuffer
import kotlin.experimental.and

/**
 * Created by zxf on 2021/5/27
 */
class FlvUnpackWriter(private val callback: (FlvTag) -> Unit) : RtmpStreamWriter() {

    private val debug = true
    private var acvConfig = false

    override fun write(dataPacket: Data?) {
        dataPacket?.run {
            if (debug) KLog.d("type = $type, msgType = ${header.messageType}")
            if (header.messageType == RtmpHeader.MessageType.DATA_AMF0) {
                val tag = FlvTag(header.messageType.value, header.packetLength, header.absoluteTimestamp).also { KLog.d(it) }
                setMetaData(tag, dataPacket.data)
                callback.invoke(tag)
                return
            }
        }
        throw Exception("lose amf data")
    }

    override fun write(packet: ContentData?) {
        packet?.run {
            val tag = FlvTag(header.messageType.value, header.packetLength, header.absoluteTimestamp)
            if (debug) KLog.d(tag)
            if (data.size != tag.size) throw Exception("data len error : ${tag.size} != ${data.size}")
            when (header.messageType.value) {
                FlvConst.FLV_DATA_TYPE_VIDEO -> {
                    // 第一个字节判断是否是key frame和编码格式
                    val frameType = data[0]
                    val packetType = data[1]
                    when {
                        packetType == FlvConst.AVC_SEQUENCE_HEADER -> setAvcSpsPps(tag, data)
                        frameType == FlvConst.KEY_FRAME -> setVideoData(tag, data, true)
                        frameType == FlvConst.INTER_FRAME -> setVideoData(tag, data, false)
                        else -> throw Exception("Unable to resolve frame type : $frameType")
                    }
                }
                FlvConst.FLV_DATA_TYPE_AUDIO -> setAudioTag(tag, data)
                else -> throw Exception("error type : ${tag.type}")
            }
            callback.invoke(tag)
        }
    }

    private fun setAvcSpsPps(tag: FlvTag, data: ByteArray) {
        if (acvConfig) {
            // TODO 先不管了，丢弃后面的配置没有大影响
            KLog.e("more than one avc configuration")
            return
        }
        acvConfig = true
        var readIndex = 2

        // 包含sps和pps的两个NalUnit
        // data 里面16字节不用写入NalUnit，但是需要加上2个NalU头 00 00 00 01
        val configBuffer = ByteBuffer.allocate(tag.size - 16 + 8)
        // 跳过8个字节，后面还有6字节的sps/pps的数量/长度，再加上前面的frameType和packetType，共16个字节
        readIndex += 8

        // 后5位标识长度
        // TODO 不止一个sps，遇到再解决
        val spsSize = data[readIndex++] and 0x1f
        if (spsSize.toInt() != 1) throw Exception("Unable to resolve pps size : $spsSize")
        // sps长度(2)
        val spsLength = data[readIndex++].toInt() and 0xff shl 8 or (data[readIndex++].toInt() and 0xff)
        configBuffer.put(FlvConst.NAL_UNIT_START)
        configBuffer.put(data, readIndex, spsLength)
        readIndex += spsLength

        // TODO 不止一个pps，遇到再解决
        val ppsSize = data[readIndex++].toInt() and 0x1f
        if (ppsSize != 1) throw Exception("Unable to resolve pps size : $ppsSize")
        // pps长度(2)
        val ppsLength = data[readIndex++].toInt() and 0xff shl 8 or (data[readIndex++].toInt() and 0xff)
        configBuffer.put(FlvConst.NAL_UNIT_START)
        configBuffer.put(data, readIndex, ppsLength)
        readIndex += ppsLength

        configBuffer.flip() // 切换为读状态
        // 检测长度是否正确
        if (configBuffer.remaining() != configBuffer.capacity() || readIndex != data.size)
            throw Exception("read data error")
        tag.data = configBuffer
    }

    private fun setVideoData(tag: FlvTag, data: ByteArray, isKeyFrame: Boolean) {
        // 跳过3个字节composition time
        // 4个字节的NalU长度
        val len0 = data[5].toInt().and(0xff)
        val len1 = data[6].toInt().and(0xff)
        val len2 = data[7].toInt().and(0xff)
        val len3 = data[8].toInt().and(0xff)
        val len = len0.shl(24) or (len1.shl(16)) or (len2.shl(8)) or len3
        if (len != data.size - 9) throw Exception("read data error : data.len = $len")

        val dataBuffer = ByteBuffer.allocate(len + 4)
        dataBuffer.put(FlvConst.NAL_UNIT_START)
        dataBuffer.put(data, 9, len)

        dataBuffer.flip()
        tag.data = dataBuffer
    }

    private fun setAudioTag(tag: FlvTag, data: ByteArray) {
        // TODO aac流还不熟悉，以后再来
    }

    private fun setMetaData(tag: FlvTag, data: MutableList<AmfData>) {
        data.forEach { amf ->
            when (amf) {
                is AmfObject -> {
                    amf.getProperty("duration").run {
                        if (this is AmfNumber) tag.duration = value.also { KLog.d("duration : $it") }
                    }
                    amf.getProperty("width").run {
                        if (this is AmfNumber) tag.width = value.also { KLog.d("width : $it") }
                    }
                    amf.getProperty("height").run {
                        if (this is AmfNumber) tag.height = value.also { KLog.d("height : $it") }
                    }
                }
                is AmfArray -> {
                    for (i in 0..amf.length step 2) {
                        val key = amf.items[i]
                        if (key is AmfString) {
                            val value = amf.items[i + 1]
                            KLog.d(key.value)
                            if (key.value == "duration" && value is AmfNumber) {
                                tag.duration = value.value.also { KLog.d("duration : $it") }
                            }
                            if (key.value == "width" && value is AmfNumber) {
                                tag.width = value.value.also { KLog.d("width : $it") }
                            }
                            if (key.value == "height" && value is AmfNumber) {
                                tag.height = value.value.also { KLog.d("height : $it") }
                            }
                        }
                    }
                }
                else -> throw Exception("cant resolve amf : $amf")
            }
        }
    }

}