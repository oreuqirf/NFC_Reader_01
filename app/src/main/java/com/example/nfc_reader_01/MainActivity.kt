package com.example.nfc_reader_01

import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.TagLostException
import android.nfc.tech.NfcV
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.lifecycleScope

// --------------------------------------------------------------------------
// DEFINICIÓN DE CONSTANTES
// --------------------------------------------------------------------------
private const val TAG_NFC_LOG = "NFC_PROTOCOL"
private const val TAG_NFC_ERROR = "NFC_ERROR"
private const val TAG_NFC_DEBUG = "NFC_DEBUG"
// CONSTANTE RE-AGREGADA: Aumentamos el timeout a 3000ms para mayor seguridad.
private const val NFCV_TRANSCEIVE_TIMEOUT_MS = 3000

// La clase debe implementar NfcAdapter.ReaderCallback para usar Reader Mode
class MainActivity : AppCompatActivity(), NfcInteractionListener, NfcAdapter.ReaderCallback {

    private lateinit var navController: NavController
    private lateinit var sharedNfcViewModel: SharedNfcViewModel

    // Propiedades NFC
    private var nfcAdapter: NfcAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        // ----------------------------------------------------------------
        // 1. INICIALIZACIÓN: ViewModel, Navegación y UI
        // ----------------------------------------------------------------
        // El ViewModel es scopeado a la Activity para que los Fragments compartan estado
        sharedNfcViewModel = ViewModelProvider(this).get(SharedNfcViewModel::class.java)

        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment
        navController = navHostFragment.navController

        val navView: BottomNavigationView = findViewById(R.id.nav_view)
        // Corrección: Se utiliza solo la configuración correcta del NavController.
        navView.setupWithNavController(navController)

        // ----------------------------------------------------------------
        // 2. INICIALIZACIÓN: Configuración NFC
        // ----------------------------------------------------------------
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)

        if (nfcAdapter == null) {
            Toast.makeText(this, "NFC no soportado en este dispositivo.", Toast.LENGTH_LONG).show()
        } else if (nfcAdapter?.isEnabled == false) {
            Toast.makeText(this, "NFC desactivado. Por favor, actívelo.", Toast.LENGTH_LONG).show()
        }

        logProtocolActivity("Aplicación iniciada. Lógica NFC configurada para Reader Mode.")
    }

    /**
     * Habilita el Reader Mode al entrar en la actividad.
     * FLAGS: Solo NfcV.
     */
    override fun onResume() {
        super.onResume()
        if (nfcAdapter?.isEnabled == true) {

            // Usamos FLAG_READER_NFC_V para escuchar estrictamente tags ISO 15693 (NfcV)
            val flags = NfcAdapter.FLAG_READER_NFC_V
            val options = Bundle()

            nfcAdapter?.enableReaderMode(
                this,      // La actividad que recibe el callback
                this,      // La instancia de NfcAdapter.ReaderCallback (esta clase)
                flags,     // Las tecnologías que buscamos
                options    // Opciones adicionales (ej. polling delay)
            )
            logProtocolActivity("Reader Mode habilitado estrictamente para NfcV (Bypass al error de Intent).")
        }
    }

    /**
     * Deshabilita el Reader Mode al salir de la actividad.
     */
    override fun onPause() {
        super.onPause()
        nfcAdapter?.disableReaderMode(this)
        logProtocolActivity("Reader Mode deshabilitado.")
    }

    // -------------------------------------------------------------------
    // MÉTODO REQUERIDO POR NfcAdapter.ReaderCallback
    // -------------------------------------------------------------------

    /**
     * Se llama cuando se detecta un TAG NFC mientras el Reader Mode está activo.
     */
    override fun onTagDiscovered(tag: Tag?) {
        if (tag != null) {
            logProtocolActivity("TAG detectado vía Reader Mode. Iniciando transacción...")
            handleTagDiscovery(tag)
        } else {
            Log.e(TAG_NFC_ERROR, "onTagDiscovered devolvió un objeto Tag nulo.")
        }
    }

    /**
     * -------------------------------------------------------------------
     * LÓGICA DE COMUNICACIÓN NFC (EJECUCIÓN DEL COMANDO)
     * -------------------------------------------------------------------
     */
    private fun handleTagDiscovery(tag: Tag) {
        // Lanzar una corrutina para realizar operaciones de red y NFC (I/O)
        lifecycleScope.launch(Dispatchers.IO) {

            // 1. Lectura segura del LiveData en el HILO PRINCIPAL
            val (command: Byte?, dataToWriteRaw: ByteArray?) = withContext(Dispatchers.Main) {
                // Leemos directamente del ViewModel compartido de la Activity
                Pair(sharedNfcViewModel.commandToSend.value, sharedNfcViewModel.configData.value)
            }

            // Verificación y Lógica Defensiva de Payload
            if (command == null) {
                Log.e(TAG_NFC_ERROR, "Comando nulo. Abortando transceive.")
                return@launch
            }

            // **CORRECCIÓN DEFENSIVA EXTENDIDA**: Incluir el 0x03.
            // Si el payload es NULL para un comando de lectura (0x01, 0x02, 0x03),
            // forzamos el payload a ser el bloque inicial (0x00).
            val isReadCommandRequiringBlockIndex =
                command == 0x01.toByte() || command == 0x02.toByte() || command == 0x03.toByte()

            val finalDataToWrite = if (dataToWriteRaw == null && isReadCommandRequiringBlockIndex) {
                Log.w(TAG_NFC_DEBUG, "Payload nulo detectado para comando de lectura (0x${String.format("%02X", command)}). Forzando a [0x00].")
                byteArrayOf(0x00.toByte())
            } else {
                dataToWriteRaw
            }

            val commandHex = String.format("%02X", command)
            val tagIdHex = tag.id.toHexString()
            val payloadSize = finalDataToWrite?.size ?: 0

            // Log final para confirmar el valor que se va a usar
            Log.d(TAG_NFC_DEBUG, "DataToWrite (Payload) en handleTagDiscovery (Final): ${finalDataToWrite?.toHexString() ?: "NULL"}")

            // 2. Notificar inmediatamente que el TAG fue capturado
            sharedNfcViewModel.setNfcTag(tagIdHex)
            logProtocolActivity("TAG ${tagIdHex} detectado. Comando en espera: 0x$commandHex.")


            // 3. Intentar obtener la instancia NfcV
            val nfcvTag = NfcV.get(tag)

            if (nfcvTag == null) {
                logProtocolActivity("Error inesperado: El TAG detectado no se pudo convertir a NfcV a pesar del filtro.")
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "TAG no compatible (Se requiere NfcV).", Toast.LENGTH_LONG).show()
                }
                sharedNfcViewModel.setNfcTag(null)
                return@launch
            }

            // >> AJUSTE DEL TIMEOUT NfcV (RE-IMPLEMENTADO CON SINTAXIS EXPLÍCITA DE JAVA)
            try {
                // Si la propiedad 'timeout' de Kotlin falla, usamos el método explícito de Java, getTimeout().
                // NfcV hereda estos métodos de BasicTagTechnology.
                if (nfcvTag.getTimeout() < NFCV_TRANSCEIVE_TIMEOUT_MS) {
                    // Usamos el método explícito de Java, setTimeout(), para forzar la resolución.
                    nfcvTag.setTimeout(NFCV_TRANSCEIVE_TIMEOUT_MS)
                    Log.d(TAG_NFC_DEBUG, "Timeout de NfcV ajustado a ${NFCV_TRANSCEIVE_TIMEOUT_MS}ms para evitar TagLostException.")
                }
            } catch (e: Exception) {
                // Capturamos la excepción si sigue fallando la referencia, pero no detenemos la ejecución.
                Log.e(TAG_NFC_ERROR, "Fallo (esperado/compilador) al ajustar el timeout de NfcV. Usando valor por defecto. Error: ${e.message}")
            }

            // Ejecutar la comunicación NFC
            var response: ByteArray? = null
            var success = false

            try {
                // Conectar al TAG
                nfcvTag.connect()

                if (nfcvTag.isConnected) {
                    // LOG CORREGIDO: Usar el tamaño real del payload
                    logProtocolActivity("Conectado a TAG NfcV. Enviando comando 0x$commandHex (Payload: $payloadSize bytes)...")

                    // Construir el comando ISO 15693 completo
                    val commandPayload = buildNfcCommand(command, finalDataToWrite, tag.id)

                    // LÍNEA CRÍTICA: transceive
                    response = nfcvTag.transceive(commandPayload)
                    success = true

                    // Notificación en el hilo principal
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "Comando 0x$commandHex enviado. Procesando respuesta...", Toast.LENGTH_SHORT).show()
                    }

                } else {
                    logProtocolActivity("Fallo al conectar con el TAG, nfcvTag.isConnected es falso.")
                }
            } catch (e: Exception) {
                // Manejo de excepciones (ej. I/O Exception por desconexión o comando inválido)
                Log.e(TAG_NFC_ERROR, "Error en transceive: ${e.message}", e)

                // CORRECCIÓN: Manejo específico de TagLostException
                when (e) {
                    is TagLostException -> {
                        val userMessage = "Error de Conexión: La comunicación con el chip se perdió (Timeout del sistema). Mueva el móvil más cerca del TAG y manténgalo inmóvil para el siguiente intento."
                        logProtocolActivity("FATAL ERROR: $userMessage")
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@MainActivity, userMessage, Toast.LENGTH_LONG).show()
                        }
                    }
                    else -> {
                        logProtocolActivity("FATAL ERROR: Fallo en transceive o conexión: ${e.message}")
                    }
                }
            } finally {
                try {
                    // CERRAR LA CONEXIÓN es ABSOLUTAMENTE CRUCIAL
                    if (nfcvTag.isConnected) {
                        nfcvTag.close()
                        logProtocolActivity("Conexión con TAG cerrada.")
                    }
                } catch (e: Exception) {
                    Log.e(TAG_NFC_ERROR, "Error al intentar cerrar conexión: ${e.message}")
                }

                // 4. Informar el resultado de la comunicación al ViewModel
                sharedNfcViewModel.handleNfcResponse(response, success)

                // 5. Limpiar el estado del TAG
                sharedNfcViewModel.setNfcTag(null)
            }
        } // Fin de lifecycleScope.launch
    }

    /**
     * Construye el ByteArray del comando a enviar (ISO 15693: Flags + UID + Command + Data).
     */
    private fun buildNfcCommand(commandId: Byte, data: ByteArray?, tagUid: ByteArray): ByteArray {
        // Flags ajustados a 0x22 (Modo Direccionado y High Data Rate)
        val flags = 0x22.toByte() // Request Flags: Addressed mode (Bit 5), High Data Rate (Bit 1)
        val reversedTagUid = tagUid.reversedArray() // NfcV usa el UID en orden invertido

        val commandList = mutableListOf<Byte>()

        // 1. Flags
        commandList.add(flags)

        // 2. UID (8 bytes, invertido) - SOLO se incluye porque Address Flag (0x20) está encendido.
        commandList.addAll(reversedTagUid.toTypedArray())

        // 3. Command ID
        commandList.add(commandId)

        // 4. Payload Data
        data?.forEach { commandList.add(it) }

        val payloadHex = commandList.toByteArray().toHexString()
        logProtocolActivity("Comando ISO 15693 construido (Flags: 0x${String.format("%02X", flags)} + UID + Command + Data): $payloadHex")

        return commandList.toByteArray()
    }

    // ----------------------------------------------------------------
    // IMPLEMENTACIONES DE NfcInteractionListener (desde Fragments)
    // ----------------------------------------------------------------
    override fun navigateToDashboard() {
        navController.navigate(R.id.navigation_dashboard)
        logProtocolActivity("Navegación solicitada al Dashboard (Lector).")
    }

    override fun requestNextCommand(commandId: Byte) {
        val message = "NFC Command requested: 0x${String.format("%02X", commandId)}"
        logProtocolActivity(message)
        sharedNfcViewModel.sendCommand(commandId)
    }

    override fun requestWriteConfig() {
        val message = "Write configuration requested."
        logProtocolActivity(message)
        sharedNfcViewModel.requestWriteMode() // Esta función solo loguea por ahora
    }

    /**
     * Registra la actividad en Logcat y la envía al SharedNfcViewModel.
     */
    private fun logProtocolActivity(message: String){
        Log.i(TAG_NFC_LOG, message)
        sharedNfcViewModel.addProtocolLog(message)
    }

    private fun ByteArray.toHexString(): String = joinToString(" ") { "%02x".format(it) }
}
