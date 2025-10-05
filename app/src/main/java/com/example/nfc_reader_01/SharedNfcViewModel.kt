package com.example.nfc_reader_01

import android.nfc.NdefMessage
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.nfc_reader_01.data.NfcTagInfo // Importamos la clase de datos

class SharedNfcViewModel : ViewModel() {

    // ----------------------------------------------------------------
    // 1. TAG Info (NfcTagInfo) - Único objeto para todos los datos del TAG
    //    Este LiveData contendrá el ID del TAG, la tecnología y el mensaje NDEF.
    // ----------------------------------------------------------------
    private val _nfcTagInfo = MutableLiveData<NfcTagInfo?>()
    val nfcTagInfo: LiveData<NfcTagInfo?> = _nfcTagInfo

    /**
     * Establece toda la información del TAG después de la detección inicial (ID y Tech Type).
     * Si se pasa 'null', indica que el TAG ha sido desconectado o el estado debe borrarse.
     */
    fun setNfcTagInfo(tagInfo: NfcTagInfo?) {
        _nfcTagInfo.value = tagInfo
    }

    /**
     * Actualiza el objeto NfcTagInfo existente con el mensaje NDEF leído.
     * Esto permite actualizar solo el resultado de la lectura sin perder el ID y la tecnología.
     * Si no hay un TAG activo (el valor es null), esta función no hace nada.
     */
    fun updateNdefMessage(ndefMessage: NdefMessage?) {
        val currentInfo = _nfcTagInfo.value ?: return
        // Creamos una copia del objeto actualizando solo el campo ndefMessage
        setNfcTagInfo(currentInfo.copy(ndefMessage = ndefMessage))
    }

    // NOTA: Los LiveData y setters individuales para nfcTagId, nfcTechType y ndefMessage
    // han sido eliminados/reemplazados por _nfcTagInfo y sus funciones asociadas.
    // ----------------------------------------------------------------

    // ----------------------------------------------------------------
    // 2. Protocol Log (Manteniendo el mismo comportamiento)
    // ----------------------------------------------------------------
    private val _protocolLog = MutableLiveData<String>("")
    val protocolLog: LiveData<String> = _protocolLog

    /**
     * Agrega un nuevo mensaje al log de actividad del protocolo.
     */
    fun addProtocolLog(message: String) {
        val currentLog = _protocolLog.value ?: ""
        // Formato para el log (Hora + Mensaje)
        val newLogEntry = "${java.text.SimpleDateFormat("HH:mm:ss").format(java.util.Date())}: $message\n"
        // Pre-pende la nueva entrada y limita las líneas
        val newLog = (newLogEntry + currentLog).split('\n').take(50).joinToString("\n")
        _protocolLog.value = newLog
    }

    // ----------------------------------------------------------------
    // 3. Command/Write State (Lógica avanzada futura)
    // ----------------------------------------------------------------
    private val _commandState = MutableLiveData<Byte?>(null)
    val commandState: LiveData<Byte?> = _commandState

    fun sendCommand(commandId: Byte) {
        _commandState.value = commandId
    }
}
