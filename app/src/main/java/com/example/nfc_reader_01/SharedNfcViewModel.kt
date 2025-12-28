package com.example.nfc_reader_01

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.nfc_reader_01.NfcState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.example.nfc_reader_01.utils.LogManager

data class LogEntry(
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
)

@OptIn(ExperimentalStdlibApi::class)
class SharedNfcViewModel(application: Application) : AndroidViewModel(application) {

    private fun getString(resId: Int): String = getApplication<Application>().getString(resId)
    private fun getString(resId: Int, vararg args: Any): String = getApplication<Application>().getString(resId, *args)

    private val _nfcTagInfo = MutableStateFlow<String?>(null)
    val nfcTagInfo: StateFlow<String?> = _nfcTagInfo.asStateFlow()

    private val _uiMessage = MutableStateFlow(getString(R.string.status_waiting_nfc))
    val uiMessage: StateFlow<String> = _uiMessage.asStateFlow()

    private val _writeStatus = MutableStateFlow<NfcState>(NfcState.Idle)
    val writeStatus: StateFlow<NfcState> = _writeStatus.asStateFlow()

    private val _protocolLog = MutableStateFlow<List<LogEntry>>(emptyList())
    val protocolLog: StateFlow<List<LogEntry>> = _protocolLog.asStateFlow()

    private val _pendingCommand = MutableStateFlow<Byte?>(null)
    val pendingCommand: StateFlow<Byte?> = _pendingCommand.asStateFlow()

    // Variable para saber qué comando estamos esperando y filtrar respuestas basura
    private var activeCommand: Byte? = null

    private val _configDataToWrite = MutableStateFlow<ByteArray?>(null)
    val configDataToWrite: StateFlow<ByteArray?> = _configDataToWrite.asStateFlow()

    private val _identityResponseData = MutableSharedFlow<ByteArray?>(replay = 0)
    val identityResponseData: SharedFlow<ByteArray?> = _identityResponseData.asSharedFlow()

    private val _processResponseData = MutableSharedFlow<ByteArray?>(replay = 0)
    val processResponseData: SharedFlow<ByteArray?> = _processResponseData.asSharedFlow()

    private val _engineeringResponseData = MutableSharedFlow<ByteArray?>(replay = 0)
    val engineeringResponseData: SharedFlow<ByteArray?> = _engineeringResponseData.asSharedFlow()

    private val _configurationResponseData = MutableStateFlow<ByteArray?>(null)
    val configurationResponseData: StateFlow<ByteArray?> = _configurationResponseData.asStateFlow()

    fun clearConfigurationResponseData() { _configurationResponseData.value = null }
    fun setNfcTagInfo(info: String?) { _nfcTagInfo.value = info }
    fun setUiMessage(message: String) { _uiMessage.value = message }

    fun setStatusIdle() { _writeStatus.value = NfcState.Idle }
    fun setStatusLoading() { _writeStatus.value = NfcState.Loading }
    fun setStatusSuccess() { _writeStatus.value = NfcState.Success }
    fun setStatusError(message: String) { _writeStatus.value = NfcState.Error(message) }

    // Compatibilidad
    fun emitWriteStatus(status: String) {
        if (status.contains("Exitosa", ignoreCase = true) || status.contains("Success", ignoreCase = true)) {
            setStatusSuccess()
        } else {
            setStatusError(status)
        }
    }

    fun onNfcTagDetected(tagId: String) {
        viewModelScope.launch {
            LogManager.log(getString(R.string.log_nfc_detected, tagId))
            processTag(tagId)
        }
    }

    private fun processTag(id: String) {
        viewModelScope.launch {
            LogManager.log(getString(R.string.log_init_read))
        }
    }

    fun sendCommand(commandCode: Byte) {
        // Reseteamos a Loading para borrar cualquier Success anterior
        setStatusLoading()
        activeCommand = commandCode
        clearConfigurationResponseData()
        _pendingCommand.value = commandCode
    }

    fun clearCommand() {
        _pendingCommand.value = null
        _configDataToWrite.value = null
    }

    fun setConfigDataToWrite(data: ByteArray?) {
        _configDataToWrite.value = data
    }

    fun distributeResponseData(responseCode: Byte, data: ByteArray) {
        viewModelScope.launch {

            // --- FILTRO DE RESPUESTAS ANTIGUAS ---
            // Si estábamos esperando LEER (0x01, 0x02, 0x05, 0x03), y recibimos
            // confirmación de escritura (0x84, 0x8A, 0x13), la ignoramos.
            if (isReadCommand(activeCommand)) {
                if (responseCode == 0x84.toByte() || responseCode == 0x8A.toByte() || responseCode == 0x13.toByte()) {
                    LogManager.log("IGNORANDO RESPUESTA ANTIGUA (0x${responseCode.toHexString()}) MIENTRAS SE ESPERABA LECTURA.")
                    return@launch
                }
            }

            when (responseCode) {
                // LECTURA
                0x81.toByte() -> {
                    _identityResponseData.emit(data)
                    setUiMessage(getString(R.string.msg_read_success_identity))
                    setStatusSuccess()
                }
                0x82.toByte() -> {
                    _processResponseData.emit(data)
                    setUiMessage(getString(R.string.msg_read_success_process))
                    setStatusSuccess()
                }
                0x85.toByte() -> {
                    _engineeringResponseData.emit(data)
                    setUiMessage(getString(R.string.msg_read_success_engineering))
                    setStatusSuccess()
                }
                0x83.toByte() -> {
                    _configurationResponseData.emit(data)
                    setUiMessage(getString(R.string.msg_read_success_config))
                    setStatusSuccess()
                }

                // ESCRITURA
                0x84.toByte() -> {
                    val msg = getString(R.string.msg_write_success)
                    LogManager.log(msg)
                    setUiMessage(msg)
                    setStatusSuccess()
                }
                0x8A.toByte() -> {
                    val msg = getString(R.string.msg_reset_success)
                    LogManager.log(msg)
                    setUiMessage(msg)
                    setStatusSuccess()
                }
                0x13.toByte() -> {
                    val msg = getString(R.string.msg_calibration_success)
                    LogManager.log(msg)
                    setStatusSuccess()
                }

                else -> {
                    val message = getString(R.string.error_protocol_unknown, responseCode.toHexString())
                    LogManager.log(message)
                    setUiMessage(message)
                    setStatusError(message)
                }
            }
        }
    }

    private fun isReadCommand(cmd: Byte?): Boolean {
        return cmd == 0x01.toByte() || cmd == 0x02.toByte() || cmd == 0x03.toByte() || cmd == 0x05.toByte()
    }
}
