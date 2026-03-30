package com.example.myapplication

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.localbroadcastmanager.content.LocalBroadcastManager

class DataActivity : Activity() {

    private lateinit var textUserId: TextView
    private lateinit var textAccX: TextView
    private lateinit var textAccY: TextView
    private lateinit var textAccZ: TextView
    private val dataReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val x = intent?.getFloatExtra("ACC_X", 0f) ?: 0f
            val y = intent?.getFloatExtra("ACC_Y", 0f) ?: 0f
            val z = intent?.getFloatExtra("ACC_Z", 0f) ?: 0f

            textAccX.text = "X: %.2f".format(x)
            textAccY.text = "Y: %.2f".format(y)
            textAccZ.text = "Z: %.2f".format(z)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_data)

        textUserId = findViewById(R.id.textUserId)
        textAccX = findViewById(R.id.textAccX)
        textAccY = findViewById(R.id.textAccY)
        textAccZ = findViewById(R.id.textAccZ)
        val btnStop = findViewById<Button>(R.id.btnStopColeta)

        val userId = intent.getStringExtra("USER_ID") ?: "unknown"
        textUserId.text = "Usuário: $userId"

        btnStop.setOnClickListener {
            stopService(Intent(this, SensorService::class.java))
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        LocalBroadcastManager.getInstance(this)
            .registerReceiver(dataReceiver, IntentFilter("SENSOR_DATA"))
    }

    override fun onPause() {
        super.onPause()
        LocalBroadcastManager.getInstance(this)
            .unregisterReceiver(dataReceiver)
    }
}