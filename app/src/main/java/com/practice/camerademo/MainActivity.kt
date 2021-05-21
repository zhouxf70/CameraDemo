package com.practice.camerademo

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.practice.camerademo.camera.CameraActivity
import com.practice.camerademo.player.PlayerActivity
import java.nio.ByteBuffer

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.bt_camera).setOnClickListener {
            val intent = Intent(this, CameraActivity::class.java)
            startActivity(intent)
        }

        findViewById<Button>(R.id.bt_player).setOnClickListener {
            val intent = Intent(this, PlayerActivity::class.java)
            startActivity(intent)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermission()
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun requestPermission() {
        val permissions = arrayOf(
            Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO
        )
        val list = ArrayList<String>()
        for (i in permissions.indices) {
            if (checkSelfPermission(permissions[i]) != PackageManager.PERMISSION_GRANTED) list.add(permissions[i])
            else KLog.d("has permission: ${permissions[i]}")
        }
        if (list.size > 0) requestPermissions(list.toArray(emptyArray()), 0)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        for (i in permissions.indices) {
            KLog.d("request ${permissions[i]} ,result: ${grantResults[i] == PackageManager.PERMISSION_GRANTED}")
        }
    }
}