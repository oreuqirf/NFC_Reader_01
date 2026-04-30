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

// Asegúrate de tener estos imports en la parte superior:
import kotlinx.coroutines.Dispatchers
import com.example.nfc_reader_01.collection.AppDatabase
import com.example.nfc_reader_01.collection.InstrumentRecord
import android.content.Context
import com.example.nfc_reader_01.data.ConfigurationData
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers.IO

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

    // =====================================================================
    // --- VARIABLES PARA OBSERVAR LA BASE DE DATOS DESDE LA PANTALLA ---
    // =====================================================================

    // Lista de todos los registros actuales
    private val _databaseRecords = kotlinx.coroutines.flow.MutableStateFlow<List<com.example.nfc_reader_01.collection.InstrumentRecord>>(emptyList())
    val databaseRecords: kotlinx.coroutines.flow.StateFlow<List<com.example.nfc_reader_01.collection.InstrumentRecord>> = _databaseRecords

    // Cantidad de registros en el lote
    private val _batchCount = kotlinx.coroutines.flow.MutableStateFlow(0)
    val batchCount: kotlinx.coroutines.flow.StateFlow<Int> = _batchCount




    // =====================================================================
    // --- FUNCIONES DE ADMINISTRACIÓN DE BASE DE DATOS ---
    // =====================================================================

    // 1. Cargar todo (Llama a esta función cuando abras la pantalla del Gestor)
    fun loadAllRecords(context: android.content.Context) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val db = com.example.nfc_reader_01.collection.AppDatabase.getDatabase(context)
            val records = db.recordDao().getAllRecords()
            val count = db.recordDao().getRecordCount()

            _databaseRecords.value = records
            _batchCount.value = count
        }
    }

    // 2. Borrar toda la tabla (Vaciado rápido)
    fun clearDatabase(context: android.content.Context) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val db = com.example.nfc_reader_01.collection.AppDatabase.getDatabase(context)
                db.recordDao().deleteAllRecords()

                // Actualizamos la UI
                _databaseRecords.value = emptyList()
                _batchCount.value = 0

                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    _uiMessage.value = "Base de datos vaciada correctamente"
                }
            } catch (e: Exception) {
                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    _uiMessage.value = "Error al borrar: ${e.message}"
                }
            }
        }
    }

    // 3. Borrar un solo elemento
    fun deleteSingleRecord(context: android.content.Context, record: com.example.nfc_reader_01.collection.InstrumentRecord) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val db = com.example.nfc_reader_01.collection.AppDatabase.getDatabase(context)
                db.recordDao().deleteRecord(record)

                // Recargamos la lista actualizada
                loadAllRecords(context)

            } catch (e: Exception) {
                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    _uiMessage.value = "Error al borrar registro: ${e.message}"
                }
            }
        }
    }

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


    fun saveInstrumentDataToDatabase(
        context: android.content.Context,
        serial: String,
        firmware: String,
        config: ConfigurationData
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val db = com.example.nfc_reader_01.collection.AppDatabase.getDatabase(context)

                val record = com.example.nfc_reader_01.collection.InstrumentRecord(
                    serialNumber = serial,
                    firmwareVersion = firmware,
                    kMeter = config.kMeter,
                    low_stability = config.low_stability,
                    high_stability = config.high_stability,
                    tempRawLow = config.lowTempUnscaled,
                    tempRawHigh = config.highTempUnscaled,
                    tempCalLow = config.lowTempCorrected,
                    tempCalHigh = config.highTempCorrected,
                    fcQ1Error = config.fcQ1_error,
                    fcQ2Error = config.fcQ2_error,
                    fcQ035Error = config.fcQ0_35_error,
                    fcQ100Error = config.fcQ1_00_error,
                    fcQ10LmError = config.fcQ10_00_error,
                    fcQ3Error = config.fcQ3_error,
                    deviceLastConfigDate = config.lastConfigurationDate.toLong() * 1000
                )

                db.recordDao().insertRecord(record)

                withContext(Dispatchers.Main) {
                    _uiMessage.value = "¡Lectura guardada en lote!"
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _uiMessage.value = "Error al guardar: ${e.message}"
                }
            }
        }
    }
}
