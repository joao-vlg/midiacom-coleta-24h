package com.example.myapplication.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface SensorReadingDao {

    @Insert
    fun insert(reading: SensorReading)

    @Query("SELECT * FROM sensor_readings ORDER BY localId ASC LIMIT :limit")
    fun getNotUploaded(limit: Int = 500): List<SensorReading>

    @Query("SELECT COUNT(*) FROM sensor_readings")
    fun countPending(): Int

    @Query("DELETE FROM sensor_readings WHERE localId IN (:ids)")
    fun deleteByIds(ids: List<Long>)

    @Query("DELETE FROM sensor_readings")
    fun deleteAll()
}
