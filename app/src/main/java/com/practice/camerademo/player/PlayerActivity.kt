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
import com.practice.camerademo.image.ImageUtils
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

    private var mFileInputStream: FileInputStream? = null
    private var mFileOutputStream: FileOutputStream? = null
    private var mFlvUnpack: FlvUnpack? = null
    private var mVideoCodec: MediaCodecWrap? = null

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
//        texture.setOnFrameAvailableListener {  }
//        texture.setDefaultBufferSize(1920, 1080)
        val surface = Surface(texture)
        mVideoCodec = MediaCodecWrap(MediaCodecWrap.AVC, 1920, 1080, encoder = false, decodeSurface = surface)
//        mVideoCodec = MediaCodecWrap(MediaCodecWrap.AVC, 1920, 1080, encoder = false).apply {
//            start { byteBuffer, _ ->
//                val byteArray = ByteArray(byteBuffer.remaining())
//                byteBuffer.get(byteArray)
//                val picFile = File(filesDir, "decoded_pic_${System.currentTimeMillis()}.jpg")
//                ImageUtils.toPicture(byteArray, picFile, needAlign = true)
//            }
//        }
    }

    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.bt_start_download -> {
                if (mState == State.PREPARE) {
                    mState = State.DOWNLOADING
                    btDownload.setText(R.string.stop)
                    startDownload()
                } else {
                    btDownload.setText(R.string.start_download)
                    rtmpClient?.shutdown()
                }
            }
            R.id.bt_start_play_file -> {
                if (mState == State.PREPARE) {
                    mState = State.PLAYING
                    btPlayFlie.setText(R.string.stop)
                    startPlayFile()
                } else {
                    btPlayFlie.setText(R.string.start_play_file)
                    mFlvUnpack?.close()
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
        val file = File(filesDir, "my_flv.flv")
        mFileInputStream = FileInputStream(file)

//        val avcFile = File(filesDir, "my_flv_avc")
//        mFileOutputStream = FileOutputStream(avcFile)

        mFlvUnpack = FlvUnpack().apply {
            callback = object : FlvUnpack.Callback {
                override fun onFlvTagAvailable(tag: FlvTag?) {
                    if (tag == null) {
                        stop()
                        return
                    }
                    when (tag.type) {
                        FlvConst.FLV_DATA_TYPE_SCRIPT -> Unit
                        FlvConst.FLV_DATA_TYPE_AUDIO -> Unit
                        FlvConst.FLV_DATA_TYPE_VIDEO -> {
                            mVideoCodec?.inputData(tag.data!!, tag.timeStamp.toLong())
                        }
                    }
                }
            }
        }
        Thread {
            mFlvUnpack?.start(mFileInputStream!!)
        }.start()
    }

    private fun stop() {
        mState = State.PREPARE
        runOnUiThread {
            btDownload.setText(R.string.start_download)
            btPlayFlie.setText(R.string.start_play_file)
        }

        rtmpClient?.shutdown()
        rtmpClient = null
        mFlvUnpack?.close()
        mFlvUnpack = null
        mFileOutputStream?.close()
        mFileOutputStream = null
    }

    override fun onStop() {
        super.onStop()
        rtmpClient?.shutdown()
        mFlvUnpack?.close()
        mFileOutputStream?.close()
    }
}