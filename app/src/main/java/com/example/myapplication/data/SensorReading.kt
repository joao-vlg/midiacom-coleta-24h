package com.example.myapplication.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sensor_readings")
data class SensorReading(
    @PrimaryKey(autoGenerate = true) val localId: Long = 0,
    val sessionId: String,      // UUID local da sessão
    val sensorType: String,     // "acelerometro" ou "giroscopio"
    val timestampMillis: Long,  // millis desde epoch
    val x: Double,
    val y: Double,
    val z: Double
)
