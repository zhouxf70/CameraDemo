package com.practice.camerademo.flv

import android.media.MediaCodec
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import androidx.annotation.RequiresApi
import com.practice.camerademo.KLog
import com.practice.camerademo.avc.NalUnit
import com.practice.camerademo.publish.RtmpPublisher
import com.simple.rtmp.Util
import com.simple.rtmp.amf.AmfMap
import com.simple.rtmp.amf.AmfString
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer

/**
 * H.264 封装成 flv
 */
@RequiresApi(Build.VERSION_CODES.N)
class FlvPacket(
    private val width: Int = 0,
    private var height: Int = 0,
    private val onlyVideo: Boolean = true
) {

    private var debug = false
    private var active = false
    private var mOutputStream: FileOutputStream? = null
    private var mPublisher: RtmpPublisher? = null
    private var mHandler: Handler? = null
    private var mHandlerThread: HandlerThread? = null

    private var sps: NalUnit? = null
    private var pps: NalUnit? = null

    init {
        mHandlerThread = HandlerThread("flv_write_file")
        mHandlerThread!!.start()
        mHandler = Handler(mHandlerThread!!.looper)
    }

    fun start(file: File) {
        active = true
        mOutputStream = FileOutputStream(file)
        mHandler?.post {
            val header = if (onlyVideo) FlvConst.FLV_HEADER_ONLY_VIDEO
            else FlvConst.FLV_HEADER
            // 写入FLV头信息
            mOutputStream?.write(header)
            // 写入metadata
            val str = AmfString("onMetaData")
            val map = AmfMap().apply {
                setProperty("duration", 0)
                setProperty("width", width)
                setProperty("height", height)
            }
            val size = str.size + map.size
            mOutputStream?.write(getTagHeader(FlvConst.FLV_DATA_TYPE_SCRIPT, size, 0))
            str.writeTo(mOutputStream)
            map.writeTo(mOutputStream)
            Util.writeUnsignedInt32(mOutputStream, size + 11)
        }
    }

    fun publish(publisher: RtmpPublisher) {
        active = true
        mPublisher = publisher
        publisher.setMetaData(AmfMap().apply {
            setProperty("duration", 0)
            setProperty("width", width)
            setProperty("height", height)
        })
    }

    fun writeVideoFrame(buffer: ByteBuffer, info: MediaCodec.BufferInfo) {
        val pts = (info.presentationTimeUs / 1000).toInt().also { if (debug) KLog.d("pts = $it, size = ${info.size}") }
        var start = 0
        while (start < info.size) {
            val nalU = findNalUnit(buffer, start).also { if (debug) KLog.d(it) }
            if (nalU.start < 0) {
                KLog.e("cant find nal unit : start = $start")
                return
            }
            start = nalU.end + 1

            //访问单元分割符(demo 里面跳过这个NAL)
            if (nalU.type == FlvConst.NAL_UNIT_TYPE_AUD) KLog.d("=============================================")
            // sei 好像需要特殊处理，先不管了~
            if (nalU.type == FlvConst.NAL_UNIT_TYPE_SEI) KLog.d("=============================================")
            if (nalU.type == FlvConst.NAL_UNIT_TYPE_SPS || nalU.type == FlvConst.NAL_UNIT_TYPE_PPS) {
                writeSpsPPsTag(nalU, pts)
                continue
            }
            writeVideoTag(buffer, nalU, pts)
        }
    }

    private fun writeSpsPPsTag(nalUnit: NalUnit, ts: Int) {
        if (sps != null && pps != null) {
            KLog.e("find repeat sps/pps : $nalUnit")
            return
        }
        if (nalUnit.type == FlvConst.NAL_UNIT_TYPE_SPS) sps = nalUnit
        if (nalUnit.type == FlvConst.NAL_UNIT_TYPE_PPS) pps = nalUnit
        if (sps != null && pps != null) {
            val spsPPsTag = getSpsPPsTag(ts).also { if (debug) KLog.d(it) }
            mHandler?.post {
                writeToFile(spsPPsTag)
            }
        }
    }

    private fun writeVideoTag(buffer: ByteBuffer, nalUnit: NalUnit, ts: Int) {
        if (sps == null || pps == null) throw Exception("invalid data")
        val videoTag = getVideoTag(buffer, nalUnit, ts).also { if (debug) KLog.d(it) }
        mHandler?.post {
            writeToFile(videoTag)
        }
    }

    private fun getSpsPPsTag(ts: Int): FlvTag {
        val spsSize = sps!!.size
        val ppsSize = pps!!.size
        val bb = ByteBuffer.allocate(spsSize + ppsSize + 16)
        // keyFrame
        bb.put(FlvConst.KEY_FRAME)
        // AVCPacket+CompositionTime 值都是0
        repeat(4) {
            bb.put(0)
        }
        // ConfigurationVersion
        bb.put(0x01)
        // sps[1] 0 sps[3]
        bb.put(sps!!.data[1])
        bb.put(0)
        bb.put(sps!!.data[3])
        // reserved + lengthSizeMinusOne
        bb.put(0xff.toByte())
        // reserved + numOfSequenceParameterSets  只取第一个sps,该字节为11100001
        bb.put(0xe1.toByte())
        // sps data length
        bb.put((spsSize shr 8 and 0xff).toByte())
        bb.put((spsSize and 0xff).toByte())
        bb.put(sps!!.data)
        // pps个数  只取第一个pps
        bb.put(0x01)
        // pps data length
        bb.put((ppsSize shr 8 and 0xff).toByte())
        bb.put((ppsSize and 0xff).toByte())
        bb.put(pps!!.data)
        if (bb.remaining() != 0) throw Exception("write sps pps data error")
        bb.position(0)
        return FlvTag(FlvConst.FLV_DATA_TYPE_VIDEO, bb.capacity(), ts).apply { data = bb }
    }

    private fun getVideoTag(buffer: ByteBuffer, nalUnit: NalUnit, ts: Int): FlvTag {
        val bb = ByteBuffer.allocate(nalUnit.size + 9)
        // nalU数据前面还有9个字节的video信息
        // 0 : keyFrame=0x17 interFrame = 0x27
        if (nalUnit.type == FlvConst.NAL_UNIT_TYPE_IDR)
            bb.put(FlvConst.KEY_FRAME)
        else
            bb.put(FlvConst.INTER_FRAME)
        // 1~4 AVCPacket+CompositionTime  2~4值都是0
        bb.put(byteArrayOf(FlvConst.AVC_NAL_UNIT, 0, 0, 0))
        // 5~8 nalU length
        bb.put((nalUnit.size shr 24 and 0xff).toByte())
        bb.put((nalUnit.size shr 16 and 0xff).toByte())
        bb.put((nalUnit.size shr 8 and 0xff).toByte())
        bb.put((nalUnit.size and 0xff).toByte())
        // put nalU data
        buffer.position(nalUnit.start)
        buffer.limit(nalUnit.end)
        bb.put(buffer)
        bb.position(0)
        return FlvTag(FlvConst.FLV_DATA_TYPE_VIDEO, bb.capacity(), ts).apply { data = bb }
    }

    /**
     * 每个 TagHeader 都是11个字节
     */
    private fun getTagHeader(type: Byte, dataLength: Int, timeStamp: Int): ByteArray {
        val byteArray = ByteArray(11)
        // 0 : tag type
        byteArray[0] = type
        // 1~3 : data length = 5+[4]+N
        byteArray[1] = (dataLength shr 16 and 0xff).toByte()
        byteArray[2] = (dataLength shr 8 and 0xff).toByte()
        byteArray[3] = (dataLength and 0xff).toByte()
        // 4~6[7] : timeStamp
        byteArray[4] = (timeStamp shr 16 and 0xff).toByte()
        byteArray[5] = (timeStamp shr 8 and 0xff).toByte()
        byteArray[6] = (timeStamp and 0xff).toByte()
        if (timeStamp > 0xffffff)
            byteArray[7] = (timeStamp shr 24 and 0xff).toByte()
        // 8~10 : 0
        return byteArray
    }

    /**
     * 每个NalU都以 00 00 00 01 或者 00 00 01开头
     */
    private val byte0: Byte = 0
    private val byte1: Byte = 1
    private fun findNalUnit(buffer: ByteBuffer, findStart: Int): NalUnit {
        val bufferSize = buffer.remaining()
        if (findStart >= bufferSize - 3) return NalUnit(-1, -1, -1)

        var start = -1
        var end = -1
        var type = -1
        var index = findStart
        while (index < bufferSize - 4) {
            if (buffer[index] == byte0 && buffer[index + 1] == byte0)
                if (buffer[index + 2] == byte1 || (buffer[index + 2] == byte0 && buffer[index + 3] == byte1))
                    if (start < 0) {
                        index += if (buffer[index + 2] == byte1) 2 else 3
                        start = index + 1
                    } else if (end < 0) {
                        end = index - 1
                        index += if (buffer[index + 2] == byte1) 2 else 3
                    } else break
            index++
        }
        if (start > 0 && end < 0) end = bufferSize - 1
        if (start < end) {
            // Nal 第一个字节的低5位是 type
            val typeByte = buffer[start]
            type = typeByte.toInt() and 0x1f
        }
        val nalUnit = NalUnit(start, end, type)
        if (type == FlvConst.NAL_UNIT_TYPE_SPS || type == FlvConst.NAL_UNIT_TYPE_PPS) {
            val data = ByteArray(nalUnit.size)
            for (i in 0 until nalUnit.size) {
                data[i] = buffer[nalUnit.start + i]
            }
            nalUnit.data = data
        }
        return nalUnit
    }

    private fun writeToFile(flvTag: FlvTag) {
        if (!active) return
        if (mOutputStream != null) {
            mOutputStream?.write(getTagHeader(flvTag.type, flvTag.size, flvTag.timeStamp))
            mOutputStream?.write(flvTag.data?.array())
            Util.writeUnsignedInt32(mOutputStream, flvTag.size + 11)
        } else if (mPublisher != null) {
            mPublisher?.publishVideoData(flvTag.data!!.array(), flvTag.size, flvTag.timeStamp)
        }
    }

    fun stop() {
        active = false
        try {
            mHandlerThread?.quit()
            mHandlerThread?.join()
            mHandlerThread = null
            mHandler = null

            mOutputStream?.close()
            mOutputStream = null

            Thread {
                mPublisher?.close()
                mPublisher = null
            }.start()
        } catch (e: Exception) {
            KLog.e(e)
        }
    }

}