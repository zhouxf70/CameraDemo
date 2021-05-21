package com.practice.camerademo.player

import android.media.MediaCodec
import android.opengl.GLSurfaceView
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.practice.camerademo.KLog
import com.practice.camerademo.R
import com.practice.camerademo.camera.VideoDecoder
import com.practice.camerademo.camera.VideoEncoder
import com.practice.camerademo.flv.FlvConst
import com.practice.camerademo.flv.FlvTag
import com.practice.camerademo.flv.FlvUnpack
import com.practice.camerademo.gl.MyGLRender
import com.practice.camerademo.image.Yuv
import com.simple.rtmp.DefaultRtmpClient
import com.simple.rtmp.RtmpClient
import com.simple.rtmp.output.FlvWriter
import com.simple.rtmp.output.RtmpStreamWriter
import kotlinx.android.synthetic.main.activity_player.*
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@RequiresApi(Build.VERSION_CODES.N)
class PlayerActivity : AppCompatActivity(), View.OnClickListener {

    private lateinit var etUrl: EditText
    private lateinit var btDownload: Button
    private lateinit var btPlayFlie: Button

    private var mState = State.PREPARE
    private val rtmpUrl = "rtmp://192.168.137.1:1935/live?livestream"
    private var rtmpClient: RtmpClient? = null
    private lateinit var rtmpWriter: RtmpStreamWriter

    private var mFileInputStream: FileInputStream? = null
    private var mFileOutputStream: FileOutputStream? = null
    private var mFlvUnpack: FlvUnpack? = null
    private var mVideoDecoder: VideoDecoder? = null
    private var decoderReady = false
    private var readyLock = Object()

    private var mPlayExecutor: ExecutorService? = null

    private var mRender: MyGLRender? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        etUrl = findViewById(R.id.et_url)
        etUrl.setText(rtmpUrl)
        btDownload = findViewById<Button>(R.id.bt_start_download).apply { setOnClickListener(this@PlayerActivity) }
        btPlayFlie = findViewById<Button>(R.id.bt_start_play_file).apply { setOnClickListener(this@PlayerActivity) }

        initSurface()
    }

    private fun initSurface() {
        mRender = MyGLRender(gl_surface)
        gl_surface.setEGLContextClientVersion(2)
        gl_surface.setRenderer(mRender)
        gl_surface.renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
    }

    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.bt_start_download -> {
                if (mState == State.PREPARE) {
                    mState = State.DOWNLOADING
                    btDownload.setText(R.string.stop)
                    startDownload()
                } else stop()
            }
            R.id.bt_start_play_file -> {
                if (mState == State.PREPARE) {
                    mState = State.PLAYING
                    btPlayFlie.setText(R.string.stop)
                    startPlayFile()
                } else stop()
            }
        }
    }

    private fun startDownload() {
        Thread {
            KLog.t("start download flv")
            val file = File(filesDir, "rtmp.flv")
            rtmpWriter = FlvWriter(FileOutputStream(file))
            rtmpClient = DefaultRtmpClient(etUrl.text.toString()).apply {
                connect()
                play(rtmpWriter)
            }
        }.start()
    }

    private fun startPlayFile() {
        if (mPlayExecutor == null) {
            mPlayExecutor = Executors.newSingleThreadExecutor()
        }
        KLog.t("start play flv")
        val file = File(filesDir, "my_flv.flv")
        mFileInputStream = FileInputStream(file)
        mFlvUnpack = FlvUnpack().apply {
            callback = object : FlvUnpack.Callback {
                override fun onFlvTagAvailable(tag: FlvTag?) {
                    if (tag == null) {
                        KLog.d("file read over")
                        mFlvUnpack?.close()
                        mFlvUnpack = null
                        mVideoDecoder?.inputData(null, 0)
                        return
                    }
                    when (tag.type) {
                        FlvConst.FLV_DATA_TYPE_SCRIPT -> {
                            initDecoder(tag)
                            while (!decoderReady) {
                                synchronized(readyLock) {
                                    readyLock.wait()
                                }
                            }
                        }
                        FlvConst.FLV_DATA_TYPE_AUDIO -> Unit
                        FlvConst.FLV_DATA_TYPE_VIDEO -> {
                            mVideoDecoder?.inputData(tag.data, tag.timeStamp.toLong())
                        }
                    }
                }
            }
            Thread { start(mFileInputStream!!) }.start()
        }
    }

    private var lastUpdateTime = 0L
    private fun initDecoder(tag: FlvTag) {
        val width = tag.width?.toInt() ?: 0
        val height = tag.height?.toInt() ?: 0
        mRender?.update(width, height)
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
    }

    private fun stop() {
        mState = State.PREPARE
        runOnUiThread {
            btDownload.setText(R.string.start_download)
            btPlayFlie.setText(R.string.start_play_file)
        }

        decoderReady = false
        mVideoDecoder?.close()
        mVideoDecoder = null
        rtmpClient?.shutdown()
        rtmpClient = null
        mFlvUnpack?.close()
        mFlvUnpack = null
        mFileOutputStream?.close()
        mFileOutputStream = null
        mPlayExecutor?.shutdown()
        mPlayExecutor = null
    }

    override fun onStop() {
        super.onStop()
        stop()
    }
}