package com.example.nfc_reader_01.data

import android.nfc.NdefMessage
import android.nfc.NdefRecord
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit
import kotlin.experimental.and


// =================================================================
// ESTRUCTURAS DE DATOS (DATA CLASSES)
// =================================================================

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
    val lastConfigurationDate: Int,
)

// ---------------------------------------------------------
// CAMBIO 1: Agregado campo 'temperature' al final
// ---------------------------------------------------------
data class EngineeringData(
    val volumeLiters: Int,      // Volumen en litros
    val volumeLitersUncal: Int, // Volumen en litros sin calibrar
    val temperatureUncal: Int,  // Temperatura sin calibrar
    val flowUncal: Int,         // caudal sin calibrar
    val ttof: Int,              // TToF
    val dtof: Int,              // DToF
    val stdDev: Int,            // Desviacion estandard
    val time: Int,              // Tiempo
    val chipTemperature: Int,   // Temperatura interna
    val lux: Int,               // Luxs
    val rakFrameCounter: Int,   // RAK Frame Counter
    val lastTripFlow: Int,      // Ultimo Caudal (Last Trip Flow)
    val temperature: Int        // NUEVO: Temperatura calibrada (al final)
)


data class NdefRecordsData(
    val identityData: IdentityData? = null,
    val processData: ProcessData? = null,
    val configData: ConfigurationData? = null,
    val engineeringData: EngineeringData? = null,
    val rawNdefRecords: List<NdefRecord>? = null
)


// =================================================================
// PARSER BINARIO Y CREADOR DE COMANDOS NDEF
// =================================================================

object NfcDataParser {

    val DEFAULT_CONFIG_DATA = ConfigurationData(
        kMeter = 0.0f,
        lowTempUnscaled = 0.0f,
        highTempUnscaled = 0.0f,
        lowTempCorrected = 0.0f,
        highTempCorrected = 0.0f,
        fcQ1_flow = 0.0f,
        fcQ1_error = 0.0f,
        fcQ1_temperature = 0.0f,
        fcQ2_flow = 0.0f,
        fcQ2_error = 0.0f,
        fcQ2_temperature = 0.0f,
        fcQ0_35_flow = 0.0f,
        fcQ0_35_error = 0.0f,
        fcQ0_35_temperature = 0.0f,
        fcQ1_00_flow = 0.0f,
        fcQ1_00_error = 0.0f,
        fcQ1_00_temperature = 0.0f,
        fcQ10_00_flow = 0.0f,
        fcQ10_00_error = 0.0f,
        fcQ10_00_temperature = 0.0f,
        fcQ3_flow = 0.0f,
        fcQ3_error = 0.0f,
        fcQ3_temperature = 0.0f,
        lastConfigurationDate = 0,
    )

    private val COMMAND_ID_DATA: Byte = 0x01.toByte()
    private val COMMAND_PROCESS_DATA: Byte = 0x02.toByte()
    private val COMMAND_CONFIG_DATA: Byte = 0x03.toByte()
    private val COMMAND_WRITE_CONFIG: Byte = 0x04.toByte()
    private val COMMAND_ENGINEERING_DATA: Byte = 0x05.toByte()
    const val COMMAND_ID_SAVE_CONFIG: Byte = 0x04

    private val MIME_TYPE_COMMAND = "application/x-cmd"
    private val MIME_TYPE_DATA = "application/x-data"


    // -----------------------------------------------------------------------
    // --- PARSING DE RESPUESTAS (0x81, 0x82, 0x83) ---
    // -----------------------------------------------------------------------

    fun parseProcessData(processBytes: ByteArray): ProcessData {
        if (processBytes.size != 36) {
            throw IllegalArgumentException("El tamaño de datos de proceso debe ser 36 bytes (9 Ints). Recibido: ${processBytes.size}")
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

    fun parseConfigData(configBytes: ByteArray): ConfigurationData {
        if (configBytes.size != 96) {
            throw IllegalArgumentException("El tamaño de datos de configuración debe ser 96 bytes (23F + 1I). Recibido: ${configBytes.size}")
        }

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
            lastConfigurationDate = buffer.getInt(),
        )
    }

    fun parseIdentityData(identityBytes: ByteArray): IdentityData {
        val expectedSize = 12
        if (identityBytes.size != expectedSize) {
            throw IllegalArgumentException("El tamaño de datos de identidad (0x81) debe ser $expectedSize bytes (3 x Int). Recibido: ${identityBytes.size}")
        }

        val buffer = ByteBuffer.wrap(identityBytes).order(ByteOrder.LITTLE_ENDIAN)

        val signedDeviceIdRaw = buffer.getInt()
        val deviceIdRaw = signedDeviceIdRaw.toLong() and 0xFFFFFFFFL

        val firmwareVersionRaw    = buffer.getInt()
        val firmwareVersionLast   = (firmwareVersionRaw and 0xFF).toByte()
        val firmwareVersionMinor  = (firmwareVersionRaw shr 8 and 0xFF).toByte()
        val firmwareVersionMain   = (firmwareVersionRaw shr 16 and 0xFF).toByte()
        val firmwareVersionString = String.format(Locale.US, "%d.%d.%d", firmwareVersionMain, firmwareVersionMinor, firmwareVersionLast)

        val configTimestamp = buffer.getInt()

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

    // ---------------------------------------------------------
    // CAMBIO 2: Actualizado tamaño esperado a 52 bytes y lectura
    // ---------------------------------------------------------
    fun parseEngineeringData(engineeringBytes: ByteArray): EngineeringData {
        // Ahora esperamos 52 bytes (13 enteros de 4 bytes)
        val expectedSize = 52
        if (engineeringBytes.size != expectedSize) {
            throw IllegalArgumentException("El tamaño de datos de Ingenieria debe ser $expectedSize bytes. Recibido: ${engineeringBytes.size}")
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
            rakFrameCounter = buffer.getInt(),
            lastTripFlow = buffer.getInt(),
            temperature = buffer.getInt() // NUEVO: Lectura de temperatura al final
        )
    }

    // -----------------------------------------------------------------------
    // --- CREACIÓN DE COMANDOS (0x01, 0x02, 0x03, 0x04) ---
    // -----------------------------------------------------------------------

    fun serializeConfigData(config: ConfigurationData): ByteArray {

        val currentEpochSeconds = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis()).toInt()

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
        buffer.putInt(currentEpochSeconds)
        return buffer.array()
    }

    fun createReadCommandMessage(commandId: Byte): NdefMessage {
        val payload = byteArrayOf(commandId)
        val commandRecord = NdefRecord.createMime(MIME_TYPE_COMMAND, payload)
        return NdefMessage(commandRecord)
    }

    fun createWriteConfigMessage(writeData: ByteArray): NdefMessage {
        if (writeData.size != 96) {
            throw IllegalArgumentException("El payload de datos de configuración debe ser de 96 bytes. Recibido: ${writeData.size}")
        }

        val fullPayload = ByteArray(1 + writeData.size)
        fullPayload[0] = COMMAND_WRITE_CONFIG
        System.arraycopy(writeData, 0, fullPayload, 1, writeData.size)

        if (fullPayload.size != 97) {
            throw IllegalStateException("Error de protocolo: El payload total para el Comando 0x04 debe ser de 97 bytes, pero fue ${fullPayload.size}")
        }

        val record = NdefRecord.createMime(MIME_TYPE_COMMAND, fullPayload)
        return NdefMessage(arrayOf(record))
    }


    fun parseDataPayload(record: NdefRecord): String? {
        if (record.tnf == NdefRecord.TNF_MIME_MEDIA && String(record.type) == MIME_TYPE_DATA) {
            return try {
                String(record.payload, Charset.forName("UTF-8"))
            } catch (e: Exception) {
                null
            }
        }
        return null
    }

}
