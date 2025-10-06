package com.example.nfc_reader_01

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.example.nfc_reader_01.utils.LogManager

// Nota: La función de extensión 'toHexString()' ha sido eliminada de aquí para evitar
// conflictos de sobrecarga, ya que está definida en MainActivity.kt.

/**
 * Clase de datos para una entrada de registro de protocolo.
 */
data class LogEntry(
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * ViewModel compartido para manejar el estado NFC, los comandos y las respuestas de datos
 * entre la Activity (NFC) y los Fragmentos (UI).
 */
class SharedNfcViewModel : ViewModel() {

    // --- Variables de Estado General (StateFlow - Emiten el último valor conocido) ---

    // Almacena información sobre la etiqueta NFC actualmente conectada.
    private val _nfcTagInfo = MutableStateFlow<String?>(null)
    val nfcTagInfo: StateFlow<String?> = _nfcTagInfo.asStateFlow()

    private val _uiMessage = MutableStateFlow("Esperando conexión NFC...")
    val uiMessage: StateFlow<String> = _uiMessage.asStateFlow()

    // --- SharedFlow para eventos de una sola vez (Toast/SnackBar) ---
    private val _writeStatus = MutableSharedFlow<String>(replay = 0)
    /**
     * SharedFlow para notificaciones puntuales (generalmente Toast o SnackBar)
     * sobre el resultado de CUALQUIER operación (LECTURA, ESCRITURA o RESET).
     */
    val writeStatus: SharedFlow<String> = _writeStatus.asSharedFlow()

    // --- LOGGING DE PROTOCOLO ---
    private val _protocolLog = MutableStateFlow<List<LogEntry>>(emptyList())
    val protocolLog: StateFlow<List<LogEntry>> = _protocolLog.asStateFlow()

    // --- Comandos y Respuestas de Datos (Flujos) ---

    // El comando pendiente. El valor debe ser null tras la ejecución.
    private val _pendingCommand = MutableStateFlow<Byte?>(null)
    val pendingCommand: StateFlow<Byte?> = _pendingCommand.asStateFlow()

    // Datos de configuración a escribir (para CMD_WRITE_CONFIG).
    private val _configDataToWrite = MutableStateFlow<ByteArray?>(null)
    val configDataToWrite: StateFlow<ByteArray?> = _configDataToWrite.asStateFlow()

    // Flujos para las respuestas de datos de los comandos (SharedFlow - eventos puntuales)
    private val _identityResponseData = MutableSharedFlow<ByteArray?>(replay = 0)
    val identityResponseData: SharedFlow<ByteArray?> = _identityResponseData.asSharedFlow()

    private val _processResponseData = MutableSharedFlow<ByteArray?>(replay = 0)
    val processResponseData: SharedFlow<ByteArray?> = _processResponseData.asSharedFlow()

    private val _engineeringResponseData = MutableSharedFlow<ByteArray?>(replay = 0)
    val engineeringResponseData: SharedFlow<ByteArray?> = _engineeringResponseData.asSharedFlow()


    // Respuesta de Configuración (StateFlow porque es dato persistente)
    private val _configurationResponseData = MutableStateFlow<ByteArray?>(null)
    val configurationResponseData: StateFlow<ByteArray?> = _configurationResponseData.asStateFlow()

    /**
     * Resetea el StateFlow de la respuesta de Configuración a null para evitar
     * el re-procesamiento de datos.
     */
    fun clearConfigurationResponseData() {
        _configurationResponseData.value = null
    }


    /**
     * Establece la información de la etiqueta NFC (solo el ID).
     */
    fun setNfcTagInfo(info: String?) {
        _nfcTagInfo.value = info
    }

    /**
     * Establece el mensaje de feedback para la UI.
     */
    fun setUiMessage(message: String) {
        _uiMessage.value = message
    }

    /**
     * Emite el estado de escritura a través del SharedFlow.
     * Ahora también usado para notificaciones de lectura exitosas.
     */
    fun emitWriteStatus(status: String) {
        // SharedFlow requiere un contexto de corrutina para emitir
        viewModelScope.launch {
            _writeStatus.emit(status)
        }
    }

    /**
     * Función que simula la lectura de una etiqueta NFC.
     */
    fun onNfcTagDetected(tagId: String) {
        // Usamos viewModelScope para lanzar la corrutina y llamar a la función suspend del LogManager.
        viewModelScope.launch {
            LogManager.log("Etiqueta NFC detectada con ID: $tagId")

            // Lógica de procesamiento...
            processTag(tagId)
        }
    }

    private fun processTag(id: String) {
        viewModelScope.launch {
            if (id.isEmpty()) {
                LogManager.log("ERROR: La etiqueta detectada no contenía datos válidos.")
            } else {
                LogManager.log("INFO: Iniciando protocolo de lectura de datos.")
                // ... más lógica de NFC ...
            }
        }
    }

    /**
     * Función auxiliar para obtener el nombre legible del comando.
     */
    private fun getCommandName(code: Byte): String {
        // Códigos de comando (no de respuesta)
        return when (code) {
            0x01.toByte() -> "Leer Identidad"
            0x02.toByte() -> "Leer Proceso"
            0x03.toByte() -> "Leer Configuración"
            0x04.toByte() -> "Escribir Configuración"
            0x05.toByte() -> "Leer Ingeniería"
            0x0A.toByte() -> "Reset de Fábrica"
            else -> "Comando Desconocido"
        }
    }

    /**
     * Emite un comando para que la Activity lo recoja y lo envíe por NFC.
     *
     * MODIFICACIÓN CLAVE: Emite un mensaje de estado inmediato para que la UI (Toast)
     * notifique al usuario que el comando ha sido iniciado.
     */
    fun sendCommand(commandCode: Byte) {
        val commandName = getCommandName(commandCode)

        // 1. Notificación inmediata del envío del comando
        // emitWriteStatus("COMANDO INICIADO: $commandName. Acerque el TAG...")

        // 2. Establecer el comando pendiente
        _pendingCommand.value = commandCode

        viewModelScope.launch {
            // Usamos la función toHexString disponible globalmente (definida en MainActivity.kt)
            LogManager.log("Comando 0x${commandCode.toHexString()} ($commandName) enviado al buffer.")
        }
    }

    /**
     * Limpia el comando pendiente y los datos de configuración después de su ejecución.
     */
    fun clearCommand() {
        _pendingCommand.value = null
        _configDataToWrite.value = null
    }

    /**
     * Establece los datos de configuración binarios a escribir.
     */
    fun setConfigDataToWrite(data: ByteArray?) {
        _configDataToWrite.value = data
    }


    /**
     * Recibe y distribuye los datos de respuesta binarios basándose en el código de respuesta.
     * * El primer byte del payload de la respuesta NDEF (payload[0]) es el código de respuesta (0x8X).
     */
    fun distributeResponseData(responseCode: Byte, data: ByteArray) {
        viewModelScope.launch {
            when (responseCode) {

                // --- MANEJO DE RESPUESTAS DE LECTURA (Activa Toast) ---

                // Comando 0x01 (Identity) -> Respuesta 0x81 (READ)
                0x81.toByte() -> {
                    _identityResponseData.emit(data)
                    emitWriteStatus("Lectura Identidad OK..")
                }
                // Comando 0x02 (Process) -> Respuesta 0x82 (READ)
                0x82.toByte() -> {
                    _processResponseData.emit(data)
                    emitWriteStatus("Lectura Proceso OK.")
                }
                // Comando 0x05 (Engineering) -> Respuesta 0x85 (READ)
                0x85.toByte() -> {
                    _engineeringResponseData.emit(data)
                    emitWriteStatus("Lectura Ingeniería OK")
                }
                // Comando 0x03 (Read Config) -> Respuesta 0x83 (READ)
                0x83.toByte() -> {
                    _configurationResponseData.emit(data)
                    emitWriteStatus("Lectura Configuración OK.")
                }

                // --- MANEJO DE RESPUESTAS DE ESCRITURA/RESET (Activa Toast) ---

                // Comando 0x04 (Write Config) -> Respuesta 0x84 (WRITE SUCCESS)
                0x84.toByte() -> {
                    emitWriteStatus("ESCRITURA EXITOSA (0x84).")
                    LogManager.log("ESCRITURA OK (0x84) en TAG.")
                }
                // Comando 0x0A (Factory Reset) -> Respuesta 0x8A (RESET SUCCESS)
                0x8A.toByte() -> {
                    emitWriteStatus("RESET EXITOSO (0x8A).")
                    LogManager.log("RESET DE FÁBRICA OK (0x8A) en TAG.")
                }

                // --- FIN MANEJO ESCRITURA ---

                else -> {
                    // Para cualquier otro código de respuesta no esperado (error de protocolo)
                    // Usamos la función toHexString disponible globalmente (definida en MainActivity.kt)
                    val message = "ERROR DE PROTOCOLO: Código de respuesta desconocido: 0x${responseCode.toHexString()}"
                    LogManager.log(message)
                    setUiMessage(message)
                    // Se emite un Toast de error de protocolo para que el usuario lo vea.
                    emitWriteStatus(message)
                }
            }
        }
    }
}
