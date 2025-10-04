package com.example.nfc_reader_01

import android.content.Intent
import android.media.MediaPlayer
import android.nfc.NdefMessage
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.example.nfc_reader_01.databinding.ActivityMainBinding
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder


class MainActivity : AppCompatActivity(), NfcInteractionListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    private lateinit var sharedNfcViewModel: SharedNfcViewModel
    private var nfcAdapter: NfcAdapter? = null
    private lateinit var pendingIntent: android.app.PendingIntent

    // --- Definición de Comandos (App -> TAG) y Tipos MIME ---
    private val COMMAND_ID_DATA: Byte = 0x01.toByte()
    private val COMMAND_PROCESS_DATA: Byte = 0x02.toByte()
    private val COMMAND_CONFIG_DATA: Byte = 0x03.toByte()
    private val COMMAND_WRITE_CONFIG: Byte = 0x04.toByte()

    private val MIME_TYPE_COMMAND = "application/x-cmd"
    private val MIME_TYPE_DATA = "application/x-data"
    private val TAG_NFC_LOG = "NFC_PROTOCOL"

    // --- Constantes de Respuesta (TAG -> App) ---
    private val BLOCK_RESPONSE_IDENTITY: Byte = 0x81.toByte()
    private val BLOCK_RESPONSE_PROCESS: Byte = 0x82.toByte()
    private val BLOCK_RESPONSE_CONFIG: Byte = 0x83.toByte()
    private val BLOCK_RESPONSE_ENGINEERING: Byte = 0x85.toByte()

    // Función auxiliar para enviar logs al ViewModel y a Logcat
    private fun logProtocolActivity(message: String) {
        Log.i(TAG_NFC_LOG, message)
        sharedNfcViewModel.addProtocolLog(message)
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Inicialización del ViewModel
        sharedNfcViewModel = ViewModelProvider(this).get(SharedNfcViewModel::class.java)

        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment
        navController = navHostFragment.navController

        val appBarConfiguration = AppBarConfiguration(
            setOf(R.id.navigation_home, R.id.navigation_dashboard, R.id.navigation_notifications, R.id.navigation_configuration)
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        binding.navView.setupWithNavController(navController)

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        if (nfcAdapter == null) {
            Toast.makeText(this, "Este dispositivo no soporta NFC.", Toast.LENGTH_LONG).show()
        } else {
            pendingIntent = android.app.PendingIntent.getActivity(
                this, 0,
                Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                android.app.PendingIntent.FLAG_MUTABLE
            )
        }

        navController.addOnDestinationChangedListener { _, destination, _ ->
            if (destination.id == R.id.navigation_home) {
                // Limpiar datos al volver a Home
                sharedNfcViewModel.setNdefRecords(identity = null, process = null, config = null)
                sharedNfcViewModel.setNfcTag(null)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (nfcAdapter?.isEnabled == true) {
            nfcAdapter?.enableForegroundDispatch(this, pendingIntent, null, null)
            sharedNfcViewModel.setNfcStatus(true)
        } else {
            sharedNfcViewModel.setNfcStatus(false)
        }
    }

    override fun onPause() {
        super.onPause()
        if (nfcAdapter?.isEnabled == true) {
            nfcAdapter?.disableForegroundDispatch(this)
        }
        sharedNfcViewModel.setNdefRecords(identity = null, process = null, config = null)
        sharedNfcViewModel.setNfcTag(null)
    }


    @Suppress("DEPRECATION")
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        logProtocolActivity("--- Nuevo TAG Detectado ---")

        if (intent != null && intent.hasExtra(NfcAdapter.EXTRA_TAG)) {
            val tag: Tag? = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG) as Tag?

            if (tag != null) {
                sharedNfcViewModel.setNfcTag(tag)
                handleCommandResponseCycle(tag)
            }
        }
    }

    // -----------------------------------------------------------------------
    // --- LÓGICA CLAVE: GESTIÓN CENTRALIZADA DEL CICLO COMANDO/RESPUESTA ---
    // -----------------------------------------------------------------------


    private fun handleCommandResponseCycle(tag: Tag) {
        val writeRequest = sharedNfcViewModel.writeMessageRequest.value
        val requestedCommand = sharedNfcViewModel.nextCommandRequest.value

        // 1. --- VERIFICACIÓN DE PRIORIDAD DE ESCRITURA (Comando 0x04) ---
        // Si existe un mensaje de escritura pendiente (Comando 0x04), ejecutamos la escritura y terminamos.
        if (writeRequest != null) {
            logProtocolActivity("🚨 PRIORIDAD 0x04: Mensaje de Guardado de Configuración detectado. Ejecutando escritura.")
            writeNfcTag(tag, writeRequest)
            sharedNfcViewModel.clearWriteRequest()
            logProtocolActivity("Prioridad 0x04 completada. Ciclo terminado.")
            return
        }

        val ndef = Ndef.get(tag)
        if (ndef == null) {
            logProtocolActivity("TAG no es NDEF o no es compatible con NDEF.")
            return
        }

        try {
            ndef.connect()

            // 2. --- PRIORIDAD DE ESCRITURA DE LECTURA (Comandos 0x01, 0x02, 0x03) ---
            // Si hay un comando de lectura pendiente, lo escribimos INMEDIATAMENTE y saltamos la lectura de respuesta.
            if (requestedCommand != null) {
                if (ndef.isWritable) {
                    logProtocolActivity("🚨 PRIORIDAD REGULAR: Comando 0x${String.format("%02X", requestedCommand)} solicitado. Saltando lectura de respuesta y escribiendo comando.")

                    val commandMessage = NfcDataParser.createReadCommandMessage(requestedCommand)
                    sendCommandNfcTag(ndef, commandMessage)

                    // El comando se ha enviado, limpiamos la solicitud.
                    sharedNfcViewModel.resetNextCommandRequest()
                } else {
                    logProtocolActivity("ADVERTENCIA: Comando 0x${String.format("%02X", requestedCommand)} solicitado, pero TAG no es escribible.")
                }

                // Si el comando se solicitó, la operación se considera terminada, ya sea que se haya escrito o no.
                // EVITAMOS LA LECTURA DE RESPUESTA EN ESTE CICLO.
                return
            }

            // ---------------------------------------------------------------------------
            // 3. --- LÓGICA DE LECTURA DE RESPUESTA REGULAR (Solo si NO hay comandos pendientes) ---
            // ---------------------------------------------------------------------------

            val ndefMessage = ndef.ndefMessage
            if (ndefMessage != null) {
                // Buscamos el registro de datos (MIME_TYPE_DATA) en la respuesta del Tag
                val dataRecord = ndefMessage.records.find { it.toMimeType() == MIME_TYPE_DATA }
                if (dataRecord != null) {
                    logProtocolActivity("Ciclo: Solo Lectura. Respuesta de Datos detectada. Decodificando...")
                    readDataResponse(ndefMessage)
                    // Si leemos una respuesta, el ciclo termina.
                    return
                }
            }

            // 4. Ninguna acción.
            logProtocolActivity("No hay comando pendiente ni respuesta de datos detectada. Ciclo inactivo.")

        } catch (e: Exception) {
            Log.e(TAG_NFC_LOG, "Error grave en el ciclo de comando/respuesta: ${e.message}", e)
            logProtocolActivity("ERROR GRAVE: ${e.message}")
        } finally {
            try {
                // CORRECCIÓN: 'ndef' ya se sabe que no es nulo en este punto.
                if (ndef.isConnected) {
                    ndef.close()
                    logProtocolActivity("Conexión NDEF cerrada.")
                }
            } catch (e: IOException) { /* ignore */ }
        }
    }


    // -----------------------------------------------------------------------
    // --- FUNCIONES DE PROTOCOLO COMANDO/RESPUESTA ---
    // -----------------------------------------------------------------------

    private fun sendCommandNfcTag(ndef: Ndef, message: NdefMessage) {
        try {
            if (message.toByteArray().size > ndef.maxSize) {
                logProtocolActivity("Mensaje de comando demasiado grande para TAG. Max: ${ndef.maxSize}")
                return
            }

            ndef.writeNdefMessage(message)
            logProtocolActivity("Comando enviado: ÉXITO.")

        } catch (e: Exception) {
            Log.e(TAG_NFC_LOG, "Error al escribir comando: ${e.message}", e)
            logProtocolActivity("Error al escribir comando: ${e.message}")
        }
    }

    // Se ha simplificado la firma al eliminar 'ndef' ya que no se usa en el cuerpo.
    private fun readDataResponse(ndefMessage: NdefMessage) {
        val dataRecord = ndefMessage.records.find { it.toMimeType() == MIME_TYPE_DATA }

        if (dataRecord != null) {
            val payload = dataRecord.payload

            if (payload.size == 128) {

                val byteBuffer = ByteBuffer.wrap(payload)
                byteBuffer.order(ByteOrder.LITTLE_ENDIAN)

                val blockId = byteBuffer.get()
                logProtocolActivity("Respuesta de 128 bytes recibida. Bloque ID de RESPUESTA: 0x${String.format("%02X", blockId)}")

                // Se utiliza una expresión 'when' para manejar de forma más limpia el Block ID.
                when (blockId) {
                    BLOCK_RESPONSE_IDENTITY -> {
                        val identityDataBytes = ByteArray(12)
                        byteBuffer.get(identityDataBytes)
                        val identityData = NfcDataParser.parseIdentityData(identityDataBytes)
                        sharedNfcViewModel.setNdefRecords(identityData, null, null, null)
                        logProtocolActivity("Datos de identidad (Bloque 0x81) procesados. Longitud: 12 bytes.")
                    }
                    BLOCK_RESPONSE_PROCESS -> {
                        val processDataBytes = ByteArray(36)
                        byteBuffer.get(processDataBytes)
                        val processData = NfcDataParser.parseProcessData(processDataBytes)
                        sharedNfcViewModel.setNdefRecords(null, processData, null, null)
                        logProtocolActivity("Datos de proceso (Bloque 0x82) procesados. Longitud: 36 bytes.")
                    }
                    BLOCK_RESPONSE_CONFIG -> {
                        // CRÍTICO: Se aumenta el tamaño de la lectura de 92 a 96 bytes para incluir el timestamp.
                        val configDataBytes = ByteArray(96)
                        byteBuffer.get(configDataBytes)
                        val configData = NfcDataParser.parseConfigData(configDataBytes)
                        sharedNfcViewModel.setNdefRecords(null, null, configData,null)
                        logProtocolActivity("Datos de configuración (Bloque 0x83) procesados. Longitud: 96 bytes (incluye timestamp).")
                    }
                    BLOCK_RESPONSE_ENGINEERING -> {
                        // CRÍTICO: Se aumenta el tamaño de la lectura de 92 a 96 bytes para incluir el timestamp.
                        val engineeringDataBytes = ByteArray(40)
                        byteBuffer.get(engineeringDataBytes)
                        val engineeringData = NfcDataParser.parseEngineeringData(engineeringDataBytes)
                        sharedNfcViewModel.setNdefRecords(null, null, null,engineeringData)
                        logProtocolActivity("Datos de ingeniería (Bloque 0x85) procesados. Longitud: 40 bytes ")
                    }
                    else -> {
                        logProtocolActivity("ADVERTENCIA: Bloque ID de RESPUESTA desconocido (0x${String.format("%02X", blockId)}).")
                    }
                }

                playSound()

            } else {
                logProtocolActivity("ERROR: Respuesta de datos con TAMAÑO INCORRECTO. Esperado: 128, Recibido: ${payload.size}")
                // CORRECCIÓN ESPECÍFICA: Asegurar que se pasan los 3 parámetros de NDEF Records.
                sharedNfcViewModel.setNdefRecords(null, null, null)
            }
        } else {
            logProtocolActivity("ERROR INTERNO: No se encontró el registro de datos de respuesta esperado.")
        }
    }

    // -----------------------------------------------------------------------
    // --- FUNCIONES DE ESCRITURA EXPLÍCITA (USANDO COMANDO 0x04) ---
    // -----------------------------------------------------------------------

    private fun writeNfcTag(tag: Tag, message: NdefMessage) {
        val ndef = Ndef.get(tag)
        if (ndef == null) {
            sharedNfcViewModel.setWriteStatus("Este TAG no soporta NDEF o es de solo lectura.")
            sharedNfcViewModel.onWriteFailure()
            return
        }

        try {
            ndef.connect()
            if (!ndef.isWritable) {
                sharedNfcViewModel.setWriteStatus("La etiqueta es de solo lectura.")
                sharedNfcViewModel.onWriteFailure()
                return
            }

            if (message.toByteArray().size > ndef.maxSize) {
                sharedNfcViewModel.setWriteStatus("El comando de escritura es demasiado grande para el TAG.")
                sharedNfcViewModel.onWriteFailure()
                return
            }

            ndef.writeNdefMessage(message)

            sharedNfcViewModel.onWriteSuccess()
            sharedNfcViewModel.setWriteStatus("Comando de Escritura (0x04) enviado con éxito.")
            playSound()

        } catch (e: Exception) {
            sharedNfcViewModel.onWriteFailure()
            Log.e(TAG_NFC_LOG, "Error al escribir el comando de escritura: ${e.message}", e)
            sharedNfcViewModel.setWriteStatus("Error al escribir: ${e.message}")
        } finally {
            try {
                if (ndef.isConnected) ndef.close()
            } catch (e: IOException) { /* ignore */ }
        }
    }

    private fun playSound() {
        try {
            // Se asume que R.raw.beep existe.
            val mediaPlayer = MediaPlayer.create(this, R.raw.beep)
            mediaPlayer?.start()
            mediaPlayer?.setOnCompletionListener { mp ->
                mp.release()
            }
        } catch (e: Exception) {
            Log.e(TAG_NFC_LOG, "Error al reproducir el sonido: ${e.message}")
        }
    }

    // -----------------------------------------------------------------------
    // --- IMPLEMENTACIÓN DEL LISTENER DE FRAGMENTOS (NfcInteractionListener) ---
    // -----------------------------------------------------------------------

    override fun navigateToDashboard() {
        binding.navView.post {
            try {
                navController.navigate(R.id.navigation_dashboard)
                logProtocolActivity("Navegación automática ejecutada a Dashboard.")
            } catch (e: Exception) {
                Log.e("MainActivity", "Error al navegar al Dashboard: ${e.message}", e)
                Toast.makeText(this, "Error de navegación: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * Implementación: Solicita al ViewModel que ponga en cola un comando de lectura (0x01, 0x02, 0x03).
     */
    override fun requestNextCommand(commandId: Byte) {
        sharedNfcViewModel.requestNextCommand(commandId)
    }

    /**
     * IMPLEMENTACIÓN:
     * Solicita al ViewModel que cree y ponga en cola el mensaje de escritura de configuración (0x04)
     * para el próximo escaneo.
     */
    override fun requestWriteConfig() {
        sharedNfcViewModel.requestWriteConfig()
    }
}
