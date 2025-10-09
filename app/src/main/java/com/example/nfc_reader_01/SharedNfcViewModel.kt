package com.example.nfc_reader_01

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nfc_reader_01.utils.LogManager
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Data class for a protocol log entry.
 *
 * @property message The log message.
 * @property timestamp The time the log entry was created.
 */
data class LogEntry(
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Shared ViewModel to handle NFC state, commands, and data responses
 * between the Activity (NFC handling) and Fragments (UI).
 */
class SharedNfcViewModel : ViewModel() {

    // --- General State Variables (StateFlow - Emit the last known value) ---

    /**
     * Stores information about the currently connected NFC tag.
     */
    private val _nfcTagInfo = MutableStateFlow<String?>(null)
    val nfcTagInfo: StateFlow<String?> = _nfcTagInfo.asStateFlow()

    private val _uiMessage = MutableStateFlow("Waiting for NFC connection...")
    val uiMessage: StateFlow<String> = _uiMessage.asStateFlow()

    // --- SharedFlow for one-time events (Toast/SnackBar) ---
    private val _writeStatus = MutableSharedFlow<String>(replay = 0)
    /**
     * SharedFlow for one-time notifications (usually Toast or SnackBar)
     * about the result of ANY operation (READ, WRITE, or RESET).
     */
    val writeStatus: SharedFlow<String> = _writeStatus.asSharedFlow()

    // --- PROTOCOL LOGGING ---
    private val _protocolLog = MutableStateFlow<List<LogEntry>>(emptyList())
    val protocolLog: StateFlow<List<LogEntry>> = _protocolLog.asStateFlow()

    // --- Commands and Data Responses (Flows) ---

    // The pending command. The value should be null after execution.
    private val _pendingCommand = MutableStateFlow<Byte?>(null)
    val pendingCommand: StateFlow<Byte?> = _pendingCommand.asStateFlow()

    // Configuration data to write (for CMD_WRITE_CONFIG).
    private val _configDataToWrite = MutableStateFlow<ByteArray?>(null)
    val configDataToWrite: StateFlow<ByteArray?> = _configDataToWrite.asStateFlow()

    // Flows for data responses from commands (SharedFlow - one-time events)
    private val _identityResponseData = MutableSharedFlow<ByteArray?>(replay = 0)
    val identityResponseData: SharedFlow<ByteArray?> = _identityResponseData.asSharedFlow()

    private val _processResponseData = MutableSharedFlow<ByteArray?>(replay = 0)
    val processResponseData: SharedFlow<ByteArray?> = _processResponseData.asSharedFlow()

    private val _engineeringResponseData = MutableSharedFlow<ByteArray?>(replay = 0)
    val engineeringResponseData: SharedFlow<ByteArray?> = _engineeringResponseData.asSharedFlow()


    // Configuration Response (StateFlow because it's persistent data)
    private val _configurationResponseData = MutableStateFlow<ByteArray?>(null)
    val configurationResponseData: StateFlow<ByteArray?> = _configurationResponseData.asStateFlow()

    /**
     * Resets the Configuration response StateFlow to null to avoid
     * re-processing of data.
     */
    fun clearConfigurationResponseData() {
        _configurationResponseData.value = null
    }


    /**
     * Sets the NFC tag information (only the ID).
     * @param info The tag information.
     */
    fun setNfcTagInfo(info: String?) {
        _nfcTagInfo.value = info
    }

    /**
     * Sets the feedback message for the UI.
     * @param message The message to display.
     */
    fun setUiMessage(message: String) {
        _uiMessage.value = message
    }

    /**
     * Emits the write status through the SharedFlow.
     * Now also used for successful read notifications.
     * @param status The status message.
     */
    fun emitWriteStatus(status: String) {
        // SharedFlow requires a coroutine context to emit
        viewModelScope.launch {
            _writeStatus.emit(status)
        }
    }

    /**
     * Simulates reading an NFC tag.
     * @param tagId The ID of the detected tag.
     */
    fun onNfcTagDetected(tagId: String) {
        // We use viewModelScope to launch the coroutine and call the suspend function of the LogManager.
        viewModelScope.launch {
            LogManager.log("NFC tag detected with ID: $tagId")

            // Processing logic...
            processTag(tagId)
        }
    }

    private fun processTag(id: String) {
        viewModelScope.launch {
            if (id.isEmpty()) {
                LogManager.log("ERROR: The detected tag did not contain valid data.")
            } else {
                LogManager.log("INFO: Starting data reading protocol.")
                // ... more NFC logic ...
            }
        }
    }

    /**
     * Helper function to get the readable name of the command.
     * @param code The command code.
     * @return The command name.
     */
    private fun getCommandName(code: Byte): String {
        // Command codes (not response codes)
        return when (code) {
            0x01.toByte() -> "Read Identity"
            0x02.toByte() -> "Read Process"
            0x03.toByte() -> "Read Configuration"
            0x04.toByte() -> "Write Configuration"
            0x05.toByte() -> "Read Engineering"
            0x0A.toByte() -> "Factory Reset"
            else -> "Unknown Command"
        }
    }

    /**
     * Emits a command for the Activity to pick up and send via NFC.
     *
     * KEY MODIFICATION: Emits an immediate status message for the UI (Toast)
     * to notify the user that the command has been initiated.
     * @param commandCode The command code to send.
     */
    fun sendCommand(commandCode: Byte) {
        val commandName = getCommandName(commandCode)

        // 1. Immediate notification of the command being sent
        // emitWriteStatus("COMMAND INITIATED: $commandName. Bring the TAG closer...")

        // 2. Set the pending command
        _pendingCommand.value = commandCode

        viewModelScope.launch {
            // We use the globally available toHexString function (defined in MainActivity.kt)
            LogManager.log("Command 0x${commandCode.toHexString()} ($commandName) sent to the buffer.")
        }
    }

    /**
     * Clears the pending command and configuration data after its execution.
     */
    fun clearCommand() {
        _pendingCommand.value = null
        _configDataToWrite.value = null
    }

    /**
     * Sets the binary configuration data to be written.
     * @param data The configuration data.
     */
    fun setConfigDataToWrite(data: ByteArray?) {
        _configDataToWrite.value = data
    }


    /**
     * Receives and distributes the binary response data based on the response code.
     * The first byte of the NDEF response payload (payload[0]) is the response code (0x8X).
     * @param responseCode The response code.
     * @param data The response data.
     */
    fun distributeResponseData(responseCode: Byte, data: ByteArray) {
        viewModelScope.launch {
            when (responseCode) {

                // --- HANDLING READ RESPONSES (Activates Toast) ---

                // Command 0x01 (Identity) -> Response 0x81 (READ)
                0x81.toByte() -> {
                    _identityResponseData.emit(data)
                    emitWriteStatus("Read Identity OK..")
                }
                // Command 0x02 (Process) -> Response 0x82 (READ)
                0x82.toByte() -> {
                    _processResponseData.emit(data)
                    emitWriteStatus("Read Process OK.")
                }
                // Command 0x05 (Engineering) -> Response 0x85 (READ)
                0x85.toByte() -> {
                    _engineeringResponseData.emit(data)
                    emitWriteStatus("Read Engineering OK")
                }
                // Command 0x03 (Read Config) -> Response 0x83 (READ)
                0x83.toByte() -> {
                    _configurationResponseData.emit(data)
                    emitWriteStatus("Read Configuration OK.")
                }

                // --- HANDLING WRITE/RESET RESPONSES (Activates Toast) ---

                // Command 0x04 (Write Config) -> Response 0x84 (WRITE SUCCESS)
                0x84.toByte() -> {
                    emitWriteStatus("WRITE SUCCESSFUL (0x84).")
                    LogManager.log("WRITE OK (0x84) on TAG.")
                }
                // Command 0x0A (Factory Reset) -> Response 0x8A (RESET SUCCESS)
                0x8A.toByte() -> {
                    emitWriteStatus("RESET SUCCESSFUL (0x8A).")
                    LogManager.log("FACTORY RESET OK (0x8A) on TAG.")
                }

                // --- END OF WRITE HANDLING ---

                else -> {
                    // For any other unexpected response code (protocol error)
                    // We use the globally available toHexString function (defined in MainActivity.kt)
                    val message = "PROTOCOL ERROR: Unknown response code: 0x${responseCode.toHexString()}"
                    LogManager.log(message)
                    setUiMessage(message)
                    // A protocol error Toast is emitted for the user to see.
                    emitWriteStatus(message)
                }
            }
        }
    }
}
private fun Byte.toHexString(): String {
    return String.format("%02X", this)
}
