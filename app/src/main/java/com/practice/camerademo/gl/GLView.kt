package com.practice.camerademo.gl

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.util.AttributeSet
import com.simple.rtmp.KLog
import java.nio.ByteBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * Created by zxf on 2021/5/24
 */
class GLView(context: Context, attributeSet: AttributeSet?) : GLSurfaceView(context, attributeSet) {

    constructor(context: Context) : this(context, null)

    var render: GLRender

    init {
        setEGLContextClientVersion(2)
        render = GLRender(this)
        setRenderer(render)
        renderMode = RENDERMODE_WHEN_DIRTY
    }

    class GLRender(private val glView: GLView) : Renderer {

        private var debug = false
        private var mWidth = 0
        private var mHeight = 0
        private var yBuffer: ByteBuffer? = null
        private var uBuffer: ByteBuffer? = null
        private var vBuffer: ByteBuffer? = null
        private val program = GLProgram(0)

        fun update(width: Int, height: Int) {
            if (debug) KLog.d("update window size=> w:$width h:$height")
            if (width > 0 && height > 0) {
                if (width != mWidth || height != mHeight) {
                    mWidth = width
                    mHeight = height
                    val ySize = width * height
                    yBuffer = ByteBuffer.allocate(ySize)
                    uBuffer = ByteBuffer.allocate(ySize / 4)
                    vBuffer = ByteBuffer.allocate(ySize / 4)
                }
            }
        }

        fun update(yData: ByteBuffer, uData: ByteBuffer, vData: ByteBuffer) {
            if (debug) KLog.d("update data")
            yBuffer?.clear()
            uBuffer?.clear()
            vBuffer?.clear()
            yBuffer?.put(yData)
            uBuffer?.put(uData)
            vBuffer?.put(vData)
            glView.requestRender()
        }

        override fun onDrawFrame(gl: GL10?) {
            val start = System.currentTimeMillis()
            if (yBuffer != null) {
                // reset position, have to be done
                yBuffer?.position(0)
                uBuffer?.position(0)
                vBuffer?.position(0)
                program.buildTextures(yBuffer, uBuffer, vBuffer, mWidth, mHeight)
                GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
                GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
                program.drawFrame()
            }
            if (debug) KLog.d("onDrawFrame, time = " + (System.currentTimeMillis() - start))
        }

        override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
            GLES20.glViewport(0, 0, width, height)
        }

        override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
            if (debug) KLog.d("onSurfaceCreated")
            if (!program.isProgramBuilt) {
                program.buildProgram()
            }
        }
    }
}