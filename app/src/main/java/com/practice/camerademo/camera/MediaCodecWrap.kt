package com.practice.camerademo.camera

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import androidx.annotation.RequiresApi
import com.practice.camerademo.KLog
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.nio.ByteBuffer

@RequiresApi(Build.VERSION_CODES.N)
class MediaCodecWrap(
    private val type: String,
    private val width: Int,
    private val height: Int,
    private val encoder: Boolean = true,
    private val encoderBySurface: Boolean = false,
    private val fps: Int = 25
) {

    private var isRunning = false
    private var mediaCodec: MediaCodec? = null
    private var outputJob: Job? = null
    private var inputHandler: Handler? = null
    private var inputHandlerThread: HandlerThread? = null
    var encoderSurface: Surface? = null

    init {
        mediaCodec = getCodecByType(type, encoder).apply {
            val mediaFormat = MediaFormat.createVideoFormat(type, width, height)
            if (encoder) {
                val colorFormat = if (encoderBySurface) MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
                else MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible
                mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, colorFormat)
                mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, 1200 * 1024) // 1200 kbps
                mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, fps)  //25
                mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 5)  //关键帧间隔时间 1s
                configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                //createInputSurface only be called after {@link #configure} and before {@link #start}.
                if (encoderBySurface) encoderSurface = createInputSurface()
            } else {
                configure(mediaFormat, null, null, 0)
            }
        }
        if (!encoderBySurface) {
            inputHandlerThread = HandlerThread("InputThread${System.nanoTime()}").also {
                it.start()
                inputHandler = Handler(it.looper)
            }
        }
    }

    fun start(listener: (ByteBuffer, MediaCodec.BufferInfo) -> Unit) {
        mediaCodec?.start()
        isRunning = true
        outputJob = GlobalScope.launch {
            try {
                while (isRunning) {
                    val bufferInfo = MediaCodec.BufferInfo()
                    val index = mediaCodec!!.dequeueOutputBuffer(bufferInfo, -1)
                    KLog.t("outData, index = $index")
                    if (index >= 0 && isRunning) {
                        val outputBuffer = mediaCodec!!.getOutputBuffer(index)
                        if (outputBuffer != null)
                            listener(outputBuffer, bufferInfo)
                        mediaCodec!!.releaseOutputBuffer(index, false)
                    }
                }
            } catch (e: Exception) {
                KLog.e(e)
            }
        }
    }

    fun inputData(input: Any) {
        if (!isRunning) return
        inputHandler?.post {
            try {
                val index = mediaCodec!!.dequeueInputBuffer(100)
                KLog.t("inputData, index = $index")
                if (index >= 0 && isRunning) {
                    val inputBuffer = mediaCodec!!.getInputBuffer(index)!!
                    if (input is ByteArray)
                        inputBuffer.put(input)
                    else if (input is ByteBuffer)
                        inputBuffer.put(input)
                    mediaCodec!!.queueInputBuffer(index, 0, inputBuffer.remaining(), System.nanoTime(), 0)
                }
            } catch (e: Exception) {
                KLog.e(e)
            }
        }
    }

    fun stop() {
        isRunning = false
        mediaCodec?.stop()
    }

    fun close() {
        isRunning = false
        mediaCodec?.apply {
            mediaCodec?.stop()
            mediaCodec?.release()
            mediaCodec = null
            outputJob?.run {
                if (isActive) cancel().also { KLog.d("output job is canceled") }
            }
        }
        inputHandlerThread?.apply {
            quit()
            try {
                inputHandlerThread?.join()
                inputHandlerThread = null
                inputHandler = null
            } catch (e: InterruptedException) {
                KLog.e(e)
            }
        }
    }

    private fun getCodecByType(type: String, encoder: Boolean): MediaCodec {
//        MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos.forEach {
//            KLog.d(it)
//        }
//        MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.forEach {
//            KLog.d(it)
//        }
        val codecInfos = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
        var codec: MediaCodec? = null
        for (codecInfo in codecInfos) {
            codecInfo.run {
                if (isEncoder == encoder)
                    supportedTypes.forEach {
                        if (it == type) {
                            codec = if (isEncoder) MediaCodec.createEncoderByType(type)
                            else MediaCodec.createDecoderByType(type)
                        }
                    }
            }
        }
        return codec ?: throw Exception("cant find type : $type")
    }


    companion object {


        const val AVC = "video/avc"
        const val HEVC = "video/hevc"

        const val DECODER_AVC = "OMX.qcom.video.decoder.avc"
        const val DECODER_HEVC = "OMX.qcom.video.decoder.hevc"
        const val ENCODER_AVC = "OMX.qcom.video.encoder.avc"
        const val ENCODER_HEVC = "OMX.qcom.video.encoder.hevc"
    }
}