package com.example.nfc_reader_01

import android.nfc.NdefMessage
import android.nfc.Tag
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update


/**
 * ViewModel compartido para manejar los datos leídos por NFC y el estado del protocolo
 * a lo largo de toda la Activity y sus Fragments.
 *
 * Utiliza LiveData para que los Fragments puedan observar los cambios en tiempo real.
 */
class SharedNfcViewModel : ViewModel() {

    // --- ESTADO DE LECTURA (Respuesta del Tag) ---
    private val _tagResponseData = MutableStateFlow<ByteArray?>(null)
    val tagResponseData: StateFlow<ByteArray?> = _tagResponseData.asStateFlow()

    fun setTagResponse(data: ByteArray) {
        // Nota: Esta función debe usarse solo cuando isWriteOperationPending es FALSE.
        _tagResponseData.value = data
    }


    // =====================================================================
    // SOLICITUD DE ESCRITURA DE CONFIGURACIÓN (Comando 0x04)
    // =====================================================================

    private val _writeStatus = MutableLiveData<String?>(null)
    val writeStatus: LiveData<String?> = _writeStatus

    // --- ESTADO DE ESCRITURA ---
    private val _writeMessageRequest = MutableStateFlow<NdefMessage?>(null)
    val writeMessageRequest: StateFlow<NdefMessage?> = _writeMessageRequest.asStateFlow()

    // 🚨 NUEVA BANDERA CRÍTICA DE PRIORIDAD 🚨
    // Indica que hay una operación de escritura pendiente (Comando 0x04) y DEBE ser priorizada.
    private val _isWriteOperationPending = MutableStateFlow(false)
    val isWriteOperationPending: StateFlow<Boolean> = _isWriteOperationPending.asStateFlow()


    /**
     * Establece el mensaje NDEF a escribir. Al hacerlo, activa la bandera de prioridad.
     * Esto debe ser llamado cuando el usuario presiona "Guardar Configuración".
     * * @param message El NdefMessage que contiene el comando 0x04 y los datos serializados.
     */
    fun setWriteMessageRequest(message: NdefMessage) {
        // 1. Establece la solicitud de escritura
        _writeMessageRequest.value = message

        // 2. ACTIVA LA BANDERA DE PRIORIDAD
        _isWriteOperationPending.value = true
    }

    /**
     * Se llama inmediatamente después de que la escritura haya sido intentada/disparada
     * en el manejador NFC de la Activity (sin importar si tuvo éxito o no).
     */
    fun clearWriteRequest() {
        _writeMessageRequest.update { null }
        _isWriteOperationPending.update { false }
    }


    // =====================================================================
    // ESTADO DEL NFC Y TAG
    // =====================================================================

    private val _nfcStatus = MutableLiveData<Boolean>(false)
    val nfcStatus: LiveData<Boolean> = _nfcStatus

    private val _nfcTag = MutableLiveData<Tag?>(null)
    val nfcTag: LiveData<Tag?> = _nfcTag

    fun setNfcStatus(isEnabled: Boolean) {
        _nfcStatus.value = isEnabled
    }

    fun setNfcTag(tag: Tag?) {
        _nfcTag.value = tag
    }

    // =====================================================================
    // DATOS PARSEADOS RECIBIDOS DEL TAG
    // =====================================================================

    // LiveData individuales para cada bloque de datos, facilitando la observación
    private val _identityData = MutableLiveData<IdentityData?>(null)
    val identityData: LiveData<IdentityData?> = _identityData

    private val _processData = MutableLiveData<ProcessData?>(null)
    val processData: LiveData<ProcessData?> = _processData

    private val _configData = MutableLiveData<ConfigurationData?>(null)
    val configData: LiveData<ConfigurationData?> = _configData

    private val _engineeringData = MutableLiveData<EngineeringData?>(null)
    val engineeringData: LiveData<EngineeringData?> = _engineeringData


    /**
     * Función unificada para actualizar los datos leídos del TAG.
     * Solo los datos no nulos se actualizan.
     * @param identity: Datos del bloque 0x81.
     * @param process: Datos del bloque 0x82.
     * @param config: Datos del bloque 0x83.
     * @param config: Datos del bloque 0x85.
     */
    fun setNdefRecords(
        identity: IdentityData? = null,
        process: ProcessData? = null,
        config: ConfigurationData? = null,
        engineering: EngineeringData? = null
    ) {
        if (identity != null) {
            _identityData.value = identity
        }
        if (process != null) {
            _processData.value = process
        }
        if (config != null) {
            _configData.value = config
        }
        if (engineering != null) {
            _engineeringData.value = engineering
        }

        // Después de una lectura exitosa, navegamos al Dashboard (si el TAG está conectado)
        if (_nfcTag.value != null && (identity != null || process != null || config != null || engineering != null)) {
            // No hacemos la navegación aquí, se delega a MainActivity a través del listener
            // (Esta es solo una señal interna de que los datos han cambiado)
        }
    }

    // =====================================================================
    // SOLICITUDES DE COMANDO (App -> TAG)
    // =====================================================================

    // Almacena el próximo comando de LECTURA (0x01, 0x02, 0x03) solicitado por un Fragment.
    private val _nextCommandRequest = MutableLiveData<Byte?>(null)
    val nextCommandRequest: LiveData<Byte?> = _nextCommandRequest

    /**
     * Establece el próximo comando de LECTURA a ser enviado en el siguiente ciclo NFC.
     */
    fun requestNextCommand(command: Byte) {
        _nextCommandRequest.value = command
        addProtocolLog("Solicitud de Comando 0x${String.format("%02X", command)} en cola.")
    }

    /**
     * Limpia el comando pendiente después de que MainActivity lo haya enviado.
     */
    fun resetNextCommandRequest() {
        _nextCommandRequest.value = null
    }


    /**
     * Prepara el mensaje de escritura de configuración (Comando 0x04)
     * utilizando los datos de configuración (configData) actualmente almacenados en el ViewModel.
     */
    fun requestWriteConfig() {
        val currentConfig = _configData.value
        if (currentConfig != null) {

            // 🌟 CORRECCIÓN CLAVE: Serializamos el objeto ConfigurationData a ByteArray (96 bytes)
            // utilizando la nueva función binaria de NfcDataParser.
            val dataBytes = NfcDataParser.serializeConfigData(currentConfig)

            // Pasamos los 96 bytes del payload serializado binariamente al creador del mensaje NDEF.
            val ndefMessage = NfcDataParser.createWriteConfigMessage(dataBytes)

            _writeMessageRequest.value = ndefMessage
            setWriteStatus("Configuración lista para ser escrita (Comando 0x04).")
            addProtocolLog("Solicitud de Comando 0x04 (Escritura) en cola. Payload: ${dataBytes.size} bytes.")
        } else {
            setWriteStatus("ERROR: No hay datos de configuración (0x83) cargados para escribir.")
            addProtocolLog("ERROR: Intento de escribir configuración sin datos cargados.")
        }
    }



    /**
     * Limpia la solicitud de escritura después de que MainActivity la haya ejecutado.
     */
    fun resetWriteMessageRequest() {
        _writeMessageRequest.value = null
    }

    fun onWriteSuccess() {
        setWriteStatus("¡ESCRITURA EXITOSA! Datos enviados al TAG.")
    }

    fun onWriteFailure() {
        setWriteStatus("FALLO DE ESCRITURA. Revisar logs.")
    }

    fun setWriteStatus(status: String?) {
        _writeStatus.value = status
    }

    // =====================================================================
    // PROTOCOL LOGS
    // =====================================================================

    private val _protocolLogs = MutableLiveData<List<String>>(emptyList())
    val protocolLogs: LiveData<List<String>> = _protocolLogs

    /**
     * Añade un nuevo mensaje al historial de logs con un timestamp.
     */
    fun addProtocolLog(message: String) {
        val formatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val timestamp = formatter.format(Date())
        val logEntry = "[$timestamp] $message"

        val currentLogs = _protocolLogs.value.orEmpty().toMutableList()
        currentLogs.add(logEntry)

        // Limitar el tamaño del log a 100 entradas para evitar sobrecargar la UI
        if (currentLogs.size > 100) {
            currentLogs.removeAt(0)
        }

        _protocolLogs.value = currentLogs
    }

    /**
     * Borra todo el historial de logs.
     */
    fun clearProtocolLogs() {
        _protocolLogs.value = emptyList()
        addProtocolLog("Historial de logs limpiado.")
    }
}
