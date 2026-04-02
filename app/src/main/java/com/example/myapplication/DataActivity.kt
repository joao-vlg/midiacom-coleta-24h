package com.example.myapplication

import android.app.Activity
import android.app.AlertDialog
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
import androidx.lifecycle.Observer
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.myapplication.data.AppDatabase
import com.example.myapplication.sync.UploadWorker
import java.util.UUID
import java.util.concurrent.Executors

class DataActivity : Activity() {

    // UI - Coleta
    private lateinit var layoutColetando: LinearLayout
    private lateinit var textAcc: TextView
    private lateinit var textGyro: TextView

    // UI - Aguardando WiFi
    private lateinit var layoutAguardandoWifi: LinearLayout

    // UI - Enviando
    private lateinit var layoutEnviando: LinearLayout
    private lateinit var progressUpload: ProgressBar
    private lateinit var textProgressInfo: TextView

    // UI - Sucesso
    private lateinit var layoutSucesso: LinearLayout
    private lateinit var textSucessoInfo: TextView

    // UI - Coleta interrompida
    private lateinit var layoutColetaInterrompida: LinearLayout

    // UI - Upload interrompido
    private lateinit var layoutUploadInterrompido: LinearLayout
    private lateinit var textUploadInterrompidoInfo: TextView

    private val dbExecutor = Executors.newSingleThreadExecutor()
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var totalToUpload = 0
    private var uploadWorkObserver: Observer<List<WorkInfo>>? = null

    private val dataReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.hasExtra("ACC_X") == true) {
                val x = intent.getFloatExtra("ACC_X", 0f)
                val y = intent.getFloatExtra("ACC_Y", 0f)
                val z = intent.getFloatExtra("ACC_Z", 0f)
                textAcc.text = "ACC  X %.2f  Y %.2f  Z %.2f".format(x, y, z)
            }

            if (intent?.hasExtra("GYRO_X") == true) {
                val x = intent.getFloatExtra("GYRO_X", 0f)
                val y = intent.getFloatExtra("GYRO_Y", 0f)
                val z = intent.getFloatExtra("GYRO_Z", 0f)
                textGyro.text = "GYR  X %.2f  Y %.2f  Z %.2f".format(x, y, z)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_data)

        // Bind views
        layoutColetando = findViewById(R.id.layoutColetando)
        textAcc = findViewById(R.id.textAcc)
        textGyro = findViewById(R.id.textGyro)

        layoutAguardandoWifi = findViewById(R.id.layoutAguardandoWifi)

        layoutEnviando = findViewById(R.id.layoutEnviando)
        progressUpload = findViewById(R.id.progressUpload)
        textProgressInfo = findViewById(R.id.textProgressInfo)

        layoutSucesso = findViewById(R.id.layoutSucesso)
        textSucessoInfo = findViewById(R.id.textSucessoInfo)

        layoutColetaInterrompida = findViewById(R.id.layoutColetaInterrompida)

        layoutUploadInterrompido = findViewById(R.id.layoutUploadInterrompido)
        textUploadInterrompidoInfo = findViewById(R.id.textUploadInterrompidoInfo)

        val btnStopColeta = findViewById<Button>(R.id.btnStopColeta)
        val btnVoltarInicio = findViewById<Button>(R.id.btnVoltarInicio)
        val btnUploadDaInterrompida = findViewById<Button>(R.id.btnUploadDaInterrompida)
        val btnNovaColetaInterrompida = findViewById<Button>(R.id.btnNovaColetaInterrompida)
        val btnRetomarUploadInterrompido = findViewById<Button>(R.id.btnRetomarUploadInterrompido)
        val btnNovaColetaUploadInterrompido = findViewById<Button>(R.id.btnNovaColetaUploadInterrompido)

        when (intent.getStringExtra("MODO") ?: "COLETA") {
            "COLETA" -> {
                FlowStateStore.setState(this, AppFlowState.COLLECTING)
                mostrarEstado("COLETANDO")
            }
            "UPLOAD" -> iniciarOuRetomarUpload()
            "COLETA_INTERRUPTED" -> mostrarEstado("COLETA_INTERRUPPIDA")
            "UPLOAD_INTERRUPTED" -> mostrarEstado("UPLOAD_INTERRUPPIDO")
            else -> mostrarEstado("COLETANDO")
        }

        btnStopColeta.setOnClickListener {
            val stopIntent = Intent(this, SensorService::class.java)
            stopIntent.action = SensorService.ACTION_STOP_BY_USER
            startService(stopIntent)
            iniciarOuRetomarUpload()
        }

        btnVoltarInicio.setOnClickListener {
            finish()
        }

        btnUploadDaInterrompida.setOnClickListener {
            iniciarOuRetomarUpload()
        }

        btnRetomarUploadInterrompido.setOnClickListener {
            iniciarOuRetomarUpload()
        }

        btnNovaColetaInterrompida.setOnClickListener {
            confirmarNovaColetaDescartandoPendentes()
        }

        btnNovaColetaUploadInterrompido.setOnClickListener {
            confirmarNovaColetaDescartandoPendentes()
        }
    }

    private fun iniciarOuRetomarUpload() {
        dbExecutor.execute {
            totalToUpload = AppDatabase.getInstance(this).sensorReadingDao().countPending()
            runOnUiThread {
                if (totalToUpload == 0) {
                    textSucessoInfo.text = "Nenhum registro para enviar."
                    FlowStateStore.setState(this, AppFlowState.IDLE)
                    mostrarEstado("SUCESSO")
                    return@runOnUiThread
                }

                if (hasUploadAtivo()) {
                    mostrarEstado("ENVIANDO")
                    FlowStateStore.setState(this, AppFlowState.UPLOADING)
                    observarUpload()
                } else if (isWifiAvailable()) {
                    mostrarEstado("ENVIANDO")
                    iniciarUploadWorker()
                } else {
                    mostrarEstado("AGUARDANDO_WIFI")
                    FlowStateStore.setState(this, AppFlowState.WAITING_WIFI)
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

        FlowStateStore.setState(this, AppFlowState.UPLOADING)
        WorkManager.getInstance(applicationContext).enqueueUniqueWork(
            UploadWorker.UNIQUE_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            uploadRequest
        )

        observarUpload()
    }

    private fun observarUpload() {
        val workManager = WorkManager.getInstance(applicationContext)
        if (uploadWorkObserver != null) return

        uploadWorkObserver = Observer { workInfos ->
            if (workInfos.isNullOrEmpty()) return@Observer

            val running = workInfos.firstOrNull {
                it.state == WorkInfo.State.RUNNING ||
                    it.state == WorkInfo.State.ENQUEUED ||
                    it.state == WorkInfo.State.BLOCKED
            }

            if (running != null) {
                val uploaded = running.progress.getInt(UploadWorker.KEY_UPLOADED, 0)
                val total = running.progress.getInt(UploadWorker.KEY_TOTAL, totalToUpload)
                atualizarProgresso(uploaded, total)
                mostrarEstado("ENVIANDO")
                FlowStateStore.setState(this, AppFlowState.UPLOADING)
                return@Observer
            }

            val terminal = workInfos.firstOrNull {
                it.state == WorkInfo.State.SUCCEEDED ||
                    it.state == WorkInfo.State.FAILED ||
                    it.state == WorkInfo.State.CANCELLED
            } ?: return@Observer

            when (terminal.state) {
                WorkInfo.State.SUCCEEDED -> {
                    val outputData = terminal.outputData
                    val coletaNum = outputData.getLong(UploadWorker.KEY_COLETA, -1)
                    if (coletaNum > 0) {
                        textSucessoInfo.text = "Coleta nº $coletaNum."
                    } else {
                        textSucessoInfo.text = "Nenhum registro para enviar."
                    }
                    FlowStateStore.setState(this, AppFlowState.IDLE)
                    mostrarEstado("SUCESSO")
                    limparObservadorUpload()
                }
                WorkInfo.State.FAILED, WorkInfo.State.CANCELLED -> {
                    val outputData = terminal.outputData
                    val error = outputData.getString(UploadWorker.KEY_ERROR)
                        ?: "O upload foi interrompido."
                    textUploadInterrompidoInfo.text = error
                    FlowStateStore.setState(this, AppFlowState.UPLOAD_INTERRUPTED)
                    mostrarEstado("UPLOAD_INTERRUPPIDO")
                    limparObservadorUpload()
                }
                else -> Unit
            }
        }

        workManager.getWorkInfosForUniqueWorkLiveData(UploadWorker.UNIQUE_WORK_NAME)
            .observeForever(uploadWorkObserver!!)
    }

    private fun limparObservadorUpload() {
        val observer = uploadWorkObserver ?: return
        WorkManager.getInstance(applicationContext)
            .getWorkInfosForUniqueWorkLiveData(UploadWorker.UNIQUE_WORK_NAME)
            .removeObserver(observer)
        uploadWorkObserver = null
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

    private fun atualizarProgresso(uploaded: Int, total: Int) {
        val percent = if (total > 0) (uploaded * 100) / total else 0
        progressUpload.progress = percent
        textProgressInfo.text = "$percent%"
    }

    private fun mostrarEstado(estado: String) {
        layoutColetando.visibility = View.GONE
        layoutAguardandoWifi.visibility = View.GONE
        layoutEnviando.visibility = View.GONE
        layoutSucesso.visibility = View.GONE
        layoutColetaInterrompida.visibility = View.GONE
        layoutUploadInterrompido.visibility = View.GONE

        when (estado) {
            "COLETANDO" -> layoutColetando.visibility = View.VISIBLE
            "AGUARDANDO_WIFI" -> layoutAguardandoWifi.visibility = View.VISIBLE
            "ENVIANDO" -> layoutEnviando.visibility = View.VISIBLE
            "SUCESSO" -> layoutSucesso.visibility = View.VISIBLE
            "COLETA_INTERRUPPIDA" -> layoutColetaInterrompida.visibility = View.VISIBLE
            "UPLOAD_INTERRUPPIDO" -> layoutUploadInterrompido.visibility = View.VISIBLE
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
                    FlowStateStore.setState(this@DataActivity, AppFlowState.UPLOADING)
                    iniciarUploadWorker()
                }
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
        limparObservadorUpload()

        networkCallback?.let {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            try {
                cm.unregisterNetworkCallback(it)
            } catch (_: Exception) {
            }
        }
    }

    private fun confirmarNovaColetaDescartandoPendentes() {
        AlertDialog.Builder(this)
            .setTitle("Iniciar nova coleta")
            .setMessage("Os dados pendentes desta coleta serão descartados.")
            .setPositiveButton("Continuar") { _, _ ->
                dbExecutor.execute {
                    WorkManager.getInstance(applicationContext)
                        .cancelUniqueWork(UploadWorker.UNIQUE_WORK_NAME)
                    AppDatabase.getInstance(this).sensorReadingDao().deleteAll()

                    runOnUiThread {
                        iniciarNovaColeta()
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun iniciarNovaColeta() {
        val sessionId = UUID.randomUUID().toString()
        val serviceIntent = Intent(this, SensorService::class.java)
        serviceIntent.putExtra("SESSION_ID", sessionId)
        androidx.core.content.ContextCompat.startForegroundService(this, serviceIntent)

        FlowStateStore.setState(this, AppFlowState.COLLECTING)
        mostrarEstado("COLETANDO")
    }
}
