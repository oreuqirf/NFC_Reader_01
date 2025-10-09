package com.example.nfc_reader_01.data

import android.nfc.NdefMessage
import android.nfc.NdefRecord
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.Charset
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.experimental.and


// =================================================================
// DATA STRUCTURES (DATA CLASSES)
// =================================================================

/**
 * Block 0x81: Identity Data (12 bytes: Int + Int + Int).
 *
 * @property deviceId The unique identifier of the device.
 * @property firmwareVersion The firmware version of the device.
 * @property lastConfigurationDate The date of the last configuration.
 */
data class IdentityData(
    val deviceId: Long,
    val firmwareVersion: String,
    val lastConfigurationDate: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as IdentityData

        if (deviceId != other.deviceId) return false
        if (firmwareVersion != other.firmwareVersion) return false

        return true
    }

    override fun hashCode(): Int {
        var result = deviceId.hashCode()
        result = 31 * result + firmwareVersion.hashCode()
        return result
    }
}


/**
 * Block 0x82: Process Data (36 bytes = 9 integers of 4 bytes).
 *
 * @property volume The volume.
 * @property flowRate The flow rate.
 * @property temperature The temperature.
 * @property battery The battery level.
 * @property statusFlags The status flags.
 * @property directFlowPeriod The direct flow period.
 * @property reverseFlowPeriod The reverse flow period.
 * @property noFlowPeriod The no-flow period.
 * @property leakageFlowPeriod The leakage flow period.
 */
data class ProcessData(
    val volume: Int,
    val flowRate: Int,
    val temperature: Int,
    val battery: Int,
    val statusFlags: Int,
    val directFlowPeriod: Int,
    val reverseFlowPeriod: Int,
    val noFlowPeriod: Int,
    val leakageFlowPeriod: Int,
)


/**
 * Block 0x83: Configuration Data (23 floats + 1 Int = 96 bytes total).
 * The WRITE command (0x04) sends the 23 floats + the Int of the timestamp (96 bytes of data).
 */
data class ConfigurationData(
    val kMeter: Float,
    val lowTempUnscaled: Float,
    val highTempUnscaled: Float,
    val lowTempCorrected: Float,
    val highTempCorrected: Float,
    val fcQ1_flow: Float,
    val fcQ1_error: Float,
    val fcQ1_temperature: Float,
    val fcQ2_flow: Float,
    val fcQ2_error: Float,
    val fcQ2_temperature: Float,
    val fcQ0_35_flow: Float,
    val fcQ0_35_error: Float,
    val fcQ0_35_temperature: Float,
    val fcQ1_00_flow: Float,
    val fcQ1_00_error: Float,
    val fcQ1_00_temperature: Float,
    val fcQ10_00_flow: Float,
    val fcQ10_00_error: Float,
    val fcQ10_00_temperature: Float,
    val fcQ3_flow: Float,
    val fcQ3_error: Float,
    val fcQ3_temperature: Float,
    val lastConfigurationDate: Int, // Read/write field. Unix timestamp (seconds).
)


/**
 * Engineering Data.
 *
 * @property volumeLiters The volume in liters.
 * @property volumeLitersUncal The uncalibrated volume in liters.
 * @property temperatureUncal The uncalibrated temperature.
 * @property flowUncal The uncalibrated flow.
 * @property ttof Time To Flight.
 * @property dtof Delta Time of Flight.
 * @property stdDev The standard deviation.
 * @property time The time.
 * @property chipTemperature The internal temperature.
 * @property lux The lux value.
 * @property rakFrameCounter The RAK frame counter.
 */
data class EngineeringData(
    val volumeLiters: Int,
    val volumeLitersUncal: Int,
    val temperatureUncal: Int,
    val flowUncal: Int,
    val ttof: Int,
    val dtof: Int,
    val stdDev: Int,
    val time: Int,
    val chipTemperature: Int,
    val lux: Int,
    val rakFrameCounter: Int,
)


/**
 * Container class for the ViewModel's LiveData.
 * Defines the structure observed in NotificationsFragment.kt and updated in MainActivity.kt.
 * 'rawNdefRecords' is included for compatibility with old code, although it is not used.
 *
 * @property identityData The identity data.
 * @property processData The process data.
 * @property configData The configuration data.
 * @property engineeringData The engineering data.
 * @property rawNdefRecords The raw NDEF records.
 */
data class NdefRecordsData(
    val identityData: IdentityData? = null,
    val processData: ProcessData? = null,
    val configData: ConfigurationData? = null,
    val engineeringData: EngineeringData? = null,
    val rawNdefRecords: List<NdefRecord>? = null
)


// =================================================================
// BINARY PARSER AND NDEF COMMAND CREATOR
// =================================================================

/**
 * Object to parse NFC data and create NDEF command messages.
 */
object NfcDataParser {

    // --- Command Definitions (App -> TAG) and MIME Types ---
    private val COMMAND_ID_DATA: Byte = 0x01.toByte()
    private val COMMAND_PROCESS_DATA: Byte = 0x02.toByte()
    private val COMMAND_CONFIG_DATA: Byte = 0x03.toByte()
    private val COMMAND_WRITE_CONFIG: Byte = 0x04.toByte()
    private val COMMAND_ENGINEERING_DATA: Byte = 0x05.toByte()
    const val COMMAND_ID_SAVE_CONFIG: Byte = 0x04 // Command to save (write) the configuration

    private val MIME_TYPE_COMMAND = "application/x-cmd"
    private val MIME_TYPE_DATA = "application/x-data"


    // -----------------------------------------------------------------------
    // --- RESPONSE PARSING (0x81, 0x82, 0x83) ---
    // -----------------------------------------------------------------------

    /**
     * Converts the 36-byte ByteArray (response 0x82) into a structured ProcessData object.
     * @param processBytes The 36-byte array to parse.
     * @return A [ProcessData] object.
     * @throws IllegalArgumentException if the byte array is not 36 bytes long.
     */
    fun parseProcessData(processBytes: ByteArray): ProcessData {
        // The expected size is 36 bytes (9 Ints).
        if (processBytes.size != 36) {
            throw IllegalArgumentException("The process data size must be 36 bytes (9 Ints). Received: ${processBytes.size}")
        }

        val buffer = ByteBuffer.wrap(processBytes).order(ByteOrder.LITTLE_ENDIAN)

        return ProcessData(
            volume = buffer.getInt(),
            flowRate = buffer.getInt(),
            temperature = buffer.getInt(),
            battery = buffer.getInt(),
            statusFlags = buffer.getInt(),
            directFlowPeriod = buffer.getInt(),
            reverseFlowPeriod = buffer.getInt(),
            noFlowPeriod = buffer.getInt(),
            leakageFlowPeriod = buffer.getInt()
        )
    }

    /**
     * Converts the 96-byte ByteArray (response 0x83) into a structured ConfigurationData object.
     * The TAG's response (0x83) must include the timestamp (96 bytes total).
     * @param configBytes The 96-byte array to parse.
     * @return A [ConfigurationData] object.
     * @throws IllegalArgumentException if the byte array is not 96 bytes long.
     */
    fun parseConfigData(configBytes: ByteArray): ConfigurationData {
        // The expected size is 96 bytes (23 Floats + 1 Int).
        if (configBytes.size != 96) {
            throw IllegalArgumentException("The configuration data size must be 96 bytes (23F + 1I). Received: ${configBytes.size}")
        }

        // Use ByteBuffer with LITTLE_ENDIAN
        val buffer = ByteBuffer.wrap(configBytes).order(ByteOrder.LITTLE_ENDIAN)

        return ConfigurationData(
            kMeter = buffer.getFloat(),
            lowTempUnscaled = buffer.getFloat(),
            highTempUnscaled = buffer.getFloat(),
            lowTempCorrected = buffer.getFloat(),
            highTempCorrected = buffer.getFloat(),
            fcQ1_flow = buffer.getFloat(),
            fcQ1_error = buffer.getFloat(),
            fcQ1_temperature = buffer.getFloat(),
            fcQ2_flow = buffer.getFloat(),
            fcQ2_error = buffer.getFloat(),
            fcQ2_temperature = buffer.getFloat(),
            fcQ0_35_flow = buffer.getFloat(),
            fcQ0_35_error = buffer.getFloat(),
            fcQ0_35_temperature = buffer.getFloat(),
            fcQ1_00_flow = buffer.getFloat(),
            fcQ1_00_error = buffer.getFloat(),
            fcQ1_00_temperature = buffer.getFloat(),
            fcQ10_00_flow = buffer.getFloat(),
            fcQ10_00_error = buffer.getFloat(),
            fcQ10_00_temperature = buffer.getFloat(),
            fcQ3_flow = buffer.getFloat(),
            fcQ3_error = buffer.getFloat(),
            fcQ3_temperature = buffer.getFloat(),
            lastConfigurationDate = buffer.getInt(), // Last Int (timestamp)
        )
    }

    /**
     * Converts the 12-byte ByteArray (response 0x81) into structured IdentityData.
     *
     * 12-BYTE STRUCTURE (3 x Int):
     * 1. Device ID (Int) - 4 bytes
     * 2. Firmware Version (Int) - 4 bytes
     * 3. Configuration Timestamp (Int) - 4 bytes
     *
     * @param identityBytes The 12-byte array to parse.
     * @return An [IdentityData] object.
     * @throws IllegalArgumentException if the byte array is not 12 bytes long.
     */
    fun parseIdentityData(identityBytes: ByteArray): IdentityData {
        // The expected size must be 12 bytes.
        val expectedSize = 12
        if (identityBytes.size != expectedSize) {
            throw IllegalArgumentException("The identity data size (0x81) must be $expectedSize bytes (3 x Int). Received: ${identityBytes.size}")
        }

        val buffer = ByteBuffer.wrap(identityBytes).order(ByteOrder.LITTLE_ENDIAN)

        // 1. Device ID (4 bytes) -> Read as Int and converted to an unsigned Long.
        val signedDeviceIdRaw = buffer.getInt()
        // We use 'and 0xFFFFFFFFL' to correctly handle the 32-bit value as an unsigned Long.
        val deviceIdRaw = signedDeviceIdRaw.toLong() and 0xFFFFFFFFL

        // 2. Firmware Version (4 bytes)
        val firmwareVersionRaw    = buffer.getInt()
        val firmwareVersionLast   = (firmwareVersionRaw and 0xFF).toByte()
        val firmwareVersionMinor  = (firmwareVersionRaw shr 8 and 0xFF).toByte()
        val firmwareVersionMain   = (firmwareVersionRaw shr 16 and 0xFF).toByte()
        val firmwareVersionString = String.format(Locale.US, "%d.%d.%d", firmwareVersionMain, firmwareVersionMinor, firmwareVersionLast)

        // 3. Configuration Timestamp (4 bytes) -> Read as Int
        val configTimestamp = buffer.getInt()

        // Assume the Unix timestamp is in seconds and convert it to milliseconds for Date
        val dateMillis = configTimestamp.toLong() * 1000
        val date = Date(dateMillis)
        val formatter = SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault())
        val formattedDate = formatter.format(date)

        return IdentityData(
            deviceId = deviceIdRaw,
            firmwareVersion = firmwareVersionString,
            lastConfigurationDate = formattedDate,
        )
    }

    /**
     * Converts the 44-byte ByteArray (response 0x85) into a structured EngineeringData object.
     * @param engineeringBytes The 44-byte array to parse.
     * @return An [EngineeringData] object.
     * @throws IllegalArgumentException if the byte array is not 44 bytes long.
     */
    fun parseEngineeringData(engineeringBytes: ByteArray): EngineeringData {
        // The expected size is 44 bytes (11 Ints).
        if (engineeringBytes.size != 44) {
            throw IllegalArgumentException("The engineering data size must be 44 bytes. Received: ${engineeringBytes.size}")
        }

        val buffer = ByteBuffer.wrap(engineeringBytes).order(ByteOrder.LITTLE_ENDIAN)

        return EngineeringData(
            volumeLiters = buffer.getInt(),
            volumeLitersUncal = buffer.getInt(),
            temperatureUncal = buffer.getInt(),
            flowUncal = buffer.getInt(),
            ttof = buffer.getInt(),
            dtof = buffer.getInt(),
            stdDev = buffer.getInt(),
            time = buffer.getInt(),
            chipTemperature = buffer.getInt(),
            lux = buffer.getInt(),
            rakFrameCounter = buffer.getInt()
        )
    }



    // -----------------------------------------------------------------------
    // --- COMMAND CREATION (0x01, 0x02, 0x03, 0x04) ---
    // -----------------------------------------------------------------------

    /**
     * Serializes the 23 floats and the Int of the timestamp (96 bytes) into a ByteArray.
     * This function is the basis for the 0x04 WRITE command.
     * @param config The [ConfigurationData] to serialize.
     * @return A 96-byte array.
     */
    fun serializeConfigData(config: ConfigurationData): ByteArray {

        // CRITICAL STEP: Update the configuration date
        // We get the current time in milliseconds and convert it to seconds (4-byte Int).
        val currentEpochSeconds = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis()).toInt()

        // CRITICAL ASSIGNMENT: 96 bytes (23 floats + 1 int = 24 elements * 4 bytes)
        val buffer = ByteBuffer.allocate(96).order(ByteOrder.LITTLE_ENDIAN)

        buffer.putFloat(config.kMeter)
        buffer.putFloat(config.lowTempUnscaled)
        buffer.putFloat(config.highTempUnscaled)
        buffer.putFloat(config.lowTempCorrected)
        buffer.putFloat(config.highTempCorrected)
        buffer.putFloat(config.fcQ1_flow)
        buffer.putFloat(config.fcQ1_error)
        buffer.putFloat(config.fcQ1_temperature)
        buffer.putFloat(config.fcQ2_flow)
        buffer.putFloat(config.fcQ2_error)
        buffer.putFloat(config.fcQ2_temperature)
        buffer.putFloat(config.fcQ0_35_flow)
        buffer.putFloat(config.fcQ0_35_error)
        buffer.putFloat(config.fcQ0_35_temperature)
        buffer.putFloat(config.fcQ1_00_flow)
        buffer.putFloat(config.fcQ1_00_error)
        buffer.putFloat(config.fcQ1_00_temperature)
        buffer.putFloat(config.fcQ10_00_flow)
        buffer.putFloat(config.fcQ10_00_error)
        buffer.putFloat(config.fcQ10_00_temperature)
        buffer.putFloat(config.fcQ3_flow)
        buffer.putFloat(config.fcQ3_error)
        buffer.putFloat(config.fcQ3_temperature)
        buffer.putInt(currentEpochSeconds) // Write the new timestamp
        return buffer.array() // Returns exactly 96 bytes.
    }

    /**
     * Creates an NdefMessage with a SINGLE NdefRecord of type MIME_TYPE_COMMAND.
     * CRITICAL REDUCTION: The payload is now 1 byte to minimize the NDEF message size
     * and avoid truncation issues during writing (0x01, 0x02, 0x03).
     *
     * @param commandId The ID of the command (Byte) to send (e.g., 0x01).
     * @return An NdefMessage ready to be written to the Tag.
     */
    fun createReadCommandMessage(commandId: Byte): NdefMessage {
        // Minimalist payload: only the command ID (1 byte).
        val payload = byteArrayOf(commandId)

        val commandRecord = NdefRecord.createMime(MIME_TYPE_COMMAND, payload)
        // The NdefMessage contains a SINGLE record
        return NdefMessage(commandRecord)
    }

    /**
     * CRITICAL for Command 0x04 (Write). A SINGLE RECORD!
     * Combines a 1-byte command prefix (0x04) with the serialized configuration data (96 bytes).
     * Total Payload: 97 bytes.
     *
     * @param writeData The serialized configuration data (96 bytes).
     * @return The NdefMessage with a total payload of 97 bytes ready to be written.
     * @throws IllegalArgumentException if the write data is not 96 bytes long.
     * @throws IllegalStateException if the final payload is not 97 bytes long.
     */
    fun createWriteConfigMessage(writeData: ByteArray): NdefMessage {
        if (writeData.size != 96) {
            throw IllegalArgumentException("The configuration data payload must be 96 bytes. Received: ${writeData.size}")
        }

        // The total payload must have the size of the command (1 byte) + the data (96 bytes)
        val fullPayload = ByteArray(1 + writeData.size) // 97 bytes

        // First byte: the 0x04 command
        fullPayload[0] = COMMAND_WRITE_CONFIG

        // Copy the 96 bytes of data after the command
        System.arraycopy(writeData, 0, fullPayload, 1, writeData.size)

        if (fullPayload.size != 97) {
            // This error should never occur if the 96-byte check is correct
            throw IllegalStateException("Protocol error: The total payload for Command 0x04 must be 97 bytes, but was ${fullPayload.size}")
        }

        // We create a SINGLE NDEF record, as required by the TAG's hardware.
        val record = NdefRecord.createMime(MIME_TYPE_COMMAND, fullPayload)
        return NdefMessage(arrayOf(record))
    }


    /**
     * Tries to decode the payload of the NdefRecord.
     * This assumes the payload is a simple text string encoded in UTF-8.
     *
     * @param record The NDEF record to parse.
     * @return The decoded string, or null if parsing fails.
     */
    fun parseDataPayload(record: NdefRecord): String? {
        if (record.tnf == NdefRecord.TNF_MIME_MEDIA && String(record.type) == MIME_TYPE_DATA) {
            return try {
                // Assume UTF-8 encoding
                String(record.payload, Charset.forName("UTF-8"))
            } catch (e: Exception) {
                // Error decoding
                null
            }
        }
        return null
    }

}
