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
private val CMD_READ_CONFIG: Byte = 0x03 // Comando para leer configuración
private val CMD_WRITE_CONFIG: Byte = 0x04 // Comando para escribir configuración (requiere 96 bytes de payload)
private val CMD_FACTORY_RESET: Byte = 0x0A
private val CMD_GET_SYSTEM_INFO: Byte = 0x2B.toByte()

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

    // --------------------------------------------------------------------------
    // --- IMPLEMENTACIÓN DE NFCINTERACTIONLISTENER ---
    // --------------------------------------------------------------------------

    override fun navigateToDashboard() {
        findNavController(R.id.nav_host_fragment_activity_main).navigate(R.id.navigation_dashboard)
    }

    /** Solicita la ejecución de un comando (Primer Scan) */
    override fun requestNextCommand(commandId: Byte) {
        // Para comandos de lectura/reset, el payload es solo el ID.
        sharedViewModel.setConfigDataToWrite(null) // Limpiamos cualquier data de escritura pendiente
        sharedViewModel.sendCommand(commandId)
        sharedViewModel.setUiMessage("Primer Scan: TAG Ready. Esperando NDEF para escribir comando (0x${commandId.toHexString()}).")
    }

    override fun requestWriteConfig() {
        // El fragment ya debe haber puesto los 96 bytes en configDataToWrite.
        sharedViewModel.sendCommand(CMD_WRITE_CONFIG) // Seteamos 0x04
        sharedViewModel.setUiMessage("Primer Scan: TAG Ready. Esperando NDEF para escribir comando (0x${CMD_WRITE_CONFIG.toHexString()} + 96 bytes).")
    }

    // --------------------------------------------------------------------------
    // --- LÓGICA DE NFC Y CICLO DE VIDA ---
    // --------------------------------------------------------------------------

    private fun setupNfc() {
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        if (nfcAdapter == null) {
            lifecycleScope.launch { LogManager.log("NFC: Dispositivo no compatible con NFC.") }
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
                // 1. Observador para el Comando Pendiente (actualiza UI Message)
                launch {
                    sharedViewModel.pendingCommand.collect { command ->
                        if (command != null) {
                            sharedViewModel.setUiMessage("Primer Scan: Comando 0x${command.toHexString()} en espera de escritura NDEF...")
                        } else if (sharedViewModel.nfcTagInfo.value == null) {
                            sharedViewModel.setUiMessage("TAG Ready: Esperando primer escaneo NDEF (para escribir comando)...")
                        }
                    }
                }

                // 2. Observador para los Mensajes Toast/SnackBar (writeStatus SharedFlow)
                // Este observador es el que convierte las emisiones del ViewModel en Toasts.
                launch {
                    sharedViewModel.writeStatus.collect { status ->
                        Toast.makeText(this@MainActivity, status, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    /**
     * Habilita el despacho de primer plano para priorizar la detección de NDEF y otras tecnologías.
     */
    override fun onResume() {
        super.onResume()

        val intentFilters = arrayOf(
            IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED),
            IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED),
            IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED)
        )

        // Incluimos todas las tecnologías clave, incluida NfcV (confirmada por el usuario)
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

    /**
     * Muestra en el log todas las tecnologías detectadas en el TAG.
     */
    private fun logTagTechnologies(tag: Tag) {
        val techList = tag.techList.joinToString(", ")
        lifecycleScope.launch {
            LogManager.log("TAG DIAGNÓSTICO: ID=${tag.id.toHexString()}, Techs Detectadas=[$techList]")
        }
    }

    /**
     * Procesa el Intent de descubrimiento de TAG.
     */
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

        sharedViewModel.setNfcTagInfo(tag.id.toHexString())
        logTagTechnologies(tag)

        lifecycleScope.launch {
            processNdefTransaction(tag)
        }
    }

    /**
     * Maneja la transacción NDEF, priorizando NDEF/NdefFormatable, y usando NfcV como fallback de formato forzado.
     * **Contiene el bloque try/catch global para evitar cierres inesperados de la aplicación.**
     */
    private suspend fun processNdefTransaction(tag: Tag) = withContext(Dispatchers.IO) {
        // Bloque try/catch global para atrapar cualquier excepción que se propague,
        // garantizando que la coroutine no falle y no cierre la aplicación.
        try {
            val commandId = sharedViewModel.pendingCommand.value
            val ndef = Ndef.get(tag)
            val ndefFormatable = NdefFormatable.get(tag)
            val nfcV = NfcV.get(tag) // Obtenemos el objeto NfcV para el fallback

            // Logging de diagnóstico
            lifecycleScope.launch {
                LogManager.log("DBG NDEF Check: Ndef.get(tag) = ${if(ndef != null) "OK" else "NULL"}")
                LogManager.log("DBG NDEF Check: NdefFormatable.get(tag) = ${if(ndefFormatable != null) "OK" else "NULL"}")
                LogManager.log("DBG Fallback Check: NfcV.get(tag) = ${if(nfcV != null) "OK" else "NULL"}")
            }

            // --- CASO 1: COMANDO PENDIENTE (Primer Scan) ---
            if (commandId != null) {
                var success = false

                // 1. Intentar escritura NDEF estándar (TAG ya es NDEF válido y escribible)
                if (ndef != null) {
                    success = executeNdefWriteCommand(ndef, commandId)
                }

                // 2. Intentar formateo y escritura NDEF (TAG es formateable, pero el CC podría estar corrupto)
                if (!success && ndefFormatable != null) {
                    // Si esto falla (devuelve false), pasamos al fallback avanzado (paso 3)
                    success = executeNdefFormatAndWriteCommand(ndefFormatable, commandId)
                }

                // 3. Fallback Avanzado: Si NDEF/NdefFormatable fallaron, pero NfcV está disponible, forzar CC.
                if (!success && nfcV != null) {
                    lifecycleScope.launch { LogManager.log("Activando Fallback Avanzado: Formateo de CC de bajo nivel por NfcV.") }

                    val formatSuccess = executeNfcVForceFormatOnlyFallback(nfcV)

                    if (formatSuccess) {
                        sharedViewModel.setUiMessage("Primer Scan FORZADO (NfcV) OK: TAG formateado a NDEF. ¡Acerque el TAG **inmediatamente** para escribir el comando 0x${commandId.toHexString()}!")
                        // EMISIÓN DE TOAST DE FALLBACK EXITOSO
                        sharedViewModel.emitWriteStatus("FALLBACK NfcV OK: TAG formateado. Escanee de nuevo para escribir.")
                    } else {
                        val errorMsg = "ERROR: Fallo al forzar la escritura/formato del comando 0x${commandId.toHexString()}. TAG sin soporte NfcV o bloqueado."
                        sharedViewModel.setUiMessage(errorMsg)
                        // EMISIÓN DE TOAST DE FALLBACK FALLIDO
                        sharedViewModel.emitWriteStatus("ERROR NfcV: Fallo al forzar el formateo. Bloqueado o sin soporte.")
                    }
                    // Salir después del intento de formateo forzado (se requiere un nuevo escaneo para la escritura NDEF)
                    return@withContext
                }

                // Si la escritura NDEF (paso 1 o 2) fue exitosa, el comando ya se ha limpiado dentro del método de escritura
                // para evitar el doble escaneo.
                if (!success) {
                    sharedViewModel.setUiMessage("ERROR: Fallo al procesar el comando. TAG no NDEF y sin soporte para formato forzado.")
                }
                return@withContext
            }

            // --- CASO 2: NO HAY COMANDO PENDIENTE (Segundo Scan) ---
            if (ndef != null) {
                try {
                    ndef.connect()
                    executeNdefReadResponse(ndef)
                } catch (e: IOException) {
                    // Este try-catch ya maneja los 'ERROR I/O' específicos de la lectura NDEF.
                    val errorMsg = "Error NDEF de lectura (I/O): ${e.message}"
                    lifecycleScope.launch { LogManager.log(errorMsg) }
                    sharedViewModel.setUiMessage(errorMsg)
                    // sharedViewModel.emitWriteStatus("ERROR NDEF I/O: Fallo al leer la respuesta.") // Toast para I/O
                } finally {
                    if (ndef.isConnected) {
                        try { ndef.close() } catch (_: IOException) {}
                    }
                }
            } else {
                // El TAG no es NDEF válido y no hay comando pendiente.
                sharedViewModel.setUiMessage("Advertencia: TAG no válido/formateado. Listo para recibir comando.")
            }

        } catch (e: Exception) {
            // ** CAPTURA GLOBAL PARA EVITAR EL CIERRE DE LA APP **
            val errorMsg = "Error CRÍTICO de Coroutine/IO no capturado: ${e.message}"
            lifecycleScope.launch { LogManager.log("CRASH_PREVENTION_CATCH: $errorMsg") }
            sharedViewModel.setUiMessage("ERROR CRÍTICO: Fallo general de coroutine. Consulte el Log.")
            // EMITIMOS UN TOAST GENÉRICO DE ERROR INESPERADO AL USUARIO
            sharedViewModel.emitWriteStatus("ERROR CRÍTICO: Fallo general no capturado.")
        }
    }

    /**
     * **Helper:** Crea el payload completo del comando NDEF, incluyendo los 96 bytes de
     * configuración si el comando es CMD_WRITE_CONFIG (0x04).
     *
     * @return ByteArray? El payload completo del comando, o null si faltan datos de configuración.
     */
    private fun createNdefCommandPayload(commandId: Byte): ByteArray? {
        return if (commandId == CMD_WRITE_CONFIG) {
            val configDataBytes = sharedViewModel.configDataToWrite.value
            if (configDataBytes == null || configDataBytes.size != 96) {
                // Generar mensaje de error y Toast
                sharedViewModel.setUiMessage("ERROR: Comando 0x${CMD_WRITE_CONFIG.toHexString()} solicitado, pero los datos de 96 bytes no están listos en el ViewModel.")
                sharedViewModel.emitWriteStatus("ERROR: Datos de configuración (96B) no disponibles para escribir.")
                null
            } else {
                // CRÍTICO: Concatenar el byte del comando (0x04) + los 96 bytes de datos. (97 bytes total)
                byteArrayOf(commandId) + configDataBytes
            }
        } else {
            // Comandos de lectura/reset (payload de 1 byte)
            byteArrayOf(commandId)
        }
    }


    /**
     * **Primer Scan (Fallback NfcV):** Intenta forzar el formateo NDEF
     * escribiendo solo la Cabecera de Capacidad (CC) en el Bloque 0.
     *
     * Si tiene éxito, el siguiente escaneo permitirá que Android detecte Ndef o NdefFormatable.
     * El comando *no* se escribe en este paso.
     */
    private fun executeNfcVForceFormatOnlyFallback(nfcV: NfcV): Boolean {
        lifecycleScope.launch { LogManager.log("NfcV: Escribiendo solo el Capability Container (CC) en Bloque 0 para forzar el formato NDEF.") }

        // --- Parámetros de la operación ISO 15693 ---
        val flags: Byte = 0x02 // Flags: Direccionamiento simple (no UID), Data Rate High
        val cmdWriteSingleBlock: Byte = 0x21.toByte()
        val NDEF_MAX_SIZE: Byte = 0x40 // Tamaño NDEF de 64 bytes (0x40)

        // Bloque de Capacidad NDEF (Capability Container - CC) (4 bytes)
        // [E1] (Magic Byte), [40] (Version 1.0, Lectura/Escritura), [00] (Max Size High), [40] (Max Size Low: 64 bytes)
        val ccBlock = byteArrayOf(
            0xE1.toByte(),
            0x40.toByte(),
            0x00,
            NDEF_MAX_SIZE
        )

        try {
            if (!nfcV.isConnected) nfcV.connect()

            // Comando: [Flags, CMD_WRITE_SINGLE_BLOCK, Block Address (0x00), Data (CC Block)]
            val writeCCCommand = byteArrayOf(flags, cmdWriteSingleBlock, 0x00) + ccBlock
            val response = nfcV.transceive(writeCCCommand)

            // Validación de respuesta ISO 15693 (vacía o byte de estado 0x00 que indica éxito)
            if (response.isEmpty() || (response.size == 1 && response[0].and(0x01) == 0.toByte())) {
                return true
            } else {
                lifecycleScope.launch { LogManager.log("ERROR NfcV CC: El chip devolvió un error en la escritura del CC. Código: ${response.toHexString()}") }
                return false
            }

        } catch (e: IOException) {
            val errorMsg = "Error NfcV (ISO 15693) al forzar el formateo CC: ${e.message}. El chip podría estar totalmente bloqueado."
            lifecycleScope.launch { LogManager.log(errorMsg) }
            sharedViewModel.setUiMessage(errorMsg)
            // EMISIÓN DE TOAST DE ERROR NfcV
            sharedViewModel.emitWriteStatus("ERROR NfcV: Fallo I/O. El chip podría estar totalmente bloqueado.")
            return false
        } finally {
            if (nfcV.isConnected) {
                try { nfcV.close() } catch (_: IOException) {}
            }
        }
    }


    /**
     * **Primer Scan (Forzado Alto Nivel):** Formatea el TAG y escribe el mensaje NDEF del comando.
     * (Solo se usa si NdefFormatable está disponible)
     */
    private fun executeNdefFormatAndWriteCommand(ndefFormatable: NdefFormatable, commandId: Byte): Boolean {

        // --- PREPARACIÓN DEL PAYLOAD NDEF (Utilizando la función helper) ---
        val fullPayload = createNdefCommandPayload(commandId)
        if (fullPayload == null) {
            // El helper ya emitió el Toast/UI Message de error de datos faltantes.
            return false
        }

        val logMessage = if (commandId == CMD_WRITE_CONFIG) {
            "Escribiendo comando 0x${commandId.toHexString()} con 96 bytes de configuración..."
        } else {
            "Escribiendo comando 0x${commandId.toHexString()} (1 byte)..."
        }

        lifecycleScope.launch { LogManager.log("Primer Scan FORZADO (Alto Nivel): TAG será formateado y escrito. $logMessage") }

        val commandRecord = NdefRecord.createMime(MIME_COMMAND_TYPE, fullPayload)
        val message = NdefMessage(commandRecord)

        try {
            ndefFormatable.connect()
            ndefFormatable.format(message)

            // Éxito de la escritura
            sharedViewModel.setUiMessage("Primer Scan FORZADO ALTO NIVEL OK: Formateado y $logMessage enviado. ¡Acerque el TAG nuevamente para el Segundo Scan!")
            // EMISIÓN DE TOAST DE ESCRITURA FORZADA EXITOSA
            sharedViewModel.emitWriteStatus("Escritura Forzada OK: TAG formateado y comando enviado.")

            // Limpiar el comando de inmediato para evitar el doble escaneo/Toast
            sharedViewModel.clearCommand()

            // Limpiar datos de escritura si la operación fue exitosa
            if (commandId == CMD_WRITE_CONFIG) {
                sharedViewModel.setConfigDataToWrite(null)
            }
            return true
        } catch (e: IOException) {
            val errorMsg = "Error NDEF al formatear/escribir: ${e.message}. Recurriendo a NfcV."
            lifecycleScope.launch { LogManager.log(errorMsg) }
            // EMISIÓN DE TOAST DE ERROR
            sharedViewModel.emitWriteStatus("ERROR NDEF Formato: Fallo I/O. Recurriendo a NfcV.")
            return false
        } finally {
            if (ndefFormatable.isConnected) {
                try { ndefFormatable.close() } catch (_: IOException) {}
            }
        }
    }


    /**
     * **Primer Scan (Estándar):** Escribe el comando como un mensaje NDEF (application/x-cmd).
     * Ahora maneja comandos de 1 byte y el comando de ESCRITURA (0x04) con payload de 96 bytes.
     * (Solo se usa si Ndef está disponible)
     */
    private fun executeNdefWriteCommand(ndef: Ndef, commandId: Byte): Boolean {
        // --- PREPARACIÓN DEL PAYLOAD NDEF (Utilizando la función helper) ---
        val fullPayload = createNdefCommandPayload(commandId)
        if (fullPayload == null) {
            // El helper ya emitió el Toast/UI Message de error de datos faltantes.
            return false
        }

        val logMessage = if (commandId == CMD_WRITE_CONFIG) {
            "Escribiendo comando 0x${commandId.toHexString()} con 96 bytes de configuración..."
        } else {
            "Escribiendo comando 0x${commandId.toHexString()} (1 byte)..."
        }

        val commandRecord = NdefRecord.createMime(MIME_COMMAND_TYPE, fullPayload)
        val message = NdefMessage(commandRecord)

        lifecycleScope.launch {
            LogManager.log("Primer Scan: $logMessage")
        }

        // --- LÓGICA DE ESCRITURA CON MANEJO ROBUSTO DE ERRORES ---
        try {
            // 2. Intentar la conexión
            if (!ndef.isConnected) ndef.connect()

            // 3. Realizar verificaciones previas a la escritura
            if (!ndef.isWritable) {
                val errorMsg = "ERROR: TAG NDEF no es escribible. Verifique el bloqueo del TAG."
                lifecycleScope.launch { LogManager.log(errorMsg) }
                sharedViewModel.setUiMessage(errorMsg)
                // EMISIÓN DE TOAST DE ERROR DE BLOQUEO
                sharedViewModel.emitWriteStatus("ERROR: El TAG está bloqueado. No se puede escribir el comando.")
                return false
            }
            // CRÍTICO: Comprobación de tamaño para el mensaje de 97 bytes
            if (ndef.maxSize < message.toByteArray().size) {
                val errorMsg = "ERROR: Mensaje de comando (${message.toByteArray().size} B) es demasiado grande para el TAG (Max ${ndef.maxSize} B)."
                lifecycleScope.launch { LogManager.log(errorMsg) }
                sharedViewModel.setUiMessage(errorMsg)
                // EMISIÓN DE TOAST DE ERROR DE TAMAÑO
                sharedViewModel.emitWriteStatus("ERROR: El mensaje es demasiado grande para el TAG.")
                return false
            }

            // 4. Realizar la operación de escritura
            ndef.writeNdefMessage(message)

            // 5. Éxito
            sharedViewModel.setUiMessage("Primer Scan OK: $logMessage enviado. ¡Acerque el TAG nuevamente para el Segundo Scan (Leer Respuesta)!")
            // EMISIÓN DE TOAST DE ÉXITO ESTÁNDAR
            sharedViewModel.emitWriteStatus("Escritura NDEF OK. Comando 0x${commandId.toHexString()} enviado. Listo para Escaneo 2.")

            // Limpiar el comando de inmediato para evitar el doble escaneo/Toast
            sharedViewModel.clearCommand()

            // Si la escritura es exitosa, limpiar los datos de escritura.
            if (commandId == CMD_WRITE_CONFIG) {
                sharedViewModel.setConfigDataToWrite(null)
            }
            return true

            // --- BLOQUE CATCH PARA PROBLEMAS DE CONEXIÓN O SEGURIDAD ---
        } catch (e: SecurityException) {
            // Catch: java.lang.SecurityException: Tag is out of date.
            val errorMsg = "Error de Seguridad (TAG Perdido): El TAG se ha movido o desconectado. Acerque el TAG de nuevo para reintentar."
            lifecycleScope.launch { LogManager.log("NFC_WRITE SecurityException (Stale Tag): ${e.message}") }
            sharedViewModel.setUiMessage(errorMsg)
            // EMISIÓN DE TOAST DE ERROR DE CONEXIÓN
            sharedViewModel.emitWriteStatus("ERROR NDEF I/O: Conexión perdida. Reintente.")
            return false

        } catch (e: IOException) {
            // Catch: Lanzado por connect(), writeNdefMessage(), o cualquier otra llamada de tecnología
            val errorMsg = "Error de Conexión (I/O): El TAG se movió o falló la comunicación durante la operación. Acerque el TAG de nuevo para reintentar."
            lifecycleScope.launch { LogManager.log("NFC_WRITE IOException (Connection Lost): ${e.message}") }
            sharedViewModel.setUiMessage(errorMsg)
            // EMISIÓN DE TOAST DE ERROR DE CONEXIÓN
            sharedViewModel.emitWriteStatus("ERROR NDEF I/O: Conexión perdida. Reintente.")
            return false

        } catch (e: Exception) {
            // Catch cualquier otra excepción inesperada
            val errorMsg = "Error Inesperado: Ocurrió un error inesperado durante la escritura NDEF: ${e.message}"
            lifecycleScope.launch { LogManager.log("NFC_WRITE Unexpected error: ${e.message}") }
            sharedViewModel.setUiMessage(errorMsg)
            // EMISIÓN DE TOAST DE ERROR INESPERADO
            sharedViewModel.emitWriteStatus("ERROR Inesperado: Fallo de escritura NDEF.")
            return false

        } finally {
            // 6. Siempre cerrar la conexión
            try {
                if (ndef.isConnected) {
                    ndef.close()
                }
            } catch (closeE: Exception) {
                // Ignorar errores durante el cierre, ya que la operación principal ya terminó o falló.
                lifecycleScope.launch { LogManager.log("Advertencia: Error al cerrar la conexión Ndef: ${closeE.message}") }
            }
        }
    }

    /**
     * **Segundo Scan:** Lee el mensaje NDEF y busca la respuesta (application/x-data).
     */
    private fun executeNdefReadResponse(ndef: Ndef) {
        lifecycleScope.launch { LogManager.log("Segundo Scan: Intentando leer respuesta NDEF (application/x-data)...") }

        try {
            val ndefMessage = ndef.getNdefMessage()
            if (ndefMessage == null) {
                sharedViewModel.setUiMessage("Segundo Scan: NDEF vacío o no formateado. ¿El TAG procesó el comando?")
                return
            }

            var responseFound = false
            for (record in ndefMessage.records) {
                if (record.tnf == NdefRecord.TNF_MIME_MEDIA) {
                    val recordType = record.type.toString(StandardCharsets.US_ASCII)

                    if (recordType == MIME_RESPONSE_TYPE) {
                        val payload = record.payload
                        if (payload.isNotEmpty()) {
                            val originalCommandId = payload[0]
                            val data = payload.copyOfRange(1, payload.size)

                            lifecycleScope.launch {
                                LogManager.log("Segundo Scan OK: Respuesta de 0x${originalCommandId.toHexString()} (Payload size: ${data.size} bytes)")
                            }

                            // El ViewModel distribuirá y emitirá el Toast de éxito (0x81, 0x84, etc.)
                            sharedViewModel.distributeResponseData(originalCommandId, data)
                            sharedViewModel.setUiMessage("Segundo Scan OK: Datos de 0x${originalCommandId.toHexString()} recibidos. Listo para nuevo comando.")
                            responseFound = true
                            break
                        }
                    } else if (recordType == MIME_COMMAND_TYPE) {
                        sharedViewModel.setUiMessage("Segundo Scan: Error de sincronización. El TAG aún contiene el COMANDO (0x${record.payload[0].toHexString()}) y no la RESPUESTA. Vuelva a escanear en unos segundos.")
                        // EMISIÓN DE TOAST DE ERROR DE SINCRONIZACIÓN
                        sharedViewModel.emitWriteStatus("ERROR SINCRO: TAG aún tiene el comando. Escanee de nuevo en 2s.")
                        responseFound = true
                        break
                    }
                }
            }

            if (!responseFound) {
                sharedViewModel.setUiMessage("Segundo Scan: Mensaje NDEF encontrado, pero no se halló el registro de RESPUESTA ($MIME_RESPONSE_TYPE).")
                // EMISIÓN DE TOAST DE ERROR DE RESPUESTA
                sharedViewModel.emitWriteStatus("ERROR NDEF: Registro de Respuesta no encontrado.")
            }

        } catch (e: Exception) {
            val errorMsg = "Error NDEF al leer (Lógica): ${e.message}"
            lifecycleScope.launch { LogManager.log(errorMsg) }
            sharedViewModel.setUiMessage(errorMsg)
            // EMISIÓN DE TOAST DE ERROR INESPERADO
            sharedViewModel.emitWriteStatus("ERROR INESPERADO al leer respuesta NDEF.")
        }
    }
}

// --------------------------------------------------------------------------
// Funciones de utilidad
// --------------------------------------------------------------------------

/** Función de utilidad para convertir ByteArray a String Hexadecimal */
fun ByteArray.toHexString() = joinToString(separator = " ") {
    String.format("%02X", it)
}

/** Función de utilidad para convertir un Byte a String Hexadecimal de dos dígitos. */
fun Byte.toHexString() = String.format("%02X", this)
