package com.example.nfc_reader_01

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel compartido para manejar el estado de la comunicación NFC (comando, datos, logs).
 */
class SharedNfcViewModel : ViewModel() {

    // ----------------------------------------------------------------------
    // ESTADO DEL COMANDO Y DATOS
    // ----------------------------------------------------------------------

    // Comando NFC que se debe enviar en el próximo transceive (inicialmente, leer identidad)
    private val _commandToSend = MutableStateFlow(0x01.toByte())
    val commandToSend: StateFlow<Byte> = _commandToSend

    // Datos que se deben escribir en el TAG (null para comandos de lectura)
    private val _configData = MutableStateFlow<ByteArray?>(null)
    val configData: StateFlow<ByteArray?> = _configData

    // Datos de IDENTIDAD (o datos generales leídos) para el Dashboard.
    private val _identityData = MutableStateFlow<ByteArray?>(null)
    val identityData: StateFlow<ByteArray?> = _identityData

    // ----------------------------------------------------------------------
    // ESTADO DEL NFC TAG Y ESCRITURA
    // ----------------------------------------------------------------------

    // NFC Tag detectado (el valor null indica que no hay Tag o ha sido desconectado)
    private val _nfcTag = MutableStateFlow<String?>(null)
    val nfcTag: StateFlow<String?> = _nfcTag

    // Estado de la operación de escritura para mostrar en un Toast
    private val _writeStatus = MutableStateFlow<String?>(null)
    val writeStatus: StateFlow<String?> = _writeStatus

    // ----------------------------------------------------------------------
    // LOGS DE PROTOCOLO (Para NotificationsFragment)
    // ----------------------------------------------------------------------

    // Lista inmutable de logs para el UI
    private val _protocolLog = MutableStateFlow<List<String>>(emptyList())
    val protocolLog: StateFlow<List<String>> = _protocolLog

    // ----------------------------------------------------------------------
    // MENSAJES DE UI (Para DashboardFragment o mensajes de estado inmediato)
    // ----------------------------------------------------------------------

    // Mensaje de estado inmediato para mostrar en la interfaz (ej. "Esperando TAG...")
    private val _uiMessage = MutableStateFlow("Acerque un TAG para iniciar la lectura...")
    val uiMessage: StateFlow<String> = _uiMessage

    /**
     * Establece un mensaje de estado inmediato para el UI.
     */
    fun setUiMessage(message: String) {
        _uiMessage.value = message
    }

    /**
     * Establece el estado del NFC Tag detectado.
     * @param tagId El ID del Tag o cualquier identificador de su presencia, o null si se desconecta.
     */
    fun setNfcTag(tagId: String?) {
        _nfcTag.value = tagId
        if (tagId != null) {
            setUiMessage("TAG detectado. ID: $tagId")
        } else {
            setUiMessage("Aproxime una etiqueta NFC.")
        }
    }

    /**
     * Establece el mensaje de estado de escritura para mostrar como Toast.
     */
    fun setWriteStatus(status: String?) {
        _writeStatus.value = status
    }

    // ----------------------------------------------------------------------
    // FUNCIONES DE CONTROL
    // ----------------------------------------------------------------------

    /**
     * Establece el próximo comando a ejecutar. Esto es llamado por los Fragments de UI.
     * @param commandId El byte del comando (ej. 0x05 para escritura, 0x04 para lectura config).
     */
    fun sendCommand(commandId: Byte) {
        viewModelScope.launch {
            _commandToSend.value = commandId
            addProtocolLog("Comando 0x${String.format("%02X", commandId)} preparado para el próximo escaneo.")
            setUiMessage("Comando 0x${String.format("%02X", commandId)} preparado. Acerque el TAG ahora.")
        }
    }

    /**
     * Establece los datos de configuración a escribir. Se establece a null para comandos de lectura.
     */
    fun setConfigData(data: ByteArray?) {
        _configData.value = data
        if (data != null) {
            addProtocolLog("Datos de ${data.size} bytes cargados para escritura.")
        }
    }

    /**
     * Añade un mensaje al historial de protocolo.
     */
    fun addProtocolLog(message: String) {
        // Aseguramos que solo guardamos los últimos 50 logs para evitar sobrecargar la memoria
        _protocolLog.update { currentLogs ->
            val newLog = "${getCurrentTimestamp()} - $message"
            val updatedLogs = currentLogs + newLog
            updatedLogs.takeLast(50)
        }
    }

    /**
     * Borra los mensajes del historial de protocolo.
     */
    fun clearProtocolLog() {
        _protocolLog.value = emptyList()
        addProtocolLog("Historial de protocolo limpio.")
    }


    /**
     * Función stub/placeholder para cuando se requiera cambiar de modo.
     */
    fun requestWriteMode() {
        addProtocolLog("Modo de escritura solicitado (IMPLEMENTACIÓN PENDIENTE).")
        setUiMessage("Modo de escritura activado. Acerque el TAG para escribir configuración.")
    }

    // Helper para obtener la marca de tiempo
    private fun getCurrentTimestamp(): String {
        val formatter = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.getDefault())
        return formatter.format(java.util.Date())
    }

    /**
     * Función llamada por MainActivity cuando se recibe una respuesta del TAG.
     * @param responseData El ByteArray de respuesta del TAG.
     * @param success Indica si la comunicación fue exitosa.
     */
    fun handleNfcResponse(responseData: ByteArray?, success: Boolean) {
        if (!success) {
            addProtocolLog("Error en la comunicación NFC (TAG no responde o error de protocolo).")
            setUiMessage("Error de conexión con el TAG. Intente de nuevo.")
            setWriteStatus("Error de comunicación NFC.") // Notificación para Toast
            return
        }

        // Si la respuesta no es nula, es un resultado de lectura.
        if (responseData != null) {
            val command = _commandToSend.value
            addProtocolLog("TAG Response Received: ${responseData.size} bytes for command 0x${String.format("%02X", command)}.")
            setUiMessage("¡Lectura exitosa! Datos de ${responseData.size} bytes recibidos.")

            // Asumimos que la respuesta del TAG siempre contiene el payload completo
            // que el Dashboard necesita para parseAndDisplayData.
            _identityData.value = responseData

            when (command) {
                0x04.toByte() -> { // Comando de Lectura de Configuración (ejemplo)
                    _configData.value = responseData
                    addProtocolLog("Datos de Configuración (${responseData.size} bytes) cargados.")
                }
            }
        } else {
            // Esto ocurre en comandos de escritura donde 'responseData' es null
            setUiMessage("Comando de escritura/acción ejecutado correctamente.")
            setWriteStatus("Escritura completada con éxito.") // Notificación para Toast
        }

        // Después de cualquier acción, volvemos al comando de lectura de identidad por defecto (0x01)
        // para que el próximo escaneo sea seguro.
        _commandToSend.value = 0x01.toByte()
    }
}
