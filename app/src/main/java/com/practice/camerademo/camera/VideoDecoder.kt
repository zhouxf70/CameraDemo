package com.practice.camerademo.camera

import android.media.MediaCodec
import android.media.MediaFormat
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import androidx.annotation.RequiresApi
import com.practice.camerademo.KLog
import com.practice.camerademo.image.Yuv
import java.nio.ByteBuffer

@RequiresApi(Build.VERSION_CODES.N)
class VideoDecoder(
    private val type: String,
    private val width: Int,
    private val height: Int,
    private val decodeSurface: Surface? = null
) {

    private var state = CodecState.PREPARE
    private var mediaCodec: MediaCodec? = null
    private var inputHandler: Handler? = null
    private var inputHandlerThread: HandlerThread? = null

    var callback: Callback? = null
    var yuv: Yuv = Yuv(width, height)

    init {
        mediaCodec = MediaCodec.createDecoderByType(type).apply {
            val mediaFormat = MediaFormat.createVideoFormat(type, width, height)
            configure(mediaFormat, decodeSurface, null, 0)
        }
    }

    fun start(ready: () -> Unit) {
        state = CodecState.RUNNING
        Thread {
            mediaCodec!!.run {
                inputHandlerThread = HandlerThread("Decode-input-thread").apply {
                    start()
                    inputHandler = Handler(looper)
                    KLog.d(inputFormat)
                    KLog.d(outputFormat)
                }
                start()
                ready.invoke()
                // decode to surface 不需要 output
                if (decodeSurface != null) return@run

                while (state == CodecState.RUNNING) {
                    outputData()
                }
                if (state == CodecState.STOP) mediaCodec?.stop()
                if (state == CodecState.CLOSE) realClose()
            }
        }.start()
    }

    private fun outputData() {
        while (true) {
            val bufferInfo = MediaCodec.BufferInfo()
            val index = mediaCodec!!.dequeueOutputBuffer(bufferInfo, -1).also { log("output index = $it") }
            if (index < 0 || state != CodecState.RUNNING) break

            val outputImage = mediaCodec?.getOutputImage(index)!!
            yuv.setImage(outputImage)
            callback?.outputDataAvailable(yuv, bufferInfo)
            yuv.release()
            outputImage.close()
            mediaCodec!!.releaseOutputBuffer(index, true)
        }
    }

    fun inputData(input: ByteBuffer?, timestamp: Long? = null) {
        if (state != CodecState.RUNNING) return
        inputHandler?.post {
            if (input == null) {
                stop()
                return@post
            }
            try {
                val index = mediaCodec!!.dequeueInputBuffer(-1).also { log("input index = $it") }
                if (index >= 0) {
                    val inputBuffer = mediaCodec!!.getInputBuffer(index)!!
                    inputBuffer.put(input)
                    val ts = timestamp ?: System.nanoTime()
                    mediaCodec!!.queueInputBuffer(index, 0, inputBuffer.remaining(), ts, 0)
                }
            } catch (e: IllegalStateException) {
                log(e)
                e.printStackTrace()
            }
        }
    }

    fun stop() {
        if (decodeSurface != null) mediaCodec?.stop()
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
        mediaCodec?.apply {
            release()
            mediaCodec = null
        }
    }

    interface Callback {
        fun outputDataAvailable(output: Yuv, info: MediaCodec.BufferInfo)
    }

    private fun log(msg: Any?) {
//        KLog.d(msg)
    }

}