package com.example.nfc_reader_01.data

import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale

// -------------------------------------------------------------------------
// 1. DATA CLASSES (Sin cambios)
// -------------------------------------------------------------------------

data class IdentityData(
    val deviceId: Long,
    val firmwareVersion: String,
    val hardwareVersion: String,
    val lastConfigurationDate: String
)

data class ProcessData(
    val volume: Long,
    val flowRate: Int,
    val temperature: Int,
    val battery: Int,
    val statusFlags: Int,
    val directFlowPeriod: Int,
    val reverseFlowPeriod: Int,
    val noFlowPeriod: Int,
    val leakageFlowPeriod: Int
)

data class ConfigurationData(
    val kMeter: Float,
    val lowStability: Float,
    val highStability: Float,

    val lowTempUnscaled: Float, val highTempUnscaled: Float,
    val lowTempCorrected: Float, val highTempCorrected: Float,

    // ¡Solo los 8 errores (32 bytes en total)!
    val fcQ1_error: Float,
    val fcQ2_error: Float,
    val fcP3_error: Float,
    val fcP4_error: Float,
    val fcP5_error: Float,
    val fcP6_error: Float,
    val fcP7_error: Float,
    val fcP8_error: Float,
    val fc10_00_error: Float,
    val fcP10_error: Float,
    val fcQ3_error: Float,
    val fcQ4_error: Float,

    val lastConfigurationDate: Int
)

data class EngineeringData(
    val volumeLiters: Int,
    val volumeLitersUncal: Int,
    val temperatureUncal: Int,
    val flowUncal: Int,
    val ttof: Int,
    val dtof: Int,
    val stdDev: Int,
    val time: Int,
    val lux_threshold: Int,
    val lux: Int,
    val rakFrameCounter: Int,
    val lastTripFlow: Int,
    val temperature: Int,
    val index: Int,
    val error: Int,
    val gain: Int,
    val maxRaw: Int
)

// -------------------------------------------------------------------------
// 2. OBJETO PARSER (CORREGIDO)
// -------------------------------------------------------------------------

object NfcDataParser {

    private const val TAG = "NfcDataParser"

    // --- 0x01 IDENTIDAD ---
    fun parseIdentityData(data: ByteArray): IdentityData {
        // Payload real: 12 bytes
        if (data.size < 12) throw IllegalArgumentException("Datos insuficientes Identidad")
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)

        // CORRECCIÓN: Solo saltar si hay byte extra Y coincide con header
        if (data.size > 12 && data[0] == 0x81.toByte()) {
            buffer.position(1)
        } else {
            buffer.position(0)
        }

        val deviceId = buffer.int.toLong() and 0xFFFFFFFFL
        val fwRaw = buffer.int
        val fwMajor = (fwRaw shr 16) and 0xFF
        val fwMinor = (fwRaw shr 8) and 0xFF
        val fwPatch = fwRaw and 0xFF
        val fwVersion = String.format(Locale.US, "Rev %d.%02d.%02d", fwMajor, fwMinor, fwPatch)
        val timestamp = buffer.int
        val date = java.util.Date(timestamp.toLong() * 1000)
        val sdf = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

        return IdentityData(deviceId, fwVersion, "-", sdf.format(date))
    }

    // --- 0x02 PROCESO ---
    fun parseProcessData(data: ByteArray): ProcessData {
        // Payload real: 36 bytes
        if (data.size < 36) throw IllegalArgumentException("Datos insuficientes Proceso")
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)

        // CORRECCIÓN: Solo saltar si hay byte extra Y coincide con header
        if (data.size > 36 && data[0] == 0x82.toByte()) {
            buffer.position(1)
        } else {
            buffer.position(0)
        }

        val volume = buffer.int.toLong() and 0xFFFFFFFFL
        val flowRate = buffer.int
        val temperature = buffer.int
        val battery = buffer.int
        val statusFlags = buffer.int
        val directTime = buffer.int
        val reverseTime = buffer.int
        val noFlowTime = buffer.int
        val leakageTime = buffer.int

        return ProcessData(volume, flowRate, temperature, battery, statusFlags, directTime, reverseTime, noFlowTime, leakageTime)
    }

    fun parseConfigData(data: ByteArray): ConfigurationData {
        val expectedSize = 80 // 3 (config) + 4 (temps) + 12 (errores de 4 bytes) + 1 (fecha int) = ~80 bytes mínimo
        if (data.size < 64) throw IllegalArgumentException("Datos insuficientes (${data.size} bytes)")
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)

        if (data.size > (expectedSize - 4) && data[0] == 0x83.toByte()) {
            buffer.position(1)
        } else {
            buffer.position(0)
        }

        val kMeter = buffer.float
        val lowStability = buffer.float
        val highStability = buffer.float

        // Leyendo temperaturas desescaladas como enteros (según corregimos antes) y corregidas como floats
        val lowTempUnscaled = buffer.int.toFloat() / 10.0f
        val highTempUnscaled = buffer.int.toFloat() / 10.0f
        val lowTempCorrected = buffer.float
        val highTempCorrected = buffer.float

        // --- Leer los 12 errores en el orden exacto solicitado ---
        val fcQ1_error = buffer.float
        val fcQ2_error = buffer.float
        val fcP3_error = buffer.float
        val fcP4_error = buffer.float
        val fcP5_error = buffer.float
        val fcP6_error = buffer.float
        val fcP7_error = buffer.float
        val fcP8_error = buffer.float
        val fc10_00_error = buffer.float
        val fcP10_error = buffer.float
        val fcQ3_error = buffer.float
        val fcQ4_error = buffer.float

        val lastDate = buffer.int

        return ConfigurationData(
            kMeter, lowStability, highStability,
            lowTempUnscaled, highTempUnscaled, lowTempCorrected, highTempCorrected,
            fcQ1_error, fcQ2_error, fcP3_error, fcP4_error,
            fcP5_error, fcP6_error, fcP7_error, fcP8_error,
            fc10_00_error, fcP10_error, fcQ3_error, fcQ4_error,
            lastDate
        )
    }

    fun serializeConfigData(config: ConfigurationData): ByteArray {
        val totalSize = 80 // Ajusta al nuevo tamaño exacto
        val buffer = ByteBuffer.allocate(totalSize).order(ByteOrder.LITTLE_ENDIAN)

        buffer.putFloat(config.kMeter)
        buffer.putFloat(config.lowStability)
        buffer.putFloat(config.highStability)

        buffer.putFloat(config.lowTempUnscaled); buffer.putFloat(config.highTempUnscaled)
        buffer.putFloat(config.lowTempCorrected); buffer.putFloat(config.highTempCorrected)

        // Escribir los 8 errores en orden
        buffer.putFloat(config.fcQ1_error)
        buffer.putFloat(config.fcQ2_error)
        buffer.putFloat(config.fcP3_error)
        buffer.putFloat(config.fcP4_error)
        buffer.putFloat(config.fcP5_error)
        buffer.putFloat(config.fcP6_error)
        buffer.putFloat(config.fcP7_error)
        buffer.putFloat(config.fcP8_error)
        buffer.putFloat(config.fc10_00_error)
        buffer.putFloat(config.fcP10_error)
        buffer.putFloat(config.fcQ3_error)
        buffer.putFloat(config.fcQ4_error)

        buffer.putInt(config.lastConfigurationDate)

        return buffer.array()
    }


    // --- 0x05 INGENIERÍA ---
    fun parseEngineeringData(data: ByteArray): EngineeringData {
        // Payload real: 48 bytes (mínimo)
        if (data.size < 48) throw IllegalArgumentException("Datos insuficientes Ingeniería")
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)

        // CORRECCIÓN: Solo saltar si hay byte extra Y coincide con header
        if (data.size > 48 && data[0] == 0x85.toByte()) {
            buffer.position(1)
        } else {
            buffer.position(0)
        }

        val vol = buffer.int; val volU = buffer.int; val tempU = buffer.int; val flowU = buffer.int
        val ttof = buffer.int; val dtof = buffer.int; val std = buffer.int; val time = buffer.int

        val luxTh = if (buffer.remaining() >= 4) buffer.int else 0
        val lux = if (buffer.remaining() >= 4) buffer.int else 0
        val rak = if (buffer.remaining() >= 4) buffer.int else 0
        val last = if (buffer.remaining() >= 4) buffer.int else 0
        val temp = if (buffer.remaining() >= 4) buffer.int else 0

        // Lectura de tus 4 nuevas variables
        val index = if (buffer.remaining() >= 4) buffer.int else 0
        val error = if (buffer.remaining() >= 4) buffer.int else 0
        val gain = if (buffer.remaining() >= 4) buffer.int else 0
        val maxRaw = if (buffer.remaining() >= 4) buffer.int else 0

        return EngineeringData(
            vol, volU, tempU, flowU, ttof, dtof, std, time,
            luxTh, lux, rak, last, temp, index, error, gain, maxRaw
        )
    }
}
