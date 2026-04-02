package com.example.myapplication

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import com.example.myapplication.data.AppDatabase
import com.example.myapplication.sync.UploadWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import java.util.UUID
import java.util.concurrent.Executors

class MainActivity : Activity() {

    private lateinit var layoutNormal: LinearLayout
    private val dbExecutor = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        layoutNormal = findViewById(R.id.layoutNormal)

        val btnStart = findViewById<Button>(R.id.btnStart)

        btnStart.setOnClickListener {
            iniciarColeta()
        }
    }

    override fun onResume() {
        super.onResume()
        resolverFluxoAoRetornar()
    }

    private fun resolverFluxoAoRetornar() {
        dbExecutor.execute {
            val count = AppDatabase.getInstance(this).sensorReadingDao().countPending()
            val currentState = FlowStateStore.getState(this)
            val uploadAtivo = hasUploadAtivo()

            if (currentState == AppFlowState.COLLECTING && !SensorService.isRunning) {
                FlowStateStore.setState(this, AppFlowState.COLLECTION_INTERRUPTED)
            }

            runOnUiThread {
                val effectiveState = FlowStateStore.getState(this)

                when {
                    effectiveState == AppFlowState.COLLECTING && SensorService.isRunning -> {
                        abrirDataActivity("COLETA", finishCurrent = true)
                    }
                    uploadAtivo || effectiveState == AppFlowState.UPLOADING || effectiveState == AppFlowState.WAITING_WIFI -> {
                        abrirDataActivity("UPLOAD", finishCurrent = true)
                    }
                    effectiveState == AppFlowState.COLLECTION_INTERRUPTED && count > 0 -> {
                        abrirDataActivity("COLETA_INTERRUPTED", finishCurrent = true)
                    }
                    (effectiveState == AppFlowState.UPLOAD_INTERRUPTED && count > 0) || count > 0 -> {
                        abrirDataActivity("UPLOAD_INTERRUPTED", finishCurrent = true)
                    }
                    else -> {
                        layoutNormal.visibility = View.VISIBLE
                    }
                }
            }
        }
    }

    private fun hasUploadAtivo(): Boolean {
        return try {
            val infos = WorkManager.getInstance(applicationContext)
                .getWorkInfosForUniqueWork(UploadWorker.UNIQUE_WORK_NAME)
                .get()
            infos.any {
                it.state == WorkInfo.State.RUNNING ||
                    it.state == WorkInfo.State.ENQUEUED ||
                    it.state == WorkInfo.State.BLOCKED
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun abrirDataActivity(modo: String, finishCurrent: Boolean = false) {
        val intent = Intent(this, DataActivity::class.java)
        intent.putExtra("MODO", modo)
        startActivity(intent)
        if (finishCurrent) {
            finish()
        }
    }

    private fun iniciarColeta() {
        val sessionId = UUID.randomUUID().toString()

        val serviceIntent = Intent(this, SensorService::class.java)
        serviceIntent.putExtra("SESSION_ID", sessionId)
        androidx.core.content.ContextCompat.startForegroundService(this, serviceIntent)

        val dataIntent = Intent(this, DataActivity::class.java)
        dataIntent.putExtra("SESSION_ID", sessionId)
        dataIntent.putExtra("MODO", "COLETA")
        startActivity(dataIntent)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        dbExecutor.shutdown()
    }
}
