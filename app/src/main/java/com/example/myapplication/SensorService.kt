package com.example.myapplication

import android.app.*
import android.content.*
import android.hardware.*
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.myapplication.data.AppDatabase
import com.example.myapplication.data.SensorReading
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors

class SensorService : Service(), SensorEventListener {
    private lateinit var sensorManager: SensorManager
    private var accSensor: Sensor? = null
    private var gyroSensor: Sensor? = null
    private var sessionId: String = "unknown"
    private var lastUpdateAcc: Long = 0
    private var lastUpdateGyro: Long = 0

    private lateinit var broadcaster: LocalBroadcastManager
    private lateinit var dao: com.example.myapplication.data.SensorReadingDao
    private val dbExecutor = Executors.newSingleThreadExecutor()

    override fun onCreate() {
        super.onCreate()
        broadcaster = LocalBroadcastManager.getInstance(this)
        dao = AppDatabase.getInstance(this).sensorReadingDao()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        sessionId = intent?.getStringExtra("SESSION_ID") ?: "unknown"
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(
                Context.NOTIFICATION_SERVICE
            ) as NotificationManager
            val channel = NotificationChannel(
                channelId,
                "Coleta Ativa",
                NotificationManager.IMPORTANCE_LOW
            )
            manager.createNotificationChannel(channel)
        }

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Coleta Ativa")
            .setContentText("Gravando dados da coleta")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onSensorChanged(event: SensorEvent) {
        val now = System.currentTimeMillis()

        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            if (now - lastUpdateAcc >= 10000) {
                val x = event.values[0].toDouble()
                val y = event.values[1].toDouble()
                val z = event.values[2].toDouble()

                // Salvar no CSV (backup)
                saveToFile("ACC", event.values, now)

                // Salvar no Room (para upload)
                dbExecutor.execute {
                    dao.insert(
                        SensorReading(
                            sessionId = sessionId,
                            sensorType = "acelerometro",
                            timestampMillis = now,
                            x = x, y = y, z = z
                        )
                    )
                }

                // Broadcast para DataActivity
                val intent = Intent("SENSOR_DATA")
                intent.putExtra("ACC_X", x.toFloat())
                intent.putExtra("ACC_Y", y.toFloat())
                intent.putExtra("ACC_Z", z.toFloat())
                broadcaster.sendBroadcast(intent)

                lastUpdateAcc = now
            }
        } else if (event.sensor.type == Sensor.TYPE_GYROSCOPE) {
            if (now - lastUpdateGyro >= 10000) {
                val x = event.values[0].toDouble()
                val y = event.values[1].toDouble()
                val z = event.values[2].toDouble()

                // Salvar no CSV (backup)
                saveToFile("GYRO", event.values, now)

                // Salvar no Room (para upload)
                dbExecutor.execute {
                    dao.insert(
                        SensorReading(
                            sessionId = sessionId,
                            sensorType = "giroscopio",
                            timestampMillis = now,
                            x = x, y = y, z = z
                        )
                    )
                }

                // Broadcast para DataActivity (GYRO)
                val intent = Intent("SENSOR_DATA")
                intent.putExtra("GYRO_X", x.toFloat())
                intent.putExtra("GYRO_Y", y.toFloat())
                intent.putExtra("GYRO_Z", z.toFloat())
                broadcaster.sendBroadcast(intent)

                lastUpdateGyro = now
            }
        }
    }

    private fun saveToFile(type: String, values: FloatArray, ts: Long) {
        val date = SimpleDateFormat(
            "yyyy-MM-dd HH:mm:ss",
            Locale.getDefault()
        ).format(Date(ts))
        val line = "$date,${values[0]},${values[1]},${values[2]}\n"
        try {
            val file = File(getExternalFilesDir(null), "${sessionId}_$type.csv")
            file.appendText(line)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(this)
        dbExecutor.shutdown()
    }

    override fun onBind(i: Intent?): IBinder? = null
    override fun onAccuracyChanged(s: Sensor?, a: Int) {}
}