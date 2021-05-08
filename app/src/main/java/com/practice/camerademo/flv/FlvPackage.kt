package com.practice.camerademo.flv

import android.media.MediaCodec
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import androidx.annotation.RequiresApi
import com.practice.camerademo.KLog
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer

/**
 * H.264 封装成 flv
 */
@RequiresApi(Build.VERSION_CODES.N)
class FlvPackage {

    var onlyVideo: Boolean = true
    var fps: Int = 25
    var width = 0
    var heigth = 0

    private var mFile: File? = null
    private var mOutputStream: FileOutputStream? = null
    private var mHandler: Handler? = null
    private var mHandlerThread: HandlerThread? = null

    private var timeStamp = 0 //时间戳
    private val frameInterval get() = 1000 / fps
    private var hadAddHeader = false
    private var sps: NalUnit? = null
    private var pps: NalUnit? = null

    fun start(file: File) {
        mFile = file.apply {
            if (!exists()) createNewFile().also { print("create") }
        }
        mHandlerThread = HandlerThread("flv_write_file")
        mHandlerThread!!.start()
        mHandler = Handler(mHandlerThread!!.looper)
    }

    fun writeVideoFrame(buffer: ByteBuffer, info: MediaCodec.BufferInfo) {
        val pts = (info.presentationTimeUs / 1000).toInt().also { KLog.d("pts = $it, size = ${info.size}") }

        var start = 0
        while (start < info.size) {
            val nalU = findNalUnit(buffer, start).also { KLog.d(it) }
            if (nalU.start < 0) {
                KLog.d("cant find nal unit : start = $start")
                return
            }
            start = nalU.end + 1

            //访问单元分割符(demo 里面跳过这个NAL)
            if (nalU.type == FlvConst.NAL_UNIT_TYPE_AUD) KLog.d("=============================================")
            // sei 好像需要特殊处理，先不管了~
            if (nalU.type == FlvConst.NAL_UNIT_TYPE_SEI) KLog.d("=============================================")
            if (nalU.type == FlvConst.NAL_UNIT_TYPE_SPS || nalU.type == FlvConst.NAL_UNIT_TYPE_PPS) {
                writeSpsPPsTag(nalU)
                continue
            }
            writeVideoTag(buffer, nalU)
        }
    }

    private fun writeSpsPPsTag(nalUnit: NalUnit) {
        if (sps != null && pps != null) {
            KLog.e("find repeat sps/pps : $nalUnit")
            return
        }
        if (nalUnit.type == FlvConst.NAL_UNIT_TYPE_SPS) sps = nalUnit
        if (nalUnit.type == FlvConst.NAL_UNIT_TYPE_PPS) pps = nalUnit
        if (sps != null && pps != null) {
            val spsPPsTag = getSpsPPsTag()
            mHandler?.post {
                writeToFile(spsPPsTag)
                writeToFile(getPreviousTagLength(sps!!.size + pps!!.size + 27))
            }
        }
    }

    private fun writeVideoTag(buffer: ByteBuffer, nalUnit: NalUnit) {
        if (sps == null || pps == null) throw Exception("invalid data")
        val videoTag = getVideoTag(buffer, nalUnit)
        mHandler?.post {
            writeToFile(videoTag)
            writeToFile(getPreviousTagLength(nalUnit.size + 20))
        }
    }

    private fun getSpsPPsTag(): ByteArray {
        val spsSize = sps!!.size
        val ppsSize = pps!!.size
        val byteArray = ByteArray(spsSize + ppsSize + 27)
        // headerLength = 11
        // dataLength = 5+nalUnitLength
        setTagHeader(byteArray, FlvConst.FLV_DATA_TYPE_VIDEO, spsSize + ppsSize + 16)
        // nalU 数据前有5个字节video信息
        // 11 : keyFrame
        byteArray[11] = FlvConst.KEY_FRAME
        // 12~15 : AVCPacket+CompositionTime 值都是0
        // 16 : ConfigurationVersion
        byteArray[16] = 0x01
        // 17~19 : sps[1] sps[2] sps[3]
        byteArray[17] = sps!!.data[1]
        byteArray[18] = sps!!.data[2]
        byteArray[19] = sps!!.data[3]
        // 20 : reserved + lengthSizeMinusOne
        byteArray[20] = 0xff.toByte()
        // 21 : reserved + numOfSequenceParameterSets  只取第一个sps,该字节为11111101
        byteArray[21] = 0xe1.toByte()
        // 22~23 : sps data length
        byteArray[22] = (spsSize shr 8 and 0xff).toByte()
        byteArray[23] = (spsSize and 0xff).toByte()
        // 24~23+sps.size : sps data
        for (i in 0 until spsSize) {
            byteArray[24 + i] = sps!!.data[i]
        }
        // 24+sps.size : pps个数  只取第一个pps
        byteArray[24 + spsSize] = 0x01
        // 25+sps.size~26+sps.size : pps data length
        byteArray[25 + spsSize] = (ppsSize shr 8 and 0xff).toByte()
        byteArray[26 + spsSize] = (ppsSize and 0xff).toByte()
        // 27+sps.size~26+sps.size+pps.size : pps data
        for (i in 0 until ppsSize) {
            byteArray[27 + spsSize + i] = pps!!.data[i]
        }
        return byteArray
    }

    private fun getVideoTag(buffer: ByteBuffer, nalUnit: NalUnit): ByteArray {
        val byteArray = ByteArray(20 + nalUnit.size)
        // headerLength = 11
        // dataLength = 5+[4]+nalUnitLength  其中配置tag不用加表示nalU长度的4个字节
        setTagHeader(byteArray, FlvConst.FLV_DATA_TYPE_VIDEO, nalUnit.size + 9, true)
        // nalU数据前面还有9个字节的video信息
        // 0 : keyFrame=0x17 interFrame = 0x27
        byteArray[11] = if (nalUnit.type == FlvConst.NAL_UNIT_TYPE_IDR) FlvConst.KEY_FRAME else FlvConst.INTER_FRAME
        // 1~4 AVCPacket+CompositionTime  2~4值都是0
        byteArray[12] = FlvConst.AVC_NAL_UNIT
        // 5~8 nalU length
        byteArray[16] = (nalUnit.size shr 24 and 0xff).toByte()
        byteArray[17] = (nalUnit.size shr 16 and 0xff).toByte()
        byteArray[18] = (nalUnit.size shr 8 and 0xff).toByte()
        byteArray[19] = (nalUnit.size and 0xff).toByte()
//        KLog.d("nalU len ${nalUnit.size} : ${byteArray[16]}, ${byteArray[17]}, ${byteArray[18]}, ${byteArray[19]}")
        // put nalU data
        buffer.position(nalUnit.start)
        for (i in 0 until nalUnit.size) {
            byteArray[20 + i] = buffer.get()
        }
        return byteArray
    }

    /**
     * 每个 TagHeader 都是11个字节
     */
    private fun setTagHeader(byteArray: ByteArray, type: Byte, dataLength: Int, addTimestamp: Boolean = false) {
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
        // 8~10 : 0
        if (addTimestamp) timeStamp += frameInterval
    }

    private fun getPreviousTagLength(len: Int): ByteArray {
        val byteArray = ByteArray(4)
        byteArray[0] = (len shr 24 and 0xff).toByte()
        byteArray[1] = (len shr 16 and 0xff).toByte()
        byteArray[2] = (len shr 8 and 0xff).toByte()
        byteArray[3] = (len and 0xff).toByte()
        return byteArray
    }

    /**
     * DataLen(73) = AMF1(13) + AMF2Header(5) + duration(19) + width(16) + height(17) + end(3)
     * TagLen(88)  = header(11) + data(73) + previousTagLen(4)
     */
    private fun getScriptTag(): ByteArray {
        val byteArray = ByteArray(88)
        // headerLength  11个字节
        setTagHeader(byteArray, FlvConst.FLV_DATA_TYPE_SCRIPT, 73)
        // 第一个AMF包类型是String，13个字节，全是固定值
        for (i in 0 until 13)
            byteArray[11 + i] = FlvConst.FLV_AMF1[i]
        // 第二个AMF包类型是数组，包头1+4 = 1个字节数组类型(0x08) + 4个字节数组元素个数(3)
        byteArray[24] = 0x08
        byteArray[28] = 0x03
        // 数组第一个元素duration，19个字节，可纪录位置，录像停止后再回来修改
        for (i in 0 until 19)
            byteArray[29 + i] = FlvConst.FLV_AMF2_DURATION[i]
        // 数组第二个元素width，16个字节，值先写死1920
        for (i in 0 until 16)
            byteArray[48 + i] = FlvConst.FLV_AMF2_WIDTH[i]
        // 数组第二个元素height，17个字节，值先写死1080
        for (i in 0 until 17)
            byteArray[64 + i] = FlvConst.FLV_AMF2_HEIGHT[i]
        // 数组结束位 3个字节 00 00 09
        byteArray[83] = 0x09
        // previousTagLen，4个字节
        byteArray[84] = (84 shr 24 and 0xff).toByte()
        byteArray[85] = (84 shr 16 and 0xff).toByte()
        byteArray[86] = (84 shr 8 and 0xff).toByte()
        byteArray[87] = (84 and 0xff).toByte()
        return byteArray
    }

    /**
     * 每个NalU都以 00 00 00 01 或者 00 00 01开头
     */
    private val byte0: Byte = 0
    private val byte1: Byte = 1
    private fun findNalUnit(buffer: ByteBuffer, findStart: Int): NalUnit {
        val bufferSize = buffer.remaining()
        buffer.slice()
        if (findStart >= bufferSize - 3) return NalUnit(-1, -1, -1)

        var start = -1
        var end = -1
        var type = -1
        for (i in findStart..(bufferSize - 3) step 3) {
            if (buffer[i] == byte0 && buffer[i + 1] == byte0)
                if (buffer[i + 2] == byte1 || (i + 3 < bufferSize && buffer[i + 2] == byte0 && buffer[i + 3] == byte1))
                    if (start < 0 && i < bufferSize - 3) {
                        start = if (buffer[i + 2] == byte1) i + 3 else i + 4
                    } else if (end < 0) end = i - 1
                    else break
        }
        if (start > 0 && end < 0) end = bufferSize - 1
        if (start < end) {
            // Nal 第一个字节的低5位是 type
            val typeByte = buffer[start]
            type = typeByte.toInt() and 0x1f
            start++
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

    private fun writeToFile(byteArray: ByteArray) {
        if (mOutputStream == null) {
            mOutputStream = FileOutputStream(mFile)
        }
        if (!hadAddHeader) {
            hadAddHeader = true
            val header = if (onlyVideo)
                FlvConst.FLV_HEADER_ONLY_VIDEO
            else FlvConst.FLV_HEADER
            // 写入FLV头信息
            mOutputStream?.write(header)
            // 写入metadata
            mOutputStream?.write(getScriptTag())
        }
        mOutputStream?.write(byteArray)
    }

    fun stop() {
        try {
            mHandlerThread?.quit()
            mHandlerThread?.join()
            mHandlerThread = null
            mHandler = null
            mOutputStream?.close()
            mOutputStream = null
        } catch (e: Exception) {
            KLog.e(e)
        }
    }

}