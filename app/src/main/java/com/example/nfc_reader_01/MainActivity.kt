package com.example.nfc_reader_01

import android.app.PendingIntent
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.media.ToneGenerator
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
private val CMD_READ_CONFIG: Byte = 0x03
private val CMD_WRITE_CONFIG: Byte = 0x04 // Requiere 96 bytes payload
private val CMD_READ_ENGINEERING: Byte = 0x05
private val CMD_FACTORY_RESET: Byte = 0x0A
private val CMD_GET_SYSTEM_INFO: Byte = 0x2B.toByte()

// --- COMANDOS ESPECIALES DE CONTROL ---
private val CMD_SHUTDOWN: Byte = 0x10.toByte()      // Apagado
private val CMD_RESET_VOLUME: Byte = 0x11.toByte()  // Reset Volumen
private val CMD_NORMAL_MODE: Byte = 0x12.toByte()   // Modo Normal
private val CMD_CALIBRATE_FLOW: Byte = 0x13.toByte() // Calibración (5 bytes)
private val CMD_SET_VOLUME: Byte = 0x14.toByte()    // Set Volumen (4 bytes)
private val CMD_ENTER_ENGINEERING_MODE: Byte = 0x15.toByte() // Modo Ingeniería
private val CMD_CLEAR_FLAGS: Byte = 0x16.toByte()           // Limpiar Banderas

// MIME Type para enviar el comando al TAG (Primer Scan)
private const val MIME_COMMAND_TYPE = "application/x-cmd"
// MIME Type para esperar la respuesta de datos del TAG (Segundo Scan)
private const val MIME_RESPONSE_TYPE = "application/x-data"

/**
 * Actividad principal que maneja la inicialización de NFC y la comunicación NDEF
 * de doble escaneo.
 */
class MainActivity : AppCompatActivity(), NfcInteractionListener {

    private val TAG = "NFC_MainActivity"
    private lateinit var binding: ActivityMainBinding
    private var nfcAdapter: NfcAdapter? = null
    private lateinit var pendingIntent: PendingIntent
    private val sharedViewModel: SharedNfcViewModel by viewModels()

    // Generador de sonidos para feedback auditivo
    private var toneGenerator: ToneGenerator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Inicializar Beep
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
        } catch (e: Exception) {
            lifecycleScope.launch { LogManager.log("Error init ToneGenerator: ${e.message}") }
        }

        setupNavigation()
        setupNfc()
        setupViewModelObservers()
        handleIntent(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        toneGenerator?.release()
        toneGenerator = null
    }

    private fun playBeep() {
        try {
            // Tono fuerte y corto para confirmación
            toneGenerator?.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 200)
        } catch (e: Exception) { /* Ignorar error de audio */ }
    }

    private fun setupNavigation() {
        val navView: BottomNavigationView = binding.navView
        val navController = findNavController(R.id.nav_host_fragment_activity_main)
        val appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.navigation_home,
                R.id.navigation_dashboard,
                R.id.navigation_configuration,
                R.id.navigation_calibration,
                R.id.navigation_notifications
            )
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)
    }

    // --------------------------------------------------------------------------
    // --- IMPLEMENTACIÓN DE NFCINTERACTIONLISTENER ---
    // --------------------------------------------------------------------------

    override fun navigateToDashboard() {
        findNavController(R.id.nav_host_fragment_activity_main).navigate(R.id.navigation_dashboard)
    }

    /** Solicita la ejecución de un comando simple (Primer Scan) */
    override fun requestNextCommand(commandId: Byte) {
        sharedViewModel.setConfigDataToWrite(null)
        sharedViewModel.sendCommand(commandId)
        val cmdHex = String.format("%02X", commandId)
        sharedViewModel.setUiMessage("Primer Scan: TAG Ready. Esperando NDEF para comando 0x$cmdHex.")
    }

    /** Solicitud para leer el equipo patrón (Usa Ingeniería 0x05) */
    override fun requestReadMaster() {
        sharedViewModel.setConfigDataToWrite(null)
        sharedViewModel.sendCommand(CMD_READ_ENGINEERING) // 0x05 para obtener datos precisos
        sharedViewModel.setUiMessage("Primer Scan: TAG Ready. Leer Patrón (0x05).")
    }

    override fun requestWriteConfig() {
        sharedViewModel.sendCommand(CMD_WRITE_CONFIG)
        sharedViewModel.setUiMessage("Primer Scan: TAG Ready. Escribir Config (96 bytes).")
    }

    override fun requestSetVolume() {
        sharedViewModel.sendCommand(CMD_SET_VOLUME)
        sharedViewModel.setUiMessage("Primer Scan: TAG Ready. Set Volumen (4 bytes).")
    }

    override fun requestCalibrationWrite() {
        // El fragment ya debe haber puesto los 5 bytes en configDataToWrite
        sharedViewModel.sendCommand(CMD_CALIBRATE_FLOW)
        sharedViewModel.setUiMessage("Primer Scan: TAG Ready. Calibración (5 bytes).")
    }

    override fun requestEnterEngineeringMode() {
        sharedViewModel.setConfigDataToWrite(null)
        sharedViewModel.sendCommand(CMD_ENTER_ENGINEERING_MODE)
        sharedViewModel.setUiMessage("Primer Scan: TAG Ready. Entrar Modo Ing. (0x15).")
    }

    // --------------------------------------------------------------------------
    // --- LÓGICA DE NFC Y CICLO DE VIDA ---
    // --------------------------------------------------------------------------

    private fun setupNfc() {
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        if (nfcAdapter == null) {
            lifecycleScope.launch { LogManager.log("NFC: No compatible.") }
        }
        val intent = Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        pendingIntent = PendingIntent.getActivity(this, 0, intent, flags)
    }

    private fun setupViewModelObservers() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // 1. Observador de comando pendiente
                launch {
                    sharedViewModel.pendingCommand.collect { command ->
                        if (command != null) {
                            val cmdHex = String.format("%02X", command)
                            sharedViewModel.setUiMessage("Primer Scan: Comando 0x$cmdHex en espera...")
                        } else if (sharedViewModel.nfcTagInfo.value == null) {
                            sharedViewModel.setUiMessage("TAG Ready: Esperando primer escaneo NDEF...")
                        }
                    }
                }
                // 2. Observador de Toast y Sonido
                launch {
                    sharedViewModel.writeStatus.collect { status ->
                        // Muestra el Toast
                        Toast.makeText(this@MainActivity, status, Toast.LENGTH_LONG).show()
                        // Hace el Beep
                        playBeep()
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val intentFilters = arrayOf(
            IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED),
            IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED),
            IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED)
        )
        val techList = arrayOf(
            arrayOf(Ndef::class.java.name),
            arrayOf(NdefFormatable::class.java.name),
            arrayOf(NfcV::class.java.name)
        )
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

    private fun logTagTechnologies(tag: Tag) {
        val techList = tag.techList.joinToString(", ")
        lifecycleScope.launch {
            val idHex = tag.id.joinToString(" ") { "%02X".format(it) }
            LogManager.log("TAG DIAGNÓSTICO: ID=$idHex, Techs=[$techList]")
        }
    }

    private fun handleIntent(intent: Intent) {
        val tag: Tag? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
        }

        if (tag == null) {
            sharedViewModel.setNfcTagInfo(null)
            return
        }

        val idHex = tag.id.joinToString(" ") { "%02X".format(it) }
        sharedViewModel.setNfcTagInfo(idHex)
        logTagTechnologies(tag)

        lifecycleScope.launch {
            processNdefTransaction(tag)
        }
    }

    // --------------------------------------------------------------------------
    // --- LÓGICA DE TRANSACCIÓN NFC Y FALLBACKS ---
    // --------------------------------------------------------------------------

    private suspend fun processNdefTransaction(tag: Tag) = withContext(Dispatchers.IO) {
        try {
            val commandId = sharedViewModel.pendingCommand.value
            val ndef = Ndef.get(tag)
            val ndefFormatable = NdefFormatable.get(tag)
            val nfcV = NfcV.get(tag)

            lifecycleScope.launch {
                LogManager.log("DBG Checks: Ndef=${if(ndef!=null)"OK" else "NO"}, Formatable=${if(ndefFormatable!=null)"OK" else "NO"}, NfcV=${if(nfcV!=null)"OK" else "NO"}")
            }

            // --- CASO 1: COMANDO PENDIENTE (Primer Scan) ---
            if (commandId != null) {
                var success = false

                // 1. Intentar escritura NDEF estándar
                if (ndef != null) {
                    success = executeNdefWriteCommand(ndef, commandId)
                }

                // 2. Intentar formateo y escritura NDEF (si falla el anterior y es formateable)
                if (!success && ndefFormatable != null) {
                    success = executeNdefFormatAndWriteCommand(ndefFormatable, commandId)
                }

                // 3. Fallback Avanzado: Forzar formato con NfcV
                if (!success && nfcV != null) {
                    lifecycleScope.launch { LogManager.log("Activando Fallback: Formateo CC via NfcV.") }
                    val formatSuccess = executeNfcVForceFormatOnlyFallback(nfcV)

                    if (formatSuccess) {
                        sharedViewModel.setUiMessage("Primer Scan FORZADO (NfcV) OK. ¡Acerque el TAG de nuevo!")
                        sharedViewModel.emitWriteStatus("FALLBACK OK: TAG formateado. Reintentar.")
                    } else {
                        sharedViewModel.setUiMessage("ERROR: Fallo al forzar escritura.")
                        sharedViewModel.emitWriteStatus("ERROR NfcV: Fallo total.")
                    }
                    return@withContext
                }

                if (success) {
                    sharedViewModel.clearCommand()
                } else if (!success) {
                    sharedViewModel.setUiMessage("ERROR: Fallo al procesar comando. TAG no soportado.")
                }
                return@withContext
            }

            // --- CASO 2: NO HAY COMANDO (Segundo Scan - Lectura) ---
            if (ndef != null) {
                try {
                    ndef.connect()
                    executeNdefReadResponse(ndef)
                } catch (e: IOException) {
                    val errorMsg = "Error NDEF Lectura: ${e.message}"
                    lifecycleScope.launch { LogManager.log(errorMsg) }
                    sharedViewModel.setUiMessage(errorMsg)
                    sharedViewModel.emitWriteStatus("ERROR NDEF I/O.")
                } finally {
                    try { ndef.close() } catch (_: IOException) {}
                }
            } else {
                sharedViewModel.setUiMessage("Advertencia: TAG no válido/formateado.")
            }

        } catch (e: Exception) {
            val errorMsg = "Error CRÍTICO de Transacción: ${e.message}"
            lifecycleScope.launch { LogManager.log("CRASH_PREVENT: $errorMsg") }
            sharedViewModel.setUiMessage("ERROR CRÍTICO. Ver Log.")
            sharedViewModel.emitWriteStatus("ERROR CRÍTICO.")
        }
    }

    // --- FUNCIONES AUXILIARES DE ESCRITURA/LECTURA ---

    private fun createNdefCommandPayload(commandId: Byte): ByteArray? {
        return when (commandId) {
            CMD_WRITE_CONFIG -> {
                val data = sharedViewModel.configDataToWrite.value
                if (data != null && data.size == 96) byteArrayOf(commandId) + data else null.also { reportDataError("Faltan datos config (96B).") }
            }
            CMD_SET_VOLUME -> {
                val data = sharedViewModel.configDataToWrite.value
                if (data != null && data.size == 4) byteArrayOf(commandId) + data else null.also { reportDataError("Faltan datos volumen (4B).") }
            }
            CMD_CALIBRATE_FLOW -> {
                val data = sharedViewModel.configDataToWrite.value
                if (data != null && data.size == 9) byteArrayOf(commandId) + data else null.also { reportDataError("Faltan datos calibración (9B).") }
            }
            else -> byteArrayOf(commandId) // Comandos simples
        }
    }

    private fun reportDataError(msg: String) {
        sharedViewModel.setUiMessage(msg)
        sharedViewModel.emitWriteStatus(msg)
    }

    private fun executeNdefFormatAndWriteCommand(ndefFormatable: NdefFormatable, commandId: Byte): Boolean {
        val fullPayload = createNdefCommandPayload(commandId) ?: return false
        val message = NdefMessage(NdefRecord.createMime(MIME_COMMAND_TYPE, fullPayload))
        lifecycleScope.launch { LogManager.log("Primer Scan FORZADO (Formatable)...") }

        try {
            ndefFormatable.connect()
            ndefFormatable.format(message)
            sharedViewModel.setUiMessage("Primer Scan FORZADO OK. ¡Acerque el TAG para leer!")

            // LÓGICA DE MENSAJE EXITOSO PERSONALIZADO
            if (commandId == CMD_CALIBRATE_FLOW) {
                sharedViewModel.emitWriteStatus("Transferencia Exitosa")
            } else {
                sharedViewModel.emitWriteStatus("Escritura Forzada OK.")
            }

            if (isWriteCommand(commandId)) sharedViewModel.setConfigDataToWrite(null)
            return true
        } catch (e: IOException) {
            lifecycleScope.launch { LogManager.log("Error NDEF Format: ${e.message}") }
            return false
        } finally {
            try { ndefFormatable.close() } catch (_: IOException) {}
        }
    }

    private fun executeNdefWriteCommand(ndef: Ndef, commandId: Byte): Boolean {
        val fullPayload = createNdefCommandPayload(commandId) ?: return false
        val message = NdefMessage(NdefRecord.createMime(MIME_COMMAND_TYPE, fullPayload))

        lifecycleScope.launch { LogManager.log("Primer Scan: Escribiendo comando 0x${commandId.toHexString()}...") }

        try {
            if (!ndef.isConnected) ndef.connect()
            if (!ndef.isWritable) {
                reportDataError("ERROR: TAG no escribible.")
                return false
            }
            if (ndef.maxSize < message.toByteArray().size) {
                reportDataError("ERROR: Mensaje demasiado grande.")
                return false
            }

            ndef.writeNdefMessage(message)
            sharedViewModel.setUiMessage("Primer Scan OK. ¡Acerque el TAG para leer!")

            // LÓGICA DE MENSAJE EXITOSO PERSONALIZADO
            if (commandId == CMD_CALIBRATE_FLOW) {
                sharedViewModel.emitWriteStatus("Transferencia Exitosa")
            } else {
                sharedViewModel.emitWriteStatus("Escritura NDEF OK.")
            }

            if (isWriteCommand(commandId)) sharedViewModel.setConfigDataToWrite(null)
            return true
        } catch (e: Exception) {
            val msg = if (e is SecurityException) "Tag perdido" else "Error I/O"
            sharedViewModel.emitWriteStatus(msg)
            return false
        } finally {
            try { ndef.close() } catch (_: IOException) {}
        }
    }

    private fun isWriteCommand(id: Byte): Boolean {
        return id == CMD_WRITE_CONFIG || id == CMD_SET_VOLUME || id == CMD_CALIBRATE_FLOW
    }

    private fun executeNfcVForceFormatOnlyFallback(nfcV: NfcV): Boolean {
        lifecycleScope.launch { LogManager.log("NfcV: Forzando CC en Bloque 0...") }
        val ccBlock = byteArrayOf(0xE1.toByte(), 0x40.toByte(), 0x00, 0x40)
        val flags: Byte = 0x02
        val cmdWrite: Byte = 0x21.toByte()

        try {
            if (!nfcV.isConnected) nfcV.connect()
            val cmd = byteArrayOf(flags, cmdWrite, 0x00) + ccBlock
            val response = nfcV.transceive(cmd)
            return response.isEmpty() || (response.size == 1 && response[0].and(0x01) == 0.toByte())
        } catch (e: IOException) {
            return false
        } finally {
            try { nfcV.close() } catch (_: IOException) {}
        }
    }

    private fun executeNdefReadResponse(ndef: Ndef) {
        val message = ndef.ndefMessage ?: return
        var found = false
        for (record in message.records) {
            if (record.tnf == NdefRecord.TNF_MIME_MEDIA) {
                val type = String(record.type, StandardCharsets.US_ASCII)
                if (type == MIME_RESPONSE_TYPE && record.payload.isNotEmpty()) {
                    val cmdId = record.payload[0]
                    val data = record.payload.copyOfRange(1, record.payload.size)
                    lifecycleScope.launch { LogManager.log("Segundo Scan OK: 0x${cmdId.toHexString()}") }
                    sharedViewModel.distributeResponseData(cmdId, data)
                    sharedViewModel.setUiMessage("Segundo Scan OK.")
                    found = true
                    break
                } else if (type == MIME_COMMAND_TYPE) {
                    sharedViewModel.emitWriteStatus("ERROR SINCRO: TAG tiene comando.")
                    found = true
                    break
                }
            }
        }
        if (!found) sharedViewModel.emitWriteStatus("ERROR NDEF: Respuesta no hallada.")
    }
}

// Utils
fun ByteArray.toHexString() = joinToString(" ") { "%02X".format(it) }
fun Byte.toHexString() = String.format("%02X", this.toInt() and 0xFF)
