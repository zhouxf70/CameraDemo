package com.practice.camerademo.camera

import android.Manifest
import android.annotation.SuppressLint
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
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.Window
import android.widget.Button
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.practice.camerademo.KLog
import com.practice.camerademo.R
import com.practice.camerademo.flv.FlvPacket
import com.practice.camerademo.image.ImageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.nio.ByteBuffer

@RequiresApi(Build.VERSION_CODES.N)
class CameraActivity : AppCompatActivity(), View.OnClickListener {

    private var state = CaptureState.PREVIEW
    private var mBackgroundHandler: Handler? = null
    private var mBackgroundThread: HandlerThread? = null
    private lateinit var mTextureView: TextureView
    private var mImageReader: ImageReader? = null
    private var mCameraDevice: CameraDevice? = null
    private var mCameraSession: CameraCaptureSession? = null
    private var cameraSessionValid = false
    private var mMediaEncoder: MediaCodecWrap? = null
    private var mMediaDecoder: MediaCodecWrap? = null
    private var mFlvPacket: FlvPacket? = null
    private var mFileOutputStream: OutputStream? = null

    private lateinit var btRecord: Button
    private lateinit var btPublish: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportRequestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_camera)

        requestPermission()
        findViewById<Button>(R.id.bt_photo).setOnClickListener(this)
        findViewById<Button>(R.id.bt_gif).setOnClickListener(this)
        btRecord = findViewById<Button>(R.id.bt_record).apply { setOnClickListener(this@CameraActivity) }
        btPublish = findViewById<Button>(R.id.bt_push).apply { setOnClickListener(this@CameraActivity) }
        mTextureView = findViewById(R.id.texture)
    }

    override fun onResume() {
        super.onResume()
        startBackgroundThread()
        if (mTextureView.isAvailable) initCamera()
        else
            mTextureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) = Unit
                override fun onSurfaceTextureDestroyed(texture: SurfaceTexture) = true
                override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit
                override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) = initCamera()
            }
    }

    override fun onPause() {
        KLog.d("onPause")
        imgJob?.run {
            if (isActive) cancel()
        }
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
        cameraSessionValid = false
        mCameraSession?.close()

        // preview surface
        val texture = mTextureView.surfaceTexture
        texture.setDefaultBufferSize(1920, 1080)
        val previewSurface = Surface(texture)

        // photo surface
        mImageReader = ImageReader.newInstance(1920, 1080, ImageFormat.YUV_420_888, 2)
        mImageReader!!.setOnImageAvailableListener({ processImage(it) }, mBackgroundHandler)

        val surface =
            if (state == CaptureState.RECORD && mMediaEncoder?.encoderSurface != null)
                mMediaEncoder?.encoderSurface!!
            else
                mImageReader?.surface!!

        mCameraDevice?.apply {
            createCaptureSession(arrayListOf(previewSurface, surface), object : CameraCaptureSession.StateCallback() {
                override fun onConfigureFailed(session: CameraCaptureSession) = Unit
                override fun onActive(session: CameraCaptureSession) = KLog.d("onActive")
                override fun onClosed(session: CameraCaptureSession) = KLog.d("onClosed")
                override fun onReady(session: CameraCaptureSession) = KLog.d("onReady")
                override fun onSurfacePrepared(session: CameraCaptureSession, surface: Surface) = KLog.d("onSurfacePrepared")
                override fun onCaptureQueueEmpty(session: CameraCaptureSession) = KLog.d("onCaptureQueueEmpty")
                override fun onConfigured(session: CameraCaptureSession) {
                    mCameraSession = session
                    cameraSessionValid = true
                    val captureRequest = createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).run {
                        set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                        addTarget(previewSurface)
                        addTarget(surface)
                        build()
                    }
                    mCameraSession?.setRepeatingRequest(captureRequest, object : CameraCaptureSession.CaptureCallback() {
                        override fun onCaptureStarted(session: CameraCaptureSession, request: CaptureRequest, timestamp: Long, frameNumber: Long) = Unit
                        override fun onCaptureProgressed(session: CameraCaptureSession, request: CaptureRequest, partialResult: CaptureResult) = Unit
                        override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
//                            KLog.t("onCaptureCompleted $request")
                        }
                    }, mBackgroundHandler)
                }
            }, mBackgroundHandler)
        }
    }

    @SuppressLint("SetTextI18n")
    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.bt_photo -> if (state == CaptureState.PREVIEW) state = CaptureState.PHOTO
            R.id.bt_gif -> if (state == CaptureState.PREVIEW) state = CaptureState.GIF
            R.id.bt_record ->
                if (state == CaptureState.PREVIEW) {
                    state = CaptureState.RECORD
                    btRecord.text = "Stop"
                    val file = File(filesDir, "my_flv.flv")
                    val avcFile = File(filesDir, "acv").apply {
                        if (!exists()) createNewFile().also { print("create") }
                    }
                    mFlvPacket = FlvPacket().also { it.start(file) }
                    mMediaEncoder = MediaCodecWrap(MediaCodecWrap.AVC, 1920, 1080, true, encoderBySurface = true)
                    mMediaEncoder?.start { output, bufferInfo ->
                        KLog.d("onEncodedDataAvailable ${output.remaining()}")
                        writeAvcToFile(output, avcFile)
                        mFlvPacket?.writeVideoFrame(output, bufferInfo)
                    }
                    startSession()
                } else {
                    state = CaptureState.PREVIEW
                    btRecord.text = "录制"
                    startSession()
                    mMediaEncoder?.stop()
                    mFlvPacket?.stop()
                    mFileOutputStream?.close()
                }
            R.id.bt_push -> Unit
        }
    }

    private fun writeAvcToFile(output: ByteBuffer, avcFile: File) {
        if (mFileOutputStream == null)
            mFileOutputStream = FileOutputStream(avcFile)
        val byteArray = ByteArray(output.remaining())
        for (i in byteArray.indices) {
            byteArray[i] = output.get(i)
        }
        mFileOutputStream?.write(byteArray)
    }

    private var imgJob: Job? = null
    private var lastCaptureTime = 0L
    private fun processImage(reader: ImageReader) {
//        KLog.d("processImage, state = $state")
        val image = reader.acquireLatestImage() ?: return
        if (!cameraSessionValid) {
            image.close()
            return
        }
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
            CaptureState.RECORD -> {
                val dataFromYUV = ImageUtils.getDataFromYUV(image).also { KLog.d("before encode : ${it.size}") }
                mMediaEncoder?.inputData(dataFromYUV)
            }
            CaptureState.PUBLISH -> Unit
        }
        image.close()
    }

    private val gif = ArrayList<ByteArray>(GIF_PICTURE_SIZE)
    private fun takeGif() {
        imgJob = GlobalScope.launch(Dispatchers.IO) {
            val file = File(filesDir, "my_gif.gif").also { KLog.d(it.absolutePath) }
            ImageUtils.toGif(gif, file)
            toast("save gif:${file.absolutePath}")
            gif.clear()
        }
    }

    private fun takePhoto(nv21: ByteArray) {
        imgJob = GlobalScope.launch(Dispatchers.IO) {
            val file = File(filesDir, "pic.jpg").also { KLog.d(it.absolutePath) }
            ImageUtils.toPicture(nv21, file)
            toast("save picture:${file.absolutePath}")
        }
    }

    private fun testDataValid() {
        mMediaDecoder = MediaCodecWrap(MediaCodecWrap.AVC, 1920, 1080, false)
        mMediaDecoder!!.start { output, _ ->
            val size = output.remaining()
            KLog.d("after decoder : $size")
            val file = File(filesDir, "pic_${System.currentTimeMillis()}.jpg").also { KLog.d(it.absolutePath) }
            val byteArray = ByteArray(size)
            output.get(byteArray)
            ImageUtils.toPicture(byteArray, file, needAlign = true)
        }
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

    private fun requestPermission() {
        val permissions = arrayOf(
            Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO
        )
        val list = ArrayList<String>()
        for (i in permissions.indices) {
            if (checkSelfPermission(permissions[i]) != PackageManager.PERMISSION_GRANTED)
                list.add(permissions[i])
            else
                KLog.d("has permission: ${permissions[i]}")
        }
        if (list.size > 0)
            requestPermissions(list.toArray(emptyArray()), 0)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        for (i in permissions.indices) {
            KLog.d("request ${permissions[i]} ,result: ${grantResults[i] == PackageManager.PERMISSION_GRANTED}")
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
        mMediaEncoder?.close()
        mMediaEncoder = null
        mMediaDecoder?.close()
        mMediaDecoder = null
        mFlvPacket?.stop()
        mFlvPacket = null
        imgJob?.run {
            if (isActive) cancel()
        }
    }

    companion object {
        const val GIF_PICTURE_SIZE = 10
    }

}