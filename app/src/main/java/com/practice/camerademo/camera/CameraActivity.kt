package com.practice.camerademo.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.view.*
import android.widget.Button
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.practice.camerademo.KLog
import com.practice.camerademo.R
import com.practice.camerademo.flv.FlvPacket
import com.practice.camerademo.image.ImageUtils
import com.practice.camerademo.publish.DefaultRtmpPublisher
import kotlinx.android.synthetic.main.activity_camera.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.File

@RequiresApi(Build.VERSION_CODES.N)
class CameraActivity : AppCompatActivity(), View.OnClickListener {

    private var state = CaptureState.PREVIEW
    private var mBackgroundHandler: Handler? = null
    private var mBackgroundThread: HandlerThread? = null
    private lateinit var mTextureView: TextureView
    private var mImageReader: ImageReader? = null
    private var mCameraDevice: CameraDevice? = null
    private var mCameraSession: CameraCaptureSession? = null
    private var mVideoEncoder: VideoEncoder? = null
    private var mFlvPacket: FlvPacket? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportRequestWindowFeature(Window.FEATURE_NO_TITLE)
        window.setFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON, WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_camera)
        findViewById<Button>(R.id.bt_photo).setOnClickListener(this)
        findViewById<Button>(R.id.bt_gif).setOnClickListener(this)
        findViewById<Button>(R.id.bt_record).setOnClickListener(this)
        findViewById<Button>(R.id.bt_push).setOnClickListener(this)
        mTextureView = findViewById(R.id.texture)
    }

    override fun onResume() {
        super.onResume()
        startBackgroundThread()
        if (mTextureView.isAvailable) initCamera()
        else mTextureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) = Unit
            override fun onSurfaceTextureDestroyed(texture: SurfaceTexture) = true
            override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit
            override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) = initCamera()
        }
    }

    override fun onPause() {
        KLog.d("onPause")
        releaseCamera()
        stopBackgroundThread()
        super.onPause()
    }

    private fun initCamera() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) return
        val cameraManager: CameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraId = "" + CameraCharacteristics.LENS_FACING_FRONT
        cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
            override fun onOpened(camera: CameraDevice) {
                KLog.t(camera)
                mCameraDevice = camera
                startSession()
            }

            override fun onDisconnected(camera: CameraDevice) = KLog.d("onDisconnected")
            override fun onError(camera: CameraDevice, error: Int) = KLog.e(error)
        }, mBackgroundHandler)
    }

    private fun startSession() {
        // preview surface
        val texture = mTextureView.surfaceTexture
        texture.setDefaultBufferSize(1920, 1080)
        val previewSurface = Surface(texture)

        // photo surface
        mImageReader = ImageReader.newInstance(1920, 1080, ImageFormat.YUV_420_888, 2)
        mImageReader!!.setOnImageAvailableListener({ processImage(it) }, mBackgroundHandler)

        val surface = if (state == CaptureState.PREVIEW) mImageReader?.surface!!
        else mVideoEncoder?.encoderSurface!!

        mCameraDevice?.apply {
            createCaptureSession(arrayListOf(previewSurface, surface), object : CameraCaptureSession.StateCallback() {
                override fun onConfigureFailed(session: CameraCaptureSession) = Unit
                override fun onActive(session: CameraCaptureSession) = KLog.d("onActive")
                override fun onClosed(session: CameraCaptureSession) = KLog.d("onClosed")
                override fun onReady(session: CameraCaptureSession) = KLog.d("onReady")
                override fun onConfigured(session: CameraCaptureSession) {
                    mCameraSession = session
                    val captureRequest = createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).run {
                        set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                        addTarget(previewSurface)
                        addTarget(surface)
                        build()
                    }
                    mCameraSession?.setRepeatingRequest(captureRequest, null, mBackgroundHandler)
                }
            }, mBackgroundHandler)
        }
    }

    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.bt_photo -> if (state == CaptureState.PREVIEW) state = CaptureState.PHOTO
            R.id.bt_gif -> if (state == CaptureState.PREVIEW) state = CaptureState.GIF
            R.id.bt_record -> startRecord()
            R.id.bt_push -> startPublish()
        }
    }

    private fun startPublish() {
        if (state == CaptureState.PREVIEW) {
            state = CaptureState.PUBLISH
            bt_push.setText(R.string.stop)
            val publisher = DefaultRtmpPublisher("rtmp://192.168.137.1:1935/live?livestream")
            GlobalScope.launch {
                if (publisher.connect())
                    mFlvPacket = FlvPacket(1080, 1920).also {
                        it.publish(publisher)
                        startEncode()
                    }
            }
        } else if (state == CaptureState.PUBLISH) {
            state = CaptureState.PREVIEW
            bt_push.setText(R.string.push)
            mVideoEncoder?.stop()
            mFlvPacket?.stop()
        }
    }

    private fun startRecord() {
        mCameraSession?.close()
        if (state == CaptureState.PREVIEW) {
            state = CaptureState.RECORD
            bt_record.setText(R.string.stop)
            val file = File(filesDir, "my_flv.flv")
            mFlvPacket = FlvPacket(1080, 1920).also {
                it.start(file)
                startEncode()
            }
        } else {
            state = CaptureState.PREVIEW
            startSession()
            bt_record.setText(R.string.record)
            mVideoEncoder?.stop()
            mFlvPacket?.stop()
            mFlvPacket = null
        }
    }

    private fun startEncode() {
        mVideoEncoder = VideoEncoder(VideoEncoder.AVC, 1080, 1920) { output, info ->
            KLog.d("onEncodedDataAvailable ${output.remaining()}")
            mFlvPacket?.writeVideoFrame(output, info)
        }
        mVideoEncoder?.start {
            startSession()
        }
    }

    private fun processImage(reader: ImageReader) {
        val image = reader.acquireLatestImage() ?: return
        when (state) {
            CaptureState.PREVIEW -> Unit
            CaptureState.PHOTO -> {
                state = CaptureState.PREVIEW
                takePhoto(ImageUtils.getDataFromYUV(image))
            }
            CaptureState.GIF -> {
                gif.add(ImageUtils.getDataFromYUV(image))
                if (gif.size == GIF_PICTURE_SIZE) {
                    state = CaptureState.PREVIEW
                    takeGif()
                }
            }
            else -> Unit
        }
        image.close()
    }

    private val gif = ArrayList<ByteArray>(GIF_PICTURE_SIZE)
    private fun takeGif() {
        Thread {
            val file = File(filesDir, "my_gif.gif").also { KLog.d(it.absolutePath) }
            ImageUtils.toGif(gif, file)
            toast("save gif:${file.absolutePath}")
            gif.clear()
        }.start()
    }

    private fun takePhoto(nv21: ByteArray) {
        Thread {
            val file = File(filesDir, "pic.jpg").also { KLog.d(it.absolutePath) }
            ImageUtils.toPicture(nv21, file)
            toast("save picture:${file.absolutePath}")
        }.start()
    }

    private fun startBackgroundThread() {
        mBackgroundThread = HandlerThread("camera")
        mBackgroundThread?.let {
            it.start()
            mBackgroundHandler = Handler(it.looper)
        }
    }

    private fun stopBackgroundThread() {
        mBackgroundThread?.run {
            quit()
            try {
                mBackgroundThread?.join()
                mBackgroundThread = null
                mBackgroundHandler = null
            } catch (e: InterruptedException) {
                KLog.e(e)
            }
        }
    }

    private fun toast(msg: String) {
        runOnUiThread {
            Toast.makeText(this@CameraActivity, msg, Toast.LENGTH_SHORT).show()
        }
    }

    private fun releaseCamera() {
        mCameraDevice?.close()
        mCameraDevice = null
        mCameraSession?.close()
        mCameraSession = null
        mImageReader?.close()
        mImageReader = null
        mVideoEncoder?.close()
        mVideoEncoder = null
        mFlvPacket?.stop()
        mFlvPacket = null
    }

    companion object {
        const val GIF_PICTURE_SIZE = 10
    }

}