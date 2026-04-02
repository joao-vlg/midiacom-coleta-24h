package com.example.myapplication

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.example.myapplication.data.AppDatabase
import java.util.UUID
import java.util.concurrent.Executors

class MainActivity : Activity() {

    private lateinit var layoutNormal: LinearLayout
    private lateinit var layoutPendentes: LinearLayout
    private lateinit var textPendentes: TextView
    private val dbExecutor = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        layoutNormal = findViewById(R.id.layoutNormal)
        layoutPendentes = findViewById(R.id.layoutPendentes)
        textPendentes = findViewById(R.id.textPendentes)

        val btnStart = findViewById<Button>(R.id.btnStart)
        val btnRetomarUpload = findViewById<Button>(R.id.btnRetomarUpload)
        val btnNovaColeta = findViewById<Button>(R.id.btnNovaColeta)

        btnStart.setOnClickListener {
            iniciarColeta()
        }

        btnRetomarUpload.setOnClickListener {
            val intent = Intent(this, DataActivity::class.java)
            intent.putExtra("MODO", "UPLOAD")
            startActivity(intent)
        }

        btnNovaColeta.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Descartar dados pendentes?")
                .setMessage("Existem registros de uma coleta anterior que ainda não foram enviados. Deseja descartá-los e iniciar uma nova coleta?")
                .setPositiveButton("Sim, descartar") { _, _ ->
                    dbExecutor.execute {
                        AppDatabase.getInstance(this).sensorReadingDao().deleteAll()
                        runOnUiThread { iniciarColeta() }
                    }
                }
                .setNegativeButton("Cancelar", null)
                .show()
        }
    }

    override fun onResume() {
        super.onResume()
        verificarPendentes()
    }

    private fun verificarPendentes() {
        dbExecutor.execute {
            val count = AppDatabase.getInstance(this).sensorReadingDao().countPending()
            runOnUiThread {
                if (count > 0) {
                    layoutNormal.visibility = View.GONE
                    layoutPendentes.visibility = View.VISIBLE
                    textPendentes.text = "Há $count registros de uma coleta anterior aguardando envio"
                } else {
                    layoutNormal.visibility = View.VISIBLE
                    layoutPendentes.visibility = View.GONE
                }
            }
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
    }

    override fun onDestroy() {
        super.onDestroy()
        dbExecutor.shutdown()
    }
}
