package com.practice.camerademo.camera

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.Build
import android.view.Surface
import androidx.annotation.RequiresApi
import com.practice.camerademo.KLog
import com.practice.camerademo.gl.SurfaceEncodeCore
import java.nio.ByteBuffer

@RequiresApi(Build.VERSION_CODES.N)
class VideoEncoder(
    private val type: String,
    private val width: Int,
    private val height: Int,
    private val fps: Int = 30,
    private val callback: (ByteBuffer, MediaCodec.BufferInfo) -> Unit
) {

    private var debug = false
    private var state = CodecState.PREPARE
    private var mediaCodec: MediaCodec? = null
    private var encodeCore: SurfaceEncodeCore? = null
    var encoderSurface: Surface? = null

    init {
        mediaCodec = MediaCodec.createEncoderByType(type).apply {
            val mediaFormat = MediaFormat.createVideoFormat(type, width, height)
            mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, 100 * 1024)
            mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        }
    }

    fun start(ready: () -> Unit) {
        state = CodecState.RUNNING
        Thread {
            mediaCodec!!.run {
                val inputSurface = createInputSurface()
                encodeCore = SurfaceEncodeCore(width, height)
                val texture = encodeCore?.buildEGLSurface(inputSurface)!!
                encoderSurface = Surface(texture)
                start()
                ready.invoke()

                val startTime = System.nanoTime()
                while (state == CodecState.RUNNING) {
                    outputData()
                    encodeCore?.apply {
                        draw()
                        val curFrameTime = System.nanoTime() - startTime
                        swapData(curFrameTime)
                    }
                }
                if (state == CodecState.STOP) mediaCodec?.stop()
                if (state == CodecState.CLOSE) realClose()
            }
        }.start()
    }

    private fun outputData() {
        while (true) {
            val bufferInfo = MediaCodec.BufferInfo()
            val index = mediaCodec!!.dequeueOutputBuffer(bufferInfo, 100).also { if (debug) KLog.d("output index = $it") }
            if (index < 0 || state != CodecState.RUNNING) break

            val outputBuffer = mediaCodec!!.getOutputBuffer(index)
            if (outputBuffer != null)
                callback.invoke(outputBuffer, bufferInfo)
            mediaCodec!!.releaseOutputBuffer(index, true)
        }
    }

    fun stop() {
        mediaCodec?.signalEndOfInputStream()
        state = CodecState.STOP
    }

    fun close() {
        if (state == CodecState.RUNNING) {
            stop()
            state = CodecState.CLOSE
        } else {
            realClose()
        }
    }

    private fun realClose() {
        encodeCore?.release()
        encoderSurface?.release()
        mediaCodec?.apply {
            release()
            mediaCodec = null
        }
    }


    companion object {
        const val AVC = MediaFormat.MIMETYPE_VIDEO_AVC
        const val HEVC = "video/hevc"
    }
}