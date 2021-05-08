package com.practice.camerademo.player

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import com.practice.camerademo.KLog
import com.practice.camerademo.R
import com.simple.rtmp.DefaultRtmpClient
import com.simple.rtmp.RtmpClient
import com.simple.rtmp.output.FlvWriter
import com.simple.rtmp.output.RtmpStreamWriter
import java.io.File
import java.io.FileOutputStream

class PlayerActivity : AppCompatActivity(), View.OnClickListener {

    private lateinit var etUrl: EditText
    private var rtmpClient: RtmpClient? = null
    private lateinit var rtmpWriter: RtmpStreamWriter
    private val playUrl = "rtmp://192.168.137.1:1935/live?livestream"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)
        findViewById<Button>(R.id.bt_start).setOnClickListener(this)
        findViewById<Button>(R.id.bt_stop).setOnClickListener(this)
        etUrl = findViewById(R.id.et_url)
        etUrl.setText(playUrl)
        val file = File(filesDir, "rtmp.flv")
        rtmpWriter = FlvWriter(FileOutputStream(file))
    }

    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.bt_start -> {
                Thread {
                    KLog.d("play")
                    rtmpClient = DefaultRtmpClient(playUrl).apply {
                        connect()
                        play(rtmpWriter)
                    }
                }.start()
            }
            R.id.bt_stop -> {
                rtmpClient?.shutdown()
            }
        }
    }
}