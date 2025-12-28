package com.example.nfc_reader_01

import android.app.PendingIntent
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.ToneGenerator
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import android.nfc.tech.NfcV
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
import com.example.nfc_reader_01.databinding.ActivityMainBinding
import com.example.nfc_reader_01.NfcState // Asegúrate de que este import coincida con donde guardaste NfcState
import com.example.nfc_reader_01.utils.LogManager
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
private val CMD_WRITE_CONFIG: Byte = 0x04
private val CMD_READ_ENGINEERING: Byte = 0x05
private val CMD_FACTORY_RESET: Byte = 0x0A
private val CMD_GET_SYSTEM_INFO: Byte = 0x2B.toByte()

// --- COMANDOS ESPECIALES DE CONTROL ---
private val CMD_SHUTDOWN: Byte = 0x10.toByte()
private val CMD_RESET_VOLUME: Byte = 0x11.toByte()
private val CMD_NORMAL_MODE: Byte = 0x12.toByte()
private val CMD_CALIBRATE_FLOW: Byte = 0x13.toByte()
private val CMD_SET_VOLUME: Byte = 0x14.toByte()
private val CMD_ENTER_ENGINEERING_MODE: Byte = 0x15.toByte()
private val CMD_CLEAR_FLAGS: Byte = 0x16.toByte()

// MIME Types
private const val MIME_COMMAND_TYPE = "application/x-cmd"
private const val MIME_RESPONSE_TYPE = "application/x-data"

class MainActivity : AppCompatActivity(), NfcInteractionListener {

    private val TAG = "NFC_MainActivity"
    private lateinit var binding: ActivityMainBinding
    private var nfcAdapter: NfcAdapter? = null
    private lateinit var pendingIntent: PendingIntent
    private val sharedViewModel: SharedNfcViewModel by viewModels()

    private var toneGenerator: ToneGenerator? = null
    private var mediaPlayer: MediaPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initAudioSystems()
        setupNavigation()
        setupNfc()
        setupViewModelObservers()
        handleIntent(intent)
    }

    private fun initAudioSystems() {
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
        } catch (e: Exception) {
            lifecycleScope.launch { LogManager.log("Error init ToneGenerator: ${e.message}") }
        }

        try {
            mediaPlayer = MediaPlayer.create(this, R.raw.beep)
        } catch (e: Exception) {
            lifecycleScope.launch { LogManager.log("Error init MediaPlayer: ${e.message}") }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        toneGenerator?.release()
        toneGenerator = null
        mediaPlayer?.release()
        mediaPlayer = null
    }

    private fun playCustomSuccessSound() {
        try {
            if (mediaPlayer != null) {
                if (mediaPlayer!!.isPlaying) mediaPlayer!!.seekTo(0)
                else mediaPlayer!!.start()
            } else {
                playSystemBeep()
            }
        } catch (e: Exception) {
            playSystemBeep()
        }
    }

    private fun playSystemBeep() {
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 200)
        } catch (e: Exception) { /* Ignorar */ }
    }

    private fun setupNavigation() {
        val navController = findNavController(R.id.nav_host_fragment_activity_main)
        val appBarConfiguration = AppBarConfiguration(setOf(R.id.navigation_calibration))
        setupActionBarWithNavController(navController, appBarConfiguration)
    }

    // --- IMPLEMENTACIÓN DE NFCINTERACTIONLISTENER ---

    override fun navigateToDashboard() { }

    override fun requestNextCommand(commandId: Byte) {
        sharedViewModel.setConfigDataToWrite(null)
        sharedViewModel.sendCommand(commandId)
        val cmdHex = String.format("%02X", commandId)
        sharedViewModel.setUiMessage("Primer Scan: Comando 0x$cmdHex.")
    }

    override fun requestReadMaster() {
        sharedViewModel.setConfigDataToWrite(null)
        sharedViewModel.sendCommand(CMD_READ_ENGINEERING)
        sharedViewModel.setUiMessage("Primer Scan: Leer Patrón (0x05).")
    }

    override fun requestWriteConfig() {
        sharedViewModel.sendCommand(CMD_WRITE_CONFIG)
        sharedViewModel.setUiMessage("Primer Scan: Escribir Config.")
    }

    override fun requestSetVolume() {
        sharedViewModel.sendCommand(CMD_SET_VOLUME)
        sharedViewModel.setUiMessage("Primer Scan: Set Volumen.")
    }

    override fun requestCalibrationWrite() {
        sharedViewModel.sendCommand(CMD_CALIBRATE_FLOW)
        sharedViewModel.setUiMessage("Primer Scan: Calibración.")
    }

    override fun requestEnterEngineeringMode() {
        sharedViewModel.setConfigDataToWrite(null)
        sharedViewModel.sendCommand(CMD_ENTER_ENGINEERING_MODE)
        sharedViewModel.setUiMessage("Primer Scan: Modo Ing. (0x15).")
    }

    // --- LÓGICA DE NFC Y CICLO DE VIDA ---

    private fun setupNfc() {
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
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
                // 1. Comando Pendiente
                launch {
                    sharedViewModel.pendingCommand.collect { command ->
                        if (command != null) {
                            val cmdHex = String.format("%02X", command)
                            sharedViewModel.setUiMessage("Scan: 0x$cmdHex pendiente...")
                        }
                    }
                }

                // 2. Estado de Escritura / Sonido
                launch {
                    sharedViewModel.writeStatus.collect { state ->
                        when (state) {
                            is NfcState.Success -> {
                                playCustomSuccessSound()
                            }
                            is NfcState.Error -> {
                                playSystemBeep()
                                Toast.makeText(this@MainActivity, state.errorMessage, Toast.LENGTH_LONG).show()
                            }
                            is NfcState.Idle, is NfcState.Loading -> { }
                        }
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

    private fun handleIntent(intent: Intent) {
        val tag: Tag? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
        }

        if (tag == null) return

        val idHex = tag.id.joinToString(" ") { "%02X".format(it) }
        sharedViewModel.setNfcTagInfo(idHex)

        lifecycleScope.launch {
            processNdefTransaction(tag)
        }
    }

    // --- TRANSACCIÓN NFC ---

    private suspend fun processNdefTransaction(tag: Tag) = withContext(Dispatchers.IO) {
        try {
            val commandId = sharedViewModel.pendingCommand.value
            val ndef = Ndef.get(tag)
            val ndefFormatable = NdefFormatable.get(tag)
            val nfcV = NfcV.get(tag)

            // CASO 1: COMANDO PENDIENTE (Primer Scan - Escritura)
            if (commandId != null) {
                var success = false
                if (ndef != null) success = executeNdefWriteCommand(ndef, commandId)
                if (!success && ndefFormatable != null) success = executeNdefFormatAndWriteCommand(ndefFormatable, commandId)

                // Fallback NfcV
                if (!success && nfcV != null) {
                    val formatSuccess = executeNfcVForceFormatOnlyFallback(nfcV)
                    if (formatSuccess) {
                        sharedViewModel.setUiMessage("FALLBACK OK. Reintentar.")
                        sharedViewModel.setStatusError("Formateo OK. Reintente.")
                    } else {
                        sharedViewModel.setStatusError("Error total NfcV.")
                    }
                    return@withContext
                }

                if (success) sharedViewModel.clearCommand()
                return@withContext
            }

            // CASO 2: LECTURA (Segundo Scan - Respuesta)
            if (ndef != null) {
                try {
                    ndef.connect()
                    executeNdefReadResponse(ndef)
                } catch (e: IOException) {
                    sharedViewModel.setStatusError("Error NDEF I/O.")
                } finally {
                    try { ndef.close() } catch (_: Exception) {}
                }
            }

        } catch (e: Exception) {
            sharedViewModel.setStatusError("Error Crítico.")
        }
    }

    // --- AUXILIARES ESCRITURA ---

    private fun createNdefCommandPayload(commandId: Byte): ByteArray? {
        val data = sharedViewModel.configDataToWrite.value
        return when (commandId) {
            CMD_WRITE_CONFIG -> if (data?.size == 96) byteArrayOf(commandId) + data else null
            CMD_SET_VOLUME -> if (data?.size == 4) byteArrayOf(commandId) + data else null
            // NOTA: Aquí se valida size == 9 porque CalibrationFragment envía 9 bytes (1 byte tipo + 8 bytes floats)
            CMD_CALIBRATE_FLOW -> if (data?.size == 9) byteArrayOf(commandId) + data else null
            else -> byteArrayOf(commandId)
        }
    }

    private fun executeNdefFormatAndWriteCommand(ndefFormatable: NdefFormatable, commandId: Byte): Boolean {
        val fullPayload = createNdefCommandPayload(commandId) ?: return false
        val message = NdefMessage(NdefRecord.createMime(MIME_COMMAND_TYPE, fullPayload))
        try {
            ndefFormatable.connect()
            ndefFormatable.format(message)

            // CORRECCIÓN CLAVE: NO enviar Success si es calibración (esperar 2do scan)
            if (commandId == CMD_CALIBRATE_FLOW) {
                sharedViewModel.setUiMessage("Solicitud escrita. Mantenga para confirmar...")
                // NO llamamos a setStatusSuccess() aquí
            } else {
                sharedViewModel.setStatusSuccess()
            }
            return true
        } catch (e: IOException) {
            return false
        } finally {
            try { ndefFormatable.close() } catch (_: Exception) {}
        }
    }

    private fun executeNdefWriteCommand(ndef: Ndef, commandId: Byte): Boolean {
        val fullPayload = createNdefCommandPayload(commandId) ?: return false
        val message = NdefMessage(NdefRecord.createMime(MIME_COMMAND_TYPE, fullPayload))
        try {
            if (!ndef.isConnected) ndef.connect()
            if (!ndef.isWritable) return false
            ndef.writeNdefMessage(message)

            // CORRECCIÓN CLAVE: NO enviar Success si es calibración (esperar 2do scan)
            if (commandId == CMD_CALIBRATE_FLOW) {
                sharedViewModel.setUiMessage("Solicitud enviada. Mantenga para confirmar...")
                // NO llamamos a setStatusSuccess() aquí para evitar el salto de pantalla
            } else {
                sharedViewModel.setStatusSuccess()
            }
            return true
        } catch (e: Exception) {
            return false
        } finally {
            try { ndef.close() } catch (_: Exception) {}
        }
    }

    private fun executeNfcVForceFormatOnlyFallback(nfcV: NfcV): Boolean {
        val ccBlock = byteArrayOf(0xE1.toByte(), 0x40.toByte(), 0x00, 0x40)
        try {
            if (!nfcV.isConnected) nfcV.connect()
            val cmd = byteArrayOf(0x02, 0x21, 0x00) + ccBlock
            val response = nfcV.transceive(cmd)
            return response.isEmpty() || (response.size == 1 && response[0].and(0x01) == 0.toByte())
        } catch (e: IOException) {
            return false
        } finally {
            try { nfcV.close() } catch (_: Exception) {}
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

                    // Aquí el ViewModel procesará la respuesta y emitirá Success
                    // haciendo que la pantalla cambie en el momento correcto
                    sharedViewModel.distributeResponseData(cmdId, data)

                    found = true
                    break
                }
            }
        }
        if (!found) sharedViewModel.setStatusError("Respuesta no hallada.")
    }
}

fun ByteArray.toHexString() = joinToString(" ") { "%02X".format(it) }
fun Byte.toHexString() = String.format("%02X", this.toInt() and 0xFF)
