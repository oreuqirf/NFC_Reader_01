package com.example.nfc_reader_01

import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.NdefMessage
import android.nfc.tech.Ndef
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
import java.io.IOException
import java.nio.charset.Charset
import com.example.nfc_reader_01.data.NfcTagInfo // <-- IMPORTAMOS LA NUEVA CLASE DE DATOS

// --------------------------------------------------------------------------
// DEFINICIÓN DE CONSTANTES
// --------------------------------------------------------------------------
private const val TAG_NFC_LOG = "NFC_PROTOCOL"
private const val TAG_NFC_ERROR = "NFC_ERROR"
private const val TAG_NFC_DEBUG = "NFC_DEBUG"

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
        sharedNfcViewModel = ViewModelProvider(this).get(SharedNfcViewModel::class.java)

        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment
        navController = navHostFragment.navController

        val navView: BottomNavigationView = findViewById(R.id.nav_view)
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

        logProtocolActivity("Aplicación iniciada. Lógica NFC configurada para Reader Mode (NDEF).")
    }

    /**
     * Habilita el Reader Mode al entrar en la actividad.
     * Ahora escucha todas las tecnologías compatibles con NDEF (A, B, F, V).
     */
    override fun onResume() {
        super.onResume()
        if (nfcAdapter?.isEnabled == true) {

            // FLAG_READER_NFC_A, B, F, V para capturar todas las tecnologías NDEF compatibles
            val flags = NfcAdapter.FLAG_READER_NFC_A or
                    NfcAdapter.FLAG_READER_NFC_B or
                    NfcAdapter.FLAG_READER_NFC_F or
                    NfcAdapter.FLAG_READER_NFC_V

            val options = Bundle()

            nfcAdapter?.enableReaderMode(
                this,      // La actividad que recibe el callback
                this,      // La instancia de NfcAdapter.ReaderCallback (esta clase)
                flags,     // Las tecnologías que buscamos
                options    // Opciones adicionales
            )
            logProtocolActivity("Reader Mode habilitado para tecnologías NDEF.")
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
        if (tag == null) {
            Log.e(TAG_NFC_ERROR, "onTagDiscovered devolvió un objeto Tag nulo.")
            return
        }

        logProtocolActivity("TAG detectado vía Reader Mode. Intentando obtener NDEF...")
        handleTagDiscovery(tag)
    }

    /**
     * -------------------------------------------------------------------
     * LÓGICA DE COMUNICACIÓN NFC (LECTURA NDEF)
     * -------------------------------------------------------------------
     */
    private fun handleTagDiscovery(tag: Tag) {
        // Lanzar una corrutina para realizar operaciones de NFC (I/O)
        lifecycleScope.launch(Dispatchers.IO) {

            val tagIdHex = tag.id.toHexString()
            // Obtener el nombre simple de la primera tecnología detectada
            val primaryTech = tag.techList.firstOrNull()?.substringAfterLast('.') ?: "Unknown"

            // --- 1. CONFIGURACIÓN INICIAL DEL ESTADO DEL TAG ---
            // Creamos el objeto NfcTagInfo con los datos iniciales
            var tagInfo = NfcTagInfo(
                tagIdHex = tagIdHex,
                techType = primaryTech,
                ndefMessage = null // Se actualizará si la lectura NDEF es exitosa
            )

            // Enviamos el objeto al ViewModel. Esto reemplaza los antiguos setNfcTagId y setNfcTechType.
            sharedNfcViewModel.setNfcTagInfo(tagInfo)
            logProtocolActivity("TAG detectado. ID: $tagIdHex. Tech: $primaryTech")
            // --------------------------------------------------

            // 2. Intentar obtener la tecnología Ndef
            val ndef = Ndef.get(tag)

            if (ndef == null) {
                logProtocolActivity("Etiqueta detectada ($tagIdHex), pero no soporta el formato NDEF.")
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Error: Etiqueta detectada, pero no es NDEF compatible.", Toast.LENGTH_LONG).show()
                }
                // Limpiamos todo el objeto NfcTagInfo
                sharedNfcViewModel.setNfcTagInfo(null)
                return@launch
            }

            Log.i(TAG_NFC_LOG, "TAG NDEF detectado. Iniciando conexión...")

            var responseMessage: NdefMessage? = null
            var success = false

            try {
                // Conectar al TAG
                ndef.connect()

                // Intentar leer el mensaje NDEF
                responseMessage = ndef.ndefMessage
                success = true // Si la conexión y la lectura no lanzaron excepción

                logProtocolActivity("Lectura NDEF finalizada con éxito.")

                // 3. Procesar la respuesta para el Toast (lógica visual)
                val messageText = if (responseMessage != null) {
                    // ... (Lógica de extracción de texto para el Toast)
                    val firstRecord = responseMessage.records.firstOrNull()
                    if (firstRecord != null && firstRecord.tnf == 1.toShort()) { // TNF_WELL_KNOWN
                        val textPayload = firstRecord.payload
                        val textEncoding = if ((textPayload[0].toInt() and 0x80) == 0) "UTF-8" else "UTF-16"
                        val languageCodeLength = textPayload[0].toInt() and 0x3F
                        String(
                            textPayload,
                            1 + languageCodeLength,
                            textPayload.size - 1 - languageCodeLength,
                            Charset.forName(textEncoding)
                        ).take(30) + "..."
                    } else {
                        "Mensaje NDEF con ${responseMessage.records.size} registro(s) no text."
                    }
                } else {
                    "Etiqueta NDEF vacía."
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Lectura NDEF OK. $messageText", Toast.LENGTH_LONG).show()
                }

            } catch (e: IOException) {
                // IOException captura fallos de I/O y pérdida de etiqueta
                val errorMsg = "Fallo NDEF: Se perdió la etiqueta durante la conexión o lectura. ${e.message}"
                Log.e(TAG_NFC_ERROR, errorMsg, e)

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, errorMsg, Toast.LENGTH_LONG).show()
                }
                success = false

            } catch (e: Exception) {
                // Manejo de otros errores inesperados
                val errorMsg = "Error inesperado durante la lectura NDEF: ${e.message}"
                Log.e(TAG_NFC_ERROR, errorMsg, e)

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, errorMsg, Toast.LENGTH_LONG).show()
                }
                success = false
            } finally {
                // 4. Asegurar el cierre de la conexión
                try {
                    if (ndef.isConnected) {
                        ndef.close()
                        logProtocolActivity("Conexión NDEF cerrada.")
                    }
                } catch (e: IOException) {
                    // Ignorar errores al cerrar
                }

                // 5. Informar el resultado del mensaje NDEF al ViewModel
                if (success) {
                    // Actualiza solo el campo ndefMessage del objeto NfcTagInfo ya existente
                    sharedNfcViewModel.updateNdefMessage(responseMessage)
                } else {
                    // Si falló, aseguramos que no haya un mensaje NDEF en el estado
                    sharedNfcViewModel.updateNdefMessage(null)
                }

                // 6. Limpiar el estado completo del TAG (usando el nuevo método)
                sharedNfcViewModel.setNfcTagInfo(null)
            }
        } // Fin de lifecycleScope.launch
    }


    // ----------------------------------------------------------------
    // IMPLEMENTACIONES DE NfcInteractionListener (desde Fragments)
    // ----------------------------------------------------------------
    // Estas funciones son llamadas por los Fragments para interactuar con la actividad
    // y solicitar una acción NFC.

    override fun navigateToDashboard() {
        navController.navigate(R.id.navigation_dashboard)
        logProtocolActivity("Navegación solicitada al Dashboard (Lector).")
    }

    override fun requestNextCommand(commandId: Byte) {
        val message = "NFC Command 0x${String.format("%02X", commandId)} solicitado, pero estamos en modo NDEF. Solo lectura estándar."
        logProtocolActivity(message)

        Toast.makeText(this, "Modo NDEF activo: Solo se permite la lectura estándar.", Toast.LENGTH_LONG).show()

        // sharedNfcViewModel.sendCommand(commandId) // Comentado para evitar comandos no soportados
    }

    override fun requestWriteConfig() {
        val message = "Write configuration requested. Write operations require specific NDEF logic."
        logProtocolActivity(message)
        // sharedNfcViewModel.requestWriteMode() // Mantener si la lógica de escritura NDEF se implementa después

        Toast.makeText(this, "La escritura NDEF no está implementada en este modo.", Toast.LENGTH_LONG).show()
    }

    /**
     * Registra la actividad en Logcat y la envía al SharedNfcViewModel.
     */
    private fun logProtocolActivity(message: String){
        Log.i(TAG_NFC_LOG, message)
        sharedNfcViewModel.addProtocolLog(message)
    }

    /**
     * Función de extensión para convertir un ByteArray a una cadena hexadecimal.
     */
    private fun ByteArray.toHexString(): String = joinToString(" ") { "%02x".format(it) }
}
