package com.example.myapplication

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.myapplication.data.AppDatabase
import com.example.myapplication.sync.UploadWorker
import java.util.concurrent.Executors

class DataActivity : Activity() {

    // UI - Coleta
    private lateinit var layoutColetando: LinearLayout
    private lateinit var textAccX: TextView
    private lateinit var textAccY: TextView
    private lateinit var textAccZ: TextView
    private lateinit var textGyroX: TextView
    private lateinit var textGyroY: TextView
    private lateinit var textGyroZ: TextView

    // UI - Aguardando WiFi
    private lateinit var layoutAguardandoWifi: LinearLayout

    // UI - Enviando
    private lateinit var layoutEnviando: LinearLayout
    private lateinit var progressUpload: ProgressBar
    private lateinit var textProgressInfo: TextView

    // UI - Sucesso
    private lateinit var layoutSucesso: LinearLayout
    private lateinit var textSucessoInfo: TextView

    // UI - Falha
    private lateinit var layoutFalha: LinearLayout
    private lateinit var textFalhaInfo: TextView

    private val dbExecutor = Executors.newSingleThreadExecutor()
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var totalToUpload = 0

    private val dataReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            // Acelerômetro
            if (intent?.hasExtra("ACC_X") == true) {
                val x = intent.getFloatExtra("ACC_X", 0f)
                val y = intent.getFloatExtra("ACC_Y", 0f)
                val z = intent.getFloatExtra("ACC_Z", 0f)
                textAccX.text = "X: %.2f".format(x)
                textAccY.text = "Y: %.2f".format(y)
                textAccZ.text = "Z: %.2f".format(z)
            }
            // Giroscópio
            if (intent?.hasExtra("GYRO_X") == true) {
                val x = intent.getFloatExtra("GYRO_X", 0f)
                val y = intent.getFloatExtra("GYRO_Y", 0f)
                val z = intent.getFloatExtra("GYRO_Z", 0f)
                textGyroX.text = "X: %.2f".format(x)
                textGyroY.text = "Y: %.2f".format(y)
                textGyroZ.text = "Z: %.2f".format(z)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_data)

        // Bind views
        layoutColetando = findViewById(R.id.layoutColetando)
        textAccX = findViewById(R.id.textAccX)
        textAccY = findViewById(R.id.textAccY)
        textAccZ = findViewById(R.id.textAccZ)
        textGyroX = findViewById(R.id.textGyroX)
        textGyroY = findViewById(R.id.textGyroY)
        textGyroZ = findViewById(R.id.textGyroZ)

        layoutAguardandoWifi = findViewById(R.id.layoutAguardandoWifi)

        layoutEnviando = findViewById(R.id.layoutEnviando)
        progressUpload = findViewById(R.id.progressUpload)
        textProgressInfo = findViewById(R.id.textProgressInfo)

        layoutSucesso = findViewById(R.id.layoutSucesso)
        textSucessoInfo = findViewById(R.id.textSucessoInfo)

        layoutFalha = findViewById(R.id.layoutFalha)
        textFalhaInfo = findViewById(R.id.textFalhaInfo)

        val btnStopColeta = findViewById<Button>(R.id.btnStopColeta)
        val btnVoltarInicio = findViewById<Button>(R.id.btnVoltarInicio)
        val btnTentarNovamente = findViewById<Button>(R.id.btnTentarNovamente)
        val btnDesistir = findViewById<Button>(R.id.btnDesistir)

        val modo = intent.getStringExtra("MODO") ?: "COLETA"

        if (modo == "UPLOAD") {
            // Veio da MainActivity para retomar upload
            layoutColetando.visibility = View.GONE
            pararEIniciarUpload()
        }

        btnStopColeta.setOnClickListener {
            stopService(Intent(this, SensorService::class.java))
            pararEIniciarUpload()
        }

        btnVoltarInicio.setOnClickListener {
            finish()
        }

        btnTentarNovamente.setOnClickListener {
            mostrarEstado("ENVIANDO")
            iniciarUploadWorker()
        }

        btnDesistir.setOnClickListener {
            finish()
        }
    }

    private fun pararEIniciarUpload() {
        dbExecutor.execute {
            totalToUpload = AppDatabase.getInstance(this).sensorReadingDao().countPending()
            runOnUiThread {
                if (totalToUpload == 0) {
                    textSucessoInfo.text = "Nenhum registro para enviar."
                    mostrarEstado("SUCESSO")
                    return@runOnUiThread
                }

                if (isWifiAvailable()) {
                    mostrarEstado("ENVIANDO")
                    iniciarUploadWorker()
                } else {
                    mostrarEstado("AGUARDANDO_WIFI")
                    registrarWifiCallback()
                }
            }
        }
    }

    private fun iniciarUploadWorker() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.UNMETERED)
            .build()

        val uploadRequest = OneTimeWorkRequestBuilder<UploadWorker>()
            .setConstraints(constraints)
            .addTag(UploadWorker.TAG)
            .build()

        val workManager = WorkManager.getInstance(applicationContext)
        workManager.enqueue(uploadRequest)

        // Observar progresso
        workManager.getWorkInfoByIdLiveData(uploadRequest.id).observeForever { workInfo ->
            if (workInfo == null) return@observeForever

            when (workInfo.state) {
                WorkInfo.State.RUNNING -> {
                    val uploaded = workInfo.progress.getInt(UploadWorker.KEY_UPLOADED, 0)
                    val total = workInfo.progress.getInt(UploadWorker.KEY_TOTAL, totalToUpload)
                    atualizarProgresso(uploaded, total)
                }
                WorkInfo.State.SUCCEEDED -> {
                    val outputData = workInfo.outputData
                    val uploaded = outputData.getInt(UploadWorker.KEY_UPLOADED, 0)
                    val coletaNum = outputData.getLong(UploadWorker.KEY_COLETA, -1)
                    if (coletaNum > 0) {
                        textSucessoInfo.text = "Todos os $uploaded registros foram enviados.\nColeta nº $coletaNum."
                    } else {
                        textSucessoInfo.text = "Nenhum registro para enviar."
                    }
                    mostrarEstado("SUCESSO")
                }
                WorkInfo.State.FAILED -> {
                    val outputData = workInfo.outputData
                    val error = outputData.getString(UploadWorker.KEY_ERROR) ?: "Erro desconhecido"
                    val uploaded = outputData.getInt(UploadWorker.KEY_UPLOADED, 0)
                    val total = outputData.getInt(UploadWorker.KEY_TOTAL, totalToUpload)
                    textFalhaInfo.text = "$error\n$uploaded de $total registros enviados."
                    mostrarEstado("FALHA")
                }
                else -> { /* ENQUEUED, BLOCKED, CANCELLED */ }
            }
        }
    }

    private fun atualizarProgresso(uploaded: Int, total: Int) {
        val percent = if (total > 0) (uploaded * 100) / total else 0
        progressUpload.progress = percent
        textProgressInfo.text = "$uploaded de $total registros enviados ($percent%)"
    }

    private fun mostrarEstado(estado: String) {
        layoutColetando.visibility = View.GONE
        layoutAguardandoWifi.visibility = View.GONE
        layoutEnviando.visibility = View.GONE
        layoutSucesso.visibility = View.GONE
        layoutFalha.visibility = View.GONE

        when (estado) {
            "COLETANDO" -> layoutColetando.visibility = View.VISIBLE
            "AGUARDANDO_WIFI" -> layoutAguardandoWifi.visibility = View.VISIBLE
            "ENVIANDO" -> layoutEnviando.visibility = View.VISIBLE
            "SUCESSO" -> layoutSucesso.visibility = View.VISIBLE
            "FALHA" -> layoutFalha.visibility = View.VISIBLE
        }
    }

    private fun isWifiAvailable(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    private fun registrarWifiCallback() {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()

        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                runOnUiThread {
                    mostrarEstado("ENVIANDO")
                    iniciarUploadWorker()
                }
                // Remover callback após detectar WiFi
                cm.unregisterNetworkCallback(this)
                networkCallback = null
            }
        }
        cm.registerNetworkCallback(request, networkCallback!!)
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

    override fun onDestroy() {
        super.onDestroy()
        dbExecutor.shutdown()
        // Limpar callback de rede se ainda registrado
        networkCallback?.let {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            try { cm.unregisterNetworkCallback(it) } catch (_: Exception) {}
        }
    }
}