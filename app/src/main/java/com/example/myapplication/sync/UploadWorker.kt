package com.example.myapplication.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import com.example.myapplication.data.AppDatabase
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.util.Date

class UploadWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val KEY_UPLOADED = "uploaded"
        const val KEY_TOTAL = "total"
        const val KEY_COLETA = "coleta"
        const val KEY_ERROR = "error"
        const val TAG = "upload_sensor_data"
    }

    override suspend fun doWork(): Result {
        val db = AppDatabase.getInstance(applicationContext)
        val dao = db.sensorReadingDao()
        val firestore = FirebaseFirestore.getInstance()

        val totalToUpload = dao.countPending()
        if (totalToUpload == 0) {
            return Result.success(
                Data.Builder()
                    .putInt(KEY_UPLOADED, 0)
                    .putInt(KEY_TOTAL, 0)
                    .build()
            )
        }

        // Obter próximo número de coleta via transação atômica
        val coletaNum: Long
        try {
            val counterRef = firestore.collection("metadata").document("counter")
            coletaNum = firestore.runTransaction { transaction ->
                val snapshot = transaction.get(counterRef)
                val current = snapshot.getLong("nextColeta") ?: 1L
                transaction.update(counterRef, "nextColeta", current + 1)
                current
            }.await()
        } catch (e: Exception) {
            return Result.failure(
                Data.Builder()
                    .putString(KEY_ERROR, "Erro ao obter número da coleta: ${e.message}")
                    .putInt(KEY_UPLOADED, 0)
                    .putInt(KEY_TOTAL, totalToUpload)
                    .build()
            )
        }

        // Upload em batches de 500
        var uploaded = 0
        try {
            while (true) {
                val batch = dao.getNotUploaded(500)
                if (batch.isEmpty()) break

                val writeBatch = firestore.batch()
                for (reading in batch) {
                    val collection = reading.sensorType // "acelerometro" ou "giroscopio"
                    val docRef = firestore.collection(collection).document()
                    val data = hashMapOf(
                        "coleta" to coletaNum,
                        "timestamp" to Timestamp(Date(reading.timestampMillis)),
                        "x" to reading.x,
                        "y" to reading.y,
                        "z" to reading.z
                    )
                    writeBatch.set(docRef, data)
                }

                writeBatch.commit().await()
                dao.deleteByIds(batch.map { it.localId })

                uploaded += batch.size
                setProgress(
                    Data.Builder()
                        .putInt(KEY_UPLOADED, uploaded)
                        .putInt(KEY_TOTAL, totalToUpload)
                        .build()
                )
            }
        } catch (e: Exception) {
            return Result.failure(
                Data.Builder()
                    .putString(KEY_ERROR, "Erro no upload: ${e.message}")
                    .putInt(KEY_UPLOADED, uploaded)
                    .putInt(KEY_TOTAL, totalToUpload)
                    .putLong(KEY_COLETA, coletaNum)
                    .build()
            )
        }

        return Result.success(
            Data.Builder()
                .putInt(KEY_UPLOADED, uploaded)
                .putInt(KEY_TOTAL, totalToUpload)
                .putLong(KEY_COLETA, coletaNum)
                .build()
        )
    }
}
