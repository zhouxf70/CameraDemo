package com.practice.camerademo.player

import android.graphics.SurfaceTexture
import android.os.Build
import android.os.Bundle
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.widget.Button
import android.widget.EditText
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.practice.camerademo.KLog
import com.practice.camerademo.R
import com.practice.camerademo.camera.MediaCodecWrap
import com.practice.camerademo.flv.FlvConst
import com.practice.camerademo.flv.FlvTag
import com.practice.camerademo.flv.FlvUnpack
import com.simple.rtmp.DefaultRtmpClient
import com.simple.rtmp.RtmpClient
import com.simple.rtmp.output.FlvWriter
import com.simple.rtmp.output.RtmpStreamWriter
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

@RequiresApi(Build.VERSION_CODES.N)
class PlayerActivity : AppCompatActivity(), View.OnClickListener {

    private lateinit var etUrl: EditText
    private lateinit var btDownload: Button
    private lateinit var btPlayFlie: Button
    private lateinit var mTextureView: TextureView

    private var mState = State.PREPARE
    private val rtmpUrl = "rtmp://192.168.137.1:1935/live?livestream"
    private var rtmpClient: RtmpClient? = null
    private lateinit var rtmpWriter: RtmpStreamWriter

    private var fileInputStream: FileInputStream? = null
    private var flvUnpack: FlvUnpack? = null
    private var videoCodec: MediaCodecWrap? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        etUrl = findViewById(R.id.et_url)
        etUrl.setText(rtmpUrl)
        btDownload = findViewById<Button>(R.id.bt_start_download).apply { setOnClickListener(this@PlayerActivity) }
        btPlayFlie = findViewById<Button>(R.id.bt_start_play_file).apply { setOnClickListener(this@PlayerActivity) }
        mTextureView = findViewById<TextureView>(R.id.surface).apply {
            surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) = Unit
                override fun onSurfaceTextureDestroyed(texture: SurfaceTexture) = true
                override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit
                override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) = initCodec()
            }
        }
    }

    private fun initCodec() {
        val texture = mTextureView.surfaceTexture
        texture.setDefaultBufferSize(1080, 1920)
        val surface = Surface(texture)
        videoCodec = MediaCodecWrap(MediaCodecWrap.AVC, 1080, 1920, encoder = false).apply {
            start { byteBuffer, _ ->
                KLog.d(byteBuffer.remaining())
            }
        }
    }

    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.bt_start_download -> {
                if (mState == State.PREPARE) {
                    btDownload.setText(R.string.stop)
                    startDownload()
                } else {
                    btDownload.setText(R.string.start_download)
                    rtmpClient?.shutdown()
                }
            }
            R.id.bt_start_play_file -> {
                if (mState == State.PREPARE) {
                    btPlayFlie.setText(R.string.stop)
                    startPlayFile()
                } else {
                    btPlayFlie.setText(R.string.start_play_file)
                    flvUnpack?.close()
                }
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
        KLog.t("start play flv")
        val file = File(filesDir, "rtmp.flv")
        fileInputStream = FileInputStream(file)
        flvUnpack = FlvUnpack().apply {
            callback = object : FlvUnpack.Callback {
                override fun onFlvTagAvailable(tag: FlvTag) {
                    when (tag.type) {
                        FlvConst.FLV_DATA_TYPE_SCRIPT -> Unit
                        FlvConst.FLV_DATA_TYPE_AUDIO -> Unit
                        FlvConst.FLV_DATA_TYPE_VIDEO -> videoCodec?.inputData(tag.data!!, tag.timeStamp.toLong())
                    }
                }
            }
        }
        Thread {
            flvUnpack?.start(fileInputStream!!)
        }.start()
    }
}