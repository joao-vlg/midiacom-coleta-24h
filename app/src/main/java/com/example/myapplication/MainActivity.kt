package com.example.myapplication

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.core.app.ActivityCompat

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val editUserId = findViewById<EditText>(R.id.editUserId)
        val btnStart = findViewById<Button>(R.id.btnStart)
        val btnStop = findViewById<Button>(R.id.btnStop)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }

        btnStart.setOnClickListener {
            val userId = editUserId.text.toString()
            if (userId.isNotEmpty()) {
                val serviceIntent = Intent(this, SensorService::class.java)
                serviceIntent.putExtra("USER_ID", userId)
                startForegroundService(serviceIntent)

                val dataIntent = Intent(this, DataActivity::class.java)
                dataIntent.putExtra("USER_ID", userId) // Passa o ID para a nova tela também
                startActivity(dataIntent)

            } else {
                Toast.makeText(this, "Digite um ID!", Toast.LENGTH_SHORT).show()
            }
        }

        btnStop.setOnClickListener {
            stopService(Intent(this, SensorService::class.java))
        }
    }
}