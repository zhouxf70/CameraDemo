package com.practice.camerademo.player

import android.media.MediaCodec
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.practice.camerademo.KLog
import com.practice.camerademo.R
import com.practice.camerademo.camera.VideoDecoder
import com.practice.camerademo.camera.VideoEncoder
import com.practice.camerademo.flv.FlvConst
import com.practice.camerademo.flv.FlvTag
import com.practice.camerademo.flv.FlvUnpack
import com.practice.camerademo.gl.GLView
import com.practice.camerademo.image.Yuv
import com.simple.rtmp.DefaultRtmpClient
import com.simple.rtmp.output.FlvWriter
import com.simple.rtmp.output.RtmpStreamWriter
import kotlinx.android.synthetic.main.activity_player.*
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

@RequiresApi(Build.VERSION_CODES.N)
class PlayerActivity : AppCompatActivity(), View.OnClickListener {

    private var mState = State.PREPARE
    private val rtmpUrl = "rtmp://192.168.137.1:1935/live?livestream"
    private lateinit var rtmpWriter: RtmpStreamWriter
    private var rtmpClient: DefaultRtmpClient? = null

    private var mFileOutputStream: FileOutputStream? = null
    private var mFlvUnpack: FlvUnpack? = null
    private var mVideoDecoder: VideoDecoder? = null
    private var decoderReady = false
    private var readyLock = Object()

    private var mRender: GLView.GLRender? = null

    private val flvCallback: (FlvTag?) -> Unit = { tag ->
        if (tag == null) {
            KLog.d("flv read over")
            mFlvUnpack?.close()
            mFlvUnpack = null
            mVideoDecoder?.inputData(null, 0)
        } else {
            when (tag.type) {
                // TODO 应该根据sps、pps而不是metaData获取信息
                FlvConst.FLV_DATA_TYPE_SCRIPT -> initDecoder(tag)
                FlvConst.FLV_DATA_TYPE_AUDIO -> Unit
                FlvConst.FLV_DATA_TYPE_VIDEO -> mVideoDecoder?.inputData(tag.data, tag.timeStamp.toLong())
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON, WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_player)

        et_url.setText(rtmpUrl)
        bt_play.setOnClickListener(this)
        bt_start_download.setOnClickListener(this)
        bt_start_play_file.setOnClickListener(this)

        mRender = gl_surface_view.render
    }

    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.bt_play -> {
                if (mState == State.PREPARE) {
                    mState = State.PLAY_RTMP
                    bt_play.setText(R.string.stop)
                    playRtmp()
                } else stop()
            }
            R.id.bt_start_download -> {
                if (mState == State.PREPARE) {
                    mState = State.DOWNLOAD
                    bt_start_download.setText(R.string.stop)
                    startDownload()
                } else stop()
            }
            R.id.bt_start_play_file -> {
                if (mState == State.PREPARE) {
                    mState = State.PLAY_FILE
                    bt_start_play_file.setText(R.string.stop)
                    startPlayFile()
                } else stop()
            }
        }
    }

    private fun playRtmp() {
        KLog.t("start play rtmp")
        rtmpWriter = FlvUnpackWriter(flvCallback)
        Thread {
            if (rtmpClient == null) {
                rtmpClient = DefaultRtmpClient(rtmpUrl)
                rtmpClient?.connect()
            }
            rtmpClient?.play(rtmpWriter)
        }.start()
    }

    private fun startDownload() {
        KLog.d("start download flv")
        val file = File(filesDir, "rtmp.flv")
        rtmpWriter = FlvWriter(FileOutputStream(file))
        Thread {
            if (rtmpClient == null) {
                rtmpClient = DefaultRtmpClient(rtmpUrl)
                rtmpClient?.connect()
            }
            rtmpClient?.play(rtmpWriter)
        }.start()
    }

    private fun startPlayFile() {
        KLog.t("start play flv")
        val file = File(filesDir, "rtmp.flv")
        mFlvUnpack = FlvUnpack(flvCallback)
        Thread {
            mFlvUnpack?.start(FileInputStream(file))
        }.start()
    }

    private var lastUpdateTime = 0L
    private fun initDecoder(tag: FlvTag) {
        val width = tag.width?.toInt() ?: 0
        val height = tag.height?.toInt() ?: 0
        mRender!!.update(width, height)
        mVideoDecoder = VideoDecoder(VideoEncoder.AVC, width, height).apply {
            callback = object : VideoDecoder.Callback {
                override fun outputDataAvailable(output: Yuv, info: MediaCodec.BufferInfo) {
                    // 手动控制 fps=25
                    val current = System.currentTimeMillis()
                    if (lastUpdateTime + 40 > current) {
                        Thread.sleep(lastUpdateTime + 40 - current)
                    }
                    lastUpdateTime = System.currentTimeMillis()
                    mRender?.update(output.yData, output.uData, output.vData)
                }
            }
            start {
                KLog.d("decoder start")
                synchronized(readyLock) {
                    decoderReady = true
                    readyLock.notifyAll()
                }
            }
        }

        while (!decoderReady) {
            synchronized(readyLock) {
                readyLock.wait()
            }
        }
    }

    private fun stop() {
        mState = State.PREPARE
        bt_play.setText(R.string.play)
        bt_start_download.setText(R.string.start_download)
        bt_start_play_file.setText(R.string.start_play_file)

        rtmpClient?.closeStream()
        rtmpClient?.shutdown()
        rtmpClient = null

        decoderReady = false
        mVideoDecoder?.close()
        mVideoDecoder = null
        mFlvUnpack?.close()
        mFlvUnpack = null
        mFileOutputStream?.close()
        mFileOutputStream = null
    }

    override fun onStop() {
        super.onStop()
        stop()
    }
}