package com.practice.camerademo.flv

import com.simple.rtmp.KLog
import com.simple.rtmp.Util
import com.simple.rtmp.amf.*
import java.io.BufferedInputStream
import java.io.InputStream
import java.nio.ByteBuffer

/**
 * 将flv流解包，生成h264视频流和aac音频流
 */
class FlvUnpack(val callback: (FlvTag?) -> Unit) {

    private lateinit var input: InputStream
    private var acvConfig = false // 是否已读取h264的ssp和pps
    private var start = false

    fun start(inputStream: InputStream) {
        start = true
        // 必须使用这个，不然AmfObject中使用mark容易出错
        input = BufferedInputStream(inputStream)
        readFlvHeader()
        readTag()
    }

    fun close() {
        start = false
        input.close()
    }

    private fun readTag() {
        while (start) {
            val tag = readTagHeader().also { KLog.d(it) }
            if (tag == null) {
                callback.invoke(tag)
                start = false
                break
            }
            when (tag.type) {
                FlvConst.FLV_DATA_TYPE_SCRIPT -> {
                    val amf1 = AmfString.readStringFrom(input, false).also { KLog.d("amfString : $it") }
                    if (amf1 == "onMetaData")
                        setMetaData(tag)
                }
                FlvConst.FLV_DATA_TYPE_VIDEO -> {
                    // 第一个字节判断是否是key frame和编码格式
                    val frameType = input.read().toByte()
                    val packetType = input.read().toByte()
                    when {
                        packetType == FlvConst.AVC_SEQUENCE_HEADER -> setAvcSpsPps(tag)
                        frameType == FlvConst.KEY_FRAME -> setVideoData(tag, true)
                        frameType == FlvConst.INTER_FRAME -> setVideoData(tag, false)
                        else -> throw Exception("Unable to resolve frame type : $frameType")
                    }
                }
                FlvConst.FLV_DATA_TYPE_AUDIO -> setAudioTag(tag)
                else -> throw Exception("error type : ${tag.type}")
            }
            // 读取previousTagLength并检测长度是否正确
            val previousTagLength = Util.readUnsignedInt32(input)
            if (previousTagLength != tag.size + 11)
                throw Exception("read data error : previousTagLength=$previousTagLength")
            callback.invoke(tag)
        }
    }

    private fun setAudioTag(tag: FlvTag) {
        // TODO aac流还不熟悉，以后再来
        val byteArray = ByteArray(tag.size)
        input.read(byteArray, 0, tag.size)
    }

    private fun setVideoData(tag: FlvTag, isKey: Boolean) {
        // 跳过3个字节composition time
        repeat(3) { input.read() }
        // 4个字节的NalU长度
        val nalULen = Util.readUnsignedInt32(input)
        // 4个字节的NalU头
        val nalUBuffer = ByteBuffer.allocate(nalULen + 4)
        nalUBuffer.put(FlvConst.NAL_UNIT_START)
        // put data
        val buf = ByteArray(nalUBuffer.remaining())
        input.read(buf)
        nalUBuffer.put(buf)

        nalUBuffer.flip() // 切换为读状态
        // 检测长度是否正确:减去frameType(1)+packetType(1)+comTime(3)+nalULen(4)
        if (nalUBuffer.remaining() != tag.size - 9 + 4) throw Exception("read data error")
        tag.data = nalUBuffer
    }

    private fun setAvcSpsPps(tag: FlvTag) {
        if (acvConfig) {
            // TODO 先不管了，丢弃后面的配置没有大影响
            KLog.e("more than one avc configuration")
            return
        }
        acvConfig = true

        // 包含sps和pps的两个NalUnit
        // data 里面16字节不用写入NalUnit，但是需要加上2个NalU头 00 00 00 01
        val configBuffer = ByteBuffer.allocate(tag.size - 16 + 8)
        // 跳过8个字节，后面还有6字节的sps/pps的数量/长度，再加上前面的frameType和packetType，共16个字节
        repeat(8) { input.read() }
        // 后5位标识长度
        // TODO 不止一个sps，遇到再解决
        val spsSize = input.read() and 0x1f
        if (spsSize != 1) throw Exception("Unable to resolve pps size : $spsSize")
        // sps长度(2)
        val spsLength = Util.readUnsignedInt16(input)
        configBuffer.put(FlvConst.NAL_UNIT_START)
        val bufSps = ByteArray(spsLength)
        input.read(bufSps)
        configBuffer.put(bufSps)
        // TODO 不止一个pps，遇到再解决
        val ppsSize = input.read() and 0x1f
        if (ppsSize != 1) throw Exception("Unable to resolve pps size : $ppsSize")
        // pps长度(2)
        val ppsLength = Util.readUnsignedInt16(input)
        configBuffer.put(FlvConst.NAL_UNIT_START)
        val bufPps = ByteArray(ppsLength)
        input.read(bufPps)
        configBuffer.put(bufPps)

        configBuffer.flip() // 切换为读状态
        // 检测长度是否正确
        if (configBuffer.remaining() != configBuffer.capacity()) throw Exception("read data error")
        tag.data = configBuffer
    }

    /**
     * 0 : tag type
     * 1~3 : data length
     * 4~6[7] : timestamp (timestamp extend 是最高位)
     * 8~10 : stream id
     */
    private fun readTagHeader(): FlvTag? {
        val type = input.read()
        if (type < 0) return null
        val len = Util.readUnsignedInt24(input)
        var timestamp = Util.readUnsignedInt24(input)
        val extendTimestamp = input.read()
        if (extendTimestamp > 0) {
            timestamp = timestamp and extendTimestamp.shl(24)
        }
        // stream id : useless
        repeat(3) { input.read() }
        return FlvTag(type.toByte(), len, timestamp)
    }

    private fun readFlvHeader() {
        // 9个字节FLV文件头和4个字节previous tag length
        val header = ByteArray(13)
        input.read(header)
        val format = "${header[0].toChar()}${header[1].toChar()}${header[2].toChar()}"
        if (format != "FLV") throw Exception("format $format is illegal")
        KLog.d("format : $format")
        KLog.d("version: ${header[3]}")
        KLog.d("has video: ${header[4].toInt() == 5 || header[4].toInt() == 1}")
        KLog.d("has audio: ${header[4].toInt() == 5 || header[4].toInt() == 4}")
    }

    private fun setMetaData(tag: FlvTag) {
        when (val amf2 = AmfDecoder.readFrom(input)) {
            is AmfArray -> {
                for (i in 0..amf2.length step 2) {
                    val key = amf2.items[i]
                    if (key is AmfString) {
                        val value = amf2.items[i + 1]
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
            is AmfObject -> {
                amf2.getProperty("duration").run {
                    if (this is AmfNumber) tag.duration = value.also { KLog.d("duration : $it") }
                }
                amf2.getProperty("width").run {
                    if (this is AmfNumber) tag.width = value.also { KLog.d("width : $it") }
                }
                amf2.getProperty("height").run {
                    if (this is AmfNumber) tag.height = value.also { KLog.d("height : $it") }
                }
            }
            else -> {
                KLog.e("unknown amf : $amf2")
            }
        }
    }

}