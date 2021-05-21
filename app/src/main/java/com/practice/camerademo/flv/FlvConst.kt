package com.practice.camerademo.flv

/**
 * H.264 和 FLV 常量
 * https://blog.csdn.net/yeyumin89/article/details/7932368
 */
object FlvConst {

    /**
     * H.264 NalUnitType
     * 每个NALU单元开头第一个byte的低5bits
     */
    const val NAL_UNIT_TYPE_SLICE = 1
    const val NAL_UNIT_TYPE_DPA = 2
    const val NAL_UNIT_TYPE_DPB = 3
    const val NAL_UNIT_TYPE_DPC = 4
    const val NAL_UNIT_TYPE_IDR = 5  //key frame
    const val NAL_UNIT_TYPE_SEI = 6
    const val NAL_UNIT_TYPE_SPS = 7  //sps
    const val NAL_UNIT_TYPE_PPS = 8  //pps
    const val NAL_UNIT_TYPE_AUD = 9
    const val NAL_UNIT_TYPE_EOSEQ = 10
    const val NAL_UNIT_TYPE_EOSTREAM = 11
    const val NAL_UNIT_TYPE_FILL = 12

    val NAL_UNIT_START = byteArrayOf(0x00, 0x00, 0x00, 0x01)

    // video tag
    const val KEY_FRAME: Byte = 0x17
    const val INTER_FRAME: Byte = 0x27

    /**
     * FLV文件头，9个字节
     * 1~3    FLV
     * 4       Version :0x01
     * 5       has video:0x01    has audio:0x04
     * 6~9   Length of this header
     *
     * 再加上4个字节的 previous tag length = 0
     */
    val FLV_HEADER_ONLY_VIDEO = byteArrayOf(0x46, 0x4C, 0x56, 0x01, 0x01, 0, 0, 0, 0x09, 0, 0, 0, 0)
    val FLV_HEADER = byteArrayOf(0x46, 0x4C, 0x56, 0x01, 0x05, 0, 0, 0, 0x09, 0, 0, 0, 0)

    /**
     * metaData 第一个AMF   1+2+10
     * AmfDataTypeString : 0x02
     * StringLength : 0x0A (即后面onMetaData长度)
     * onMataData
     */
    val FLV_AMF1 = byteArrayOf(
        0x02, 0x00, 0x0A, 'o'.toByte(), 'n'.toByte(), 'M'.toByte(), 'e'.toByte(), 't'.toByte(), 'a'.toByte(),
        'D'.toByte(), 'a'.toByte(), 't'.toByte(), 'a'.toByte()
    )

    /**
     * metaData 第二个AMF的duration   2+8+1+8
     * 前两个字节为duration长度 00 08
     * duration
     * AmfDataTypeNumber : 0x00
     * Number占8个字节，这里duration先置为0
     */
    val FLV_AMF2_DURATION = byteArrayOf(
        0x00, 0x08, 'd'.toByte(), 'u'.toByte(), 'r'.toByte(), 'a'.toByte(), 't'.toByte(), 'i'.toByte(), 'o'.toByte(), 'n'.toByte(),
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00
    )

    /**
     * metaData 第二个AMF的width   2+5+1+8
     * 前两个字节为width 长度 00 05
     * width
     * AmfDataTypeNumber : 0x00
     * Number占8个字节，这里width先置为1080.0 = 64 144 224 0 0 0 0 0
     */
    val FLV_AMF2_WIDTH = byteArrayOf(
        0x00, 0x05, 'w'.toByte(), 'i'.toByte(), 'd'.toByte(), 't'.toByte(), 'h'.toByte(),
        0x00, 64.toByte(), 144.toByte(), 224.toByte(), 0x00, 0x00, 0x00, 0x00, 0x00
    )

    /**
     * metaData 第二个AMF的height   2+6+1+8
     * 前两个字节为height 长度 00 06
     * height
     * AmfDataTypeNumber : 0x00
     * Number占8个字节，这里height先置为1920.0 = 64 158 0 0 0 0 0 0
     */
    val FLV_AMF2_HEIGHT = byteArrayOf(
        0x00, 0x06, 'h'.toByte(), 'e'.toByte(), 'i'.toByte(), 'g'.toByte(), 'h'.toByte(), 't'.toByte(),
        0x00, 64.toByte(), 158.toByte(), 0x00, 0x00, 0x00, 0x00, 0x00, 0x00
    )

    const val FLV_DATA_TYPE_AUDIO: Byte = 0x08
    const val FLV_DATA_TYPE_VIDEO: Byte = 0x09
    const val FLV_DATA_TYPE_SCRIPT: Byte = 0x12

    // avc packet type
    const val AVC_SEQUENCE_HEADER: Byte = 0x00
    const val AVC_NAL_UNIT: Byte = 0x01


}