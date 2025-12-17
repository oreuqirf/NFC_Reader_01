package com.example.nfc_reader_01

import android.app.PendingIntent
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import android.nfc.tech.NfcV // Protocolo ISO 15693
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.example.nfc_reader_01.databinding.ActivityMainBinding
import com.example.nfc_reader_01.utils.LogManager
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.charset.StandardCharsets
import kotlin.experimental.and

// --------------------------------------------------------------------------
// --- CONSTANTES DE PROTOCOLO Y NDEF ---
// --------------------------------------------------------------------------
private val CMD_READ_IDENTITY: Byte = 0x01
private val CMD_READ_PROCESS: Byte = 0x02
private val CMD_READ_ENGINEERING: Byte = 0x05
private val CMD_READ_CONFIG: Byte = 0x03
private val CMD_WRITE_CONFIG: Byte = 0x04
private val CMD_FACTORY_RESET: Byte = 0x0A
private val CMD_GET_SYSTEM_INFO: Byte = 0x2B.toByte()

// --- COMANDOS ESPECIALES DE CONTROL ---
private val CMD_SHUTDOWN: Byte = 0x10.toByte()      // Apagado
private val CMD_RESET_VOLUME: Byte = 0x11.toByte()  // Reset Volumen
private val CMD_NORMAL_MODE: Byte = 0x12.toByte()   // Modo Normal
private val CMD_CALIBRATE_FLOW: Byte = 0x13.toByte() // Calibración
private val CMD_SET_VOLUME: Byte = 0x14.toByte()    // Set Volumen
private val CMD_ENTER_ENGINEERING_MODE: Byte = 0x15.toByte() // NUEVO: Modo Ingeniería
// -------------------------------------------------

private const val MIME_COMMAND_TYPE = "application/x-cmd"
private const val MIME_RESPONSE_TYPE = "application/x-data"

class MainActivity : AppCompatActivity(), NfcInteractionListener {

    private val TAG = "NFC_MainActivity"
    private lateinit var binding: ActivityMainBinding
    private var nfcAdapter: NfcAdapter? = null
    private lateinit var pendingIntent: PendingIntent
    private val sharedViewModel: SharedNfcViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupNavigation()
        setupNfc()
        setupViewModelObservers()
        handleIntent(intent)
    }

    private fun setupNavigation() {
        val navView: BottomNavigationView = binding.navView
        val navController = findNavController(R.id.nav_host_fragment_activity_main)
        val appBarConfiguration = AppBarConfiguration(
            setOf(R.id.navigation_home, R.id.navigation_dashboard, R.id.navigation_configuration, R.id.navigation_notifications)
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)
    }

    // --- IMPLEMENTACIÓN DE NFCINTERACTIONLISTENER ---

    override fun navigateToDashboard() {
        findNavController(R.id.nav_host_fragment_activity_main).navigate(R.id.navigation_dashboard)
    }

    override fun requestNextCommand(commandId: Byte) {
        sharedViewModel.setConfigDataToWrite(null)
        sharedViewModel.sendCommand(commandId)
        sharedViewModel.setUiMessage("Primer Scan: TAG Ready. Esperando comando 0x${commandId.toHexString()}.")
    }

    override fun requestReadMaster() {
        sharedViewModel.setConfigDataToWrite(null)
        sharedViewModel.sendCommand(CMD_READ_PROCESS)
        sharedViewModel.setUiMessage("Primer Scan: TAG Ready. Acerque el Equipo PATRÓN para leer (0x02).")
    }

    override fun requestWriteConfig() {
        sharedViewModel.sendCommand(CMD_WRITE_CONFIG)
        sharedViewModel.setUiMessage("Primer Scan: TAG Ready. Escribir configuración (0x04 + 96 bytes).")
    }

    override fun requestSetVolume() {
        sharedViewModel.sendCommand(CMD_SET_VOLUME)
        sharedViewModel.setUiMessage("Primer Scan: TAG Ready. Set Volumen (0x14 + 4 bytes).")
    }

    override fun requestCalibrationWrite() {
        sharedViewModel.sendCommand(CMD_CALIBRATE_FLOW)
        sharedViewModel.setUiMessage("Primer Scan: TAG Ready. Calibración (0x13 + 5 bytes).")
    }

    /** NUEVO: Solicitud para entrar a Modo Ingeniería */
    override fun requestEnterEngineeringMode() {
        sharedViewModel.setConfigDataToWrite(null) // Comando simple sin payload extra
        sharedViewModel.sendCommand(CMD_ENTER_ENGINEERING_MODE) // 0x15
        sharedViewModel.setUiMessage("Primer Scan: TAG Ready. Entrar a Modo Ingeniería (0x15).")
    }

    // --- LÓGICA DE NFC Y CICLO DE VIDA ---

    private fun setupNfc() {
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        if (nfcAdapter == null) {
            lifecycleScope.launch { LogManager.log("NFC: Dispositivo no compatible.") }
        }
        val intent = Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        pendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        } else {
            @Suppress("DEPRECATION")
            PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
        }
    }

    private fun setupViewModelObservers() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    sharedViewModel.pendingCommand.collect { command ->
                        if (command != null) {
                            sharedViewModel.setUiMessage("Primer Scan: Comando 0x${command.toHexString()} en espera...")
                        } else if (sharedViewModel.nfcTagInfo.value == null) {
                            sharedViewModel.setUiMessage("TAG Ready: Esperando primer escaneo NDEF...")
                        }
                    }
                }
                launch {
                    sharedViewModel.writeStatus.collect { status ->
                        Toast.makeText(this@MainActivity, status, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val intentFilters = arrayOf(IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED), IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED))
        val techList = arrayOf(arrayOf(Ndef::class.java.name), arrayOf(NdefFormatable::class.java.name), arrayOf(NfcV::class.java.name))
        nfcAdapter?.enableForegroundDispatch(this, pendingIntent, intentFilters, techList)
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.disableForegroundDispatch(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        val tag: Tag? = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
        if (tag != null) {
            sharedViewModel.setNfcTagInfo(tag.id.toHexString())
            lifecycleScope.launch { processNdefTransaction(tag) }
        }
    }

    private fun logTagTechnologies(tag: Tag) { /* ... */ } // Simplificado para brevedad

    // --- LÓGICA DE TRANSACCIÓN ---

    private suspend fun processNdefTransaction(tag: Tag) = withContext(Dispatchers.IO) {
        try {
            val commandId = sharedViewModel.pendingCommand.value
            val ndef = Ndef.get(tag)
            val ndefFormatable = NdefFormatable.get(tag)
            val nfcV = NfcV.get(tag)

            // CASO 1: COMANDO PENDIENTE
            if (commandId != null) {
                var success = false
                if (ndef != null) success = executeNdefWriteCommand(ndef, commandId)
                if (!success && ndefFormatable != null) success = executeNdefFormatAndWriteCommand(ndefFormatable, commandId)

                if (!success && nfcV != null) {
                    val formatSuccess = executeNfcVForceFormatOnlyFallback(nfcV)
                    if (formatSuccess) {
                        sharedViewModel.emitWriteStatus("FALLBACK OK: TAG formateado. Reintentar.")
                    } else {
                        sharedViewModel.emitWriteStatus("ERROR NfcV: Fallo total.")
                    }
                    return@withContext
                }

                if (success) sharedViewModel.clearCommand()
                return@withContext
            }

            // CASO 2: LECTURA
            if (ndef != null) {
                try {
                    ndef.connect()
                    executeNdefReadResponse(ndef)
                } finally {
                    try { ndef.close() } catch (_: IOException) {}
                }
            } else {
                sharedViewModel.setUiMessage("Advertencia: TAG no válido/formateado.")
            }

        } catch (e: Exception) {
            sharedViewModel.emitWriteStatus("ERROR CRÍTICO: ${e.message}")
        }
    }

    private fun createNdefCommandPayload(commandId: Byte): ByteArray? {
        return when (commandId) {
            CMD_WRITE_CONFIG -> {
                val data = sharedViewModel.configDataToWrite.value
                if (data != null && data.size == 96) byteArrayOf(commandId) + data else null.also { reportDataError("Faltan datos config.") }
            }
            CMD_SET_VOLUME -> {
                val data = sharedViewModel.configDataToWrite.value
                if (data != null && data.size == 4) byteArrayOf(commandId) + data else null.also { reportDataError("Faltan datos volumen.") }
            }
            CMD_CALIBRATE_FLOW -> {
                val data = sharedViewModel.configDataToWrite.value
                if (data != null && data.size == 5) byteArrayOf(commandId) + data else null.also { reportDataError("Faltan datos calibración.") }
            }
            // 0x15 entra aquí (comando simple de 1 byte)
            else -> byteArrayOf(commandId)
        }
    }

    private fun reportDataError(msg: String) {
        sharedViewModel.setUiMessage(msg)
        sharedViewModel.emitWriteStatus(msg)
    }

    // Funciones executeNdefWriteCommand, executeNdefFormatAndWriteCommand, executeNfcVForceFormatOnlyFallback, executeNdefReadResponse
    // Se mantienen idénticas a la versión anterior (usando createNdefCommandPayload)

    private fun executeNdefWriteCommand(ndef: Ndef, commandId: Byte): Boolean {
        val fullPayload = createNdefCommandPayload(commandId) ?: return false
        val message = NdefMessage(NdefRecord.createMime(MIME_COMMAND_TYPE, fullPayload))
        try {
            if (!ndef.isConnected) ndef.connect()
            if (!ndef.isWritable) return false
            ndef.writeNdefMessage(message)
            sharedViewModel.emitWriteStatus("Escritura OK. Comando enviado.")
            if (isWriteCommand(commandId)) sharedViewModel.setConfigDataToWrite(null)
            return true
        } catch (e: Exception) {
            sharedViewModel.emitWriteStatus("Error Escritura: Reintente.")
            return false
        } finally {
            try { ndef.close() } catch (_: Exception) {}
        }
    }

    private fun executeNdefFormatAndWriteCommand(formatable: NdefFormatable, commandId: Byte): Boolean {
        val fullPayload = createNdefCommandPayload(commandId) ?: return false
        val message = NdefMessage(NdefRecord.createMime(MIME_COMMAND_TYPE, fullPayload))
        try {
            formatable.connect()
            formatable.format(message)
            sharedViewModel.emitWriteStatus("Escritura Forzada OK.")
            if (isWriteCommand(commandId)) sharedViewModel.setConfigDataToWrite(null)
            return true
        } catch (e: Exception) {
            return false
        } finally {
            try { formatable.close() } catch (_: Exception) {}
        }
    }

    private fun executeNfcVForceFormatOnlyFallback(nfcV: NfcV): Boolean {
        // ... (Implementación idéntica a la anterior para formateo de CC)
        val ccBlock = byteArrayOf(0xE1.toByte(), 0x40.toByte(), 0x00, 0x40)
        val flags: Byte = 0x02
        val cmdWrite: Byte = 0x21.toByte()
        try {
            if (!nfcV.isConnected) nfcV.connect()
            val cmd = byteArrayOf(flags, cmdWrite, 0x00) + ccBlock
            val response = nfcV.transceive(cmd)
            return response.isEmpty() || (response.size == 1 && response[0].and(0x01) == 0.toByte())
        } catch (e: IOException) { return false }
        finally { try { nfcV.close() } catch (_: Exception) {} }
    }

    private fun executeNdefReadResponse(ndef: Ndef) {
        val message = ndef.ndefMessage ?: return
        var found = false
        for (record in message.records) {
            if (record.tnf == NdefRecord.TNF_MIME_MEDIA) {
                // ... (Lógica de lectura idéntica)
                val type = String(record.type, StandardCharsets.US_ASCII)
                if (type == MIME_RESPONSE_TYPE && record.payload.isNotEmpty()) {
                    val cmdId = record.payload[0]
                    val data = record.payload.copyOfRange(1, record.payload.size)
                    sharedViewModel.distributeResponseData(cmdId, data)
                    found = true
                    break
                }
            }
        }
        if (!found) sharedViewModel.emitWriteStatus("ERROR NDEF: Respuesta no hallada.")
    }

    private fun isWriteCommand(id: Byte): Boolean {
        return id == CMD_WRITE_CONFIG || id == CMD_SET_VOLUME || id == CMD_CALIBRATE_FLOW
    }
}

// Utils
fun ByteArray.toHexString() = joinToString(" ") { "%02X".format(it) }
fun Byte.toHexString() = String.format("%02X", this)