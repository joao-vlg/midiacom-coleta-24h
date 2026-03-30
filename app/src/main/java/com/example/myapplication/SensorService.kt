package com.example.myapplication

import android.app.*
import android.content.*
import android.hardware.*
import android.os.IBinder
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class SensorService : Service(), SensorEventListener {
    private lateinit var sensorManager: SensorManager
    private var accSensor: Sensor? = null // Acelerômetro
    private var gyroSensor: Sensor? = null // Giroscópio
    private var userId: String = "unknown"
    private var lastUpdateAcc: Long = 0
    private var lastUpdateGyro: Long = 0

    // Gerenciador de transmissões locais
    private lateinit var broadcaster: LocalBroadcastManager

    override fun onCreate() {
        super.onCreate()
        broadcaster = LocalBroadcastManager.getInstance(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        userId = intent?.getStringExtra("USER_ID") ?: "unknown"
        startForeground(1, createNotification())

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyroSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        accSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
        gyroSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }

        return START_STICKY
    }

    private fun createNotification(): Notification {
        val channelId = "sensor_channel"
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel =
            NotificationChannel(channelId, "Coleta Ativa", NotificationManager.IMPORTANCE_LOW)
        manager.createNotificationChannel(channel)

        return Notification.Builder(this, channelId)
            .setContentTitle("Coleta Ativa")
            .setContentText("Gravando dados para: $userId")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()
    }

    override fun onSensorChanged(event: SensorEvent) {
        val now = System.currentTimeMillis()

        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            if (now - lastUpdateAcc >= 10000) {
                saveToFile("ACC", event.values, now)
                lastUpdateAcc = now

                val intent = Intent("SENSOR_DATA")
                intent.putExtra("ACC_X", event.values[0])
                intent.putExtra("ACC_Y", event.values[1])
                intent.putExtra("ACC_Z", event.values[2])
                broadcaster.sendBroadcast(intent)
            }
        } else if (event.sensor.type == Sensor.TYPE_GYROSCOPE) {
            if (now - lastUpdateGyro >= 10000) {
                saveToFile("GYRO", event.values, now)
                lastUpdateGyro = now
            }
        }
    }

    private fun saveToFile(type: String, values: FloatArray, ts: Long) {
        val date = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date(ts))
        val line = "$date,${values[0]},${values[1]},${values[2]}\n"
        try {
            val file = File(getExternalFilesDir(null), "${userId}_$type.csv")
            file.appendText(line)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onBind(i: Intent?): IBinder? = null
    override fun onAccuracyChanged(s: Sensor?, a: Int) {}
}