package com.example.nfc_reader_01

import android.nfc.NdefMessage
import android.nfc.NdefRecord
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit


// =================================================================
// ESTRUCTURAS DE DATOS (DATA CLASSES)
// =================================================================

/**
 * Bloque 0x81: Datos de Identidad (12 bytes).
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
 * Bloque 0x82: Datos de Proceso (36 bytes = 9 enteros de 4 bytes).
 */
data class ProcessData(
    val volume: Int,            // Volumen
    val flowRate: Int,          // Tasa de flujo
    val temperature: Int,       // Temperatura
    val battery: Int,           // Nivel de batería
    val statusFlags: Int,       // Flags de estado
    val directFlowPeriod: Int,  // Periodo de flujo directo
    val reverseFlowPeriod: Int, // Periodo de flujo inverso
    val noFlowPeriod: Int,      // Periodo sin flujo
    val leakageFlowPeriod: Int, // Periodo de fuga (leakage)
)


/**
 * Bloque 0x83: Datos de Configuración (23 floats + 1 Int = 96 bytes total).
 * El comando de ESCRITURA (0x04) envía los 23 floats + el Int del timestamp (96 bytes de datos).
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
    val lastConfigurationDate: Int, // Campo de escritura/lectura. Timestamp Unix (segundos).
)


data class EngineeringData(
    val volumeLiters: Int,      // Volumen en litros
    val volumeLitersUncal: Int, // Volumen en litros sin calibrar
    val temperatureUncal: Int, // Temperatura sin calibrar
    val flowUncal: Int,        // caudal sin calibrar
    val ttof: Int,             // TToF
    val dtof: Int,             // DToF
    val stdDev: Int,           // Desviacion estandard
    val time: Int,             // Tiempo
    val chipTemperature: Int,  // Temperatura interna
    val lux: Int,              // Luxs
)


/**
 * Clase Contenedora para el LiveData del ViewModel.
 * Define la estructura que se observa en NotificationsFragment.kt y se actualiza en MainActivity.kt.
 * Se incluye 'rawNdefRecords' para compatibilidad con código antiguo, aunque no se use.
 */
data class NdefRecordsData(
    val identityData: IdentityData? = null,
    val processData: ProcessData? = null,
    val configData: ConfigurationData? = null,
    val engineeringData: EngineeringData? = null,
    // Propiedad que el código antiguo esperaba, aunque no se esté llenando
    val rawNdefRecords: List<NdefRecord>? = null
)


// =================================================================
// PARSER BINARIO Y CREADOR DE COMANDOS NDEF
// =================================================================

object NfcDataParser {

    // --- Definición de Comandos (App -> TAG) y Tipos MIME ---
    private val COMMAND_ID_DATA: Byte = 0x01.toByte()
    private val COMMAND_PROCESS_DATA: Byte = 0x02.toByte()
    private val COMMAND_CONFIG_DATA: Byte = 0x03.toByte()
    private val COMMAND_WRITE_CONFIG: Byte = 0x04.toByte()
    private val COMMAND_ENGINEERING_DATA: Byte = 0x05.toByte()
    const val COMMAND_ID_SAVE_CONFIG: Byte = 0x04 // Comando para guardar (escribir) la configuración

    private val MIME_TYPE_COMMAND = "application/x-cmd"
    private val MIME_TYPE_DATA = "application/x-data"


    // -----------------------------------------------------------------------
    // --- PARSING DE RESPUESTAS (0x81, 0x82, 0x83) ---
    // -----------------------------------------------------------------------

    /**
     * Convierte el ByteArray de 36 bytes (respuesta 0x82) en un objeto ProcessData estructurado.
     */
    fun parseProcessData(processBytes: ByteArray): ProcessData {
        if (processBytes.size != 36) {
            throw IllegalArgumentException("El tamaño de datos de proceso debe ser 36 bytes. Recibido: ${processBytes.size}")
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
     * Convierte el ByteArray de 96 bytes (respuesta 0x83) en un objeto ConfigurationData estructurado.
     * La respuesta del TAG (0x83) debe incluir el timestamp (96 bytes total).
     */
    fun parseConfigData(configBytes: ByteArray): ConfigurationData {
        // La respuesta 0x83 devuelve 23 floats + 1 int = 96 bytes.
        if (configBytes.size != 96) {
            throw IllegalArgumentException("El tamaño de datos de configuración debe ser 96 bytes (23F + 1I). Recibido: ${configBytes.size}")
        }

        // Se usa ByteBuffer con LITTLE_ENDIAN
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
            lastConfigurationDate = buffer.getInt(), // Último Int (timestamp)
        )
    }

    /**
     * Convierte el ByteArray de 12 bytes (respuesta 0x81) en IdentityData estructurada.
     */
    fun parseIdentityData(identityBytes: ByteArray): IdentityData {
        if (identityBytes.size != 12) {
            throw IllegalArgumentException("El tamaño de datos de identidad debe ser 12 bytes.")
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

    /**
     * Convierte el ByteArray de 36 bytes (respuesta 0x82) en un objeto ProcessData estructurado.
     */
    fun parseEngineeringData(engineeringBytes: ByteArray): EngineeringData {
        if (engineeringBytes.size != 40) {
            throw IllegalArgumentException("El tamaño de datos de Ingenieria debe ser 40 bytes. Recibido: ${engineeringBytes.size}")
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
            lux = buffer.getInt()
        )
    }



    // -----------------------------------------------------------------------
    // --- CREACIÓN DE COMANDOS (0x01, 0x02, 0x03, 0x04) ---
    // -----------------------------------------------------------------------

    /**
     * Serializa los 23 floats y el Int del timestamp (96 bytes) en un ByteArray.
     * Esta función es la base para el comando 0x04 de ESCRITURA.
     */
    fun serializeConfigData(config: ConfigurationData): ByteArray {

        // 🚨 PASO CRÍTICO: Actualizar la fecha de la configuración
        // Obtenemos el tiempo actual en milisegundos y lo convertimos a segundos.
        val currentEpochSeconds = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis()).toInt()

        // ASIGNACIÓN CRÍTICA: 96 bytes (23 floats + 1 int = 24 elementos * 4 bytes)
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
        return buffer.array() // Retorna exactamente 96 bytes.
    }

    /**
     * Crea un NdefMessage con UN ÚNICO NdefRecord de tipo MIME_TYPE_COMMAND.
     * REDUCCIÓN CRÍTICA: El payload es ahora de 1 byte para minimizar el tamaño del mensaje NDEF
     * y evitar problemas de truncamiento en la escritura (0x01, 0x02, 0x03).
     *
     * @param commandId El ID del comando (Byte) a enviar (ej: 0x01).
     * @return Un NdefMessage listo para ser escrito en el Tag.
     */
    fun createReadCommandMessage(commandId: Byte): NdefMessage {
        // Payload minimalista: solo el ID del comando (1 byte).
        val payload = byteArrayOf(commandId)

        val commandRecord = NdefRecord.createMime(MIME_TYPE_COMMAND, payload)
        // El NdefMessage contiene UN SOLO registro
        return NdefMessage(commandRecord)
    }

    /**
     * CRÍTICA para el Comando 0x04 (Escritura). ¡UN SOLO REGISTRO!
     * Combina un prefijo de comando de 1 byte (0x04) con la data de configuración serializada (96 bytes).
     * Payload Total: 97 bytes.
     *
     * @param writeData Los datos de configuración serializados (96 bytes).
     * @return El NdefMessage con un payload total de 97 bytes listo para ser escrito.
     */
    fun createWriteConfigMessage(writeData: ByteArray): NdefMessage {
        if (writeData.size != 96) {
            throw IllegalArgumentException("El payload de datos de configuración debe ser de 96 bytes. Recibido: ${writeData.size}")
        }

        // El payload total debe tener el tamaño del comando (1 byte) + los datos (96 bytes)
        val fullPayload = ByteArray(1 + writeData.size) // 97 bytes

        // Primer byte: el comando 0x04
        fullPayload[0] = COMMAND_WRITE_CONFIG

        // Copiar los 96 bytes de datos después del comando
        System.arraycopy(writeData, 0, fullPayload, 1, writeData.size)

        if (fullPayload.size != 97) {
            // Este error nunca debería ocurrir si el chequeo de 96 bytes es correcto
            throw IllegalStateException("Error de protocolo: El payload total para el Comando 0x04 debe ser de 97 bytes, pero fue ${fullPayload.size}")
        }

        // Creamos UN SOLO registro NDEF, como requiere el hardware del TAG.
        val record = NdefRecord.createMime(MIME_TYPE_COMMAND, fullPayload)
        return NdefMessage(arrayOf(record))
    }


    /**
     * Intenta decodificar el payload del NdefRecord.
     * Esto asume que el payload es una cadena de texto simple codificada en UTF-8.
     */
    fun parseDataPayload(record: NdefRecord): String? {
        if (record.tnf == NdefRecord.TNF_MIME_MEDIA && String(record.type) == MIME_TYPE_DATA) {
            return try {
                // Asume codificación UTF-8
                String(record.payload, Charset.forName("UTF-8"))
            } catch (e: Exception) {
                // Error al decodificar
                null
            }
        }
        return null
    }

}
