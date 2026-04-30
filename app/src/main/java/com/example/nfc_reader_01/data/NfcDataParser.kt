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
    val low_stability: Float,   // 0-100%
    val high_stability: Float,    // 0-100%
    val lowTempUnscaled: Float, val highTempUnscaled: Float,
    val lowTempCorrected: Float, val highTempCorrected: Float,
    val fcQ1_flow: Float, val fcQ1_temperature: Float, val fcQ1_error: Float,
    val fcQ2_flow: Float, val fcQ2_temperature: Float, val fcQ2_error: Float,
    val fcQ0_35_flow: Float, val fcQ0_35_temperature: Float, val fcQ0_35_error: Float,
    val fcQ1_00_flow: Float, val fcQ1_00_temperature: Float, val fcQ1_00_error: Float,
    val fcQ10_00_flow: Float, val fcQ10_00_temperature: Float, val fcQ10_00_error: Float,
    val fcQ3_flow: Float, val fcQ3_temperature: Float, val fcQ3_error: Float,
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
    val temperature: Int
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

    // --- 0x03 CONFIGURACIÓN ---
    fun parseConfigData(data: ByteArray): ConfigurationData {
        // Payload real: 106 bytes
        if (data.size < 104) throw IllegalArgumentException("Datos insuficientes Configuración (${data.size} bytes)")
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)

        // CORRECCIÓN: Lógica unificada
        if (data.size > 100 && data[0] == 0x83.toByte()) {
            buffer.position(1)
        } else {
            buffer.position(0)
        }

        val kMeter = buffer.float

        val stability = buffer.float
        val stopTime  = buffer.float

        val lowTempUnscaled = buffer.float; val highTempUnscaled = buffer.float
        val lowTempCorrected = buffer.float; val highTempCorrected = buffer.float

        // Q1
        val q1_flow = buffer.float; val q1_error = buffer.float; val q1_temp = buffer.float
        // Q2
        val q2_flow = buffer.float; val q2_error = buffer.float; val q2_temp = buffer.float
        // 0.35
        val q035_flow = buffer.float; val q035_error = buffer.float; val q035_temp = buffer.float
        // 1.00
        val q100_flow = buffer.float; val q100_error = buffer.float; val q100_temp = buffer.float
        // 10.00
        val q10_flow = buffer.float; val q10_error = buffer.float; val q10_temp = buffer.float
        // Q3
        val q3_flow = buffer.float; val q3_error = buffer.float; val q3_temp = buffer.float

        val lastDate = buffer.int

        return ConfigurationData(
            kMeter, stability, stopTime,
            lowTempUnscaled, highTempUnscaled, lowTempCorrected, highTempCorrected,
            q1_flow, q1_temp, q1_error,
            q2_flow, q2_temp, q2_error,
            q035_flow, q035_temp, q035_error,
            q100_flow, q100_temp, q100_error,
            q10_flow, q10_temp, q10_error,
            q3_flow, q3_temp, q3_error,
            lastDate
        )
    }

    // --- 0x04 SERIALIZAR ---
    fun serializeConfigData(config: ConfigurationData): ByteArray {
        val buffer = ByteBuffer.allocate(104).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putFloat(config.kMeter)

        buffer.putFloat(config.low_stability)
        buffer.putFloat(config.high_stability)

        buffer.putFloat(config.lowTempUnscaled); buffer.putFloat(config.highTempUnscaled)
        buffer.putFloat(config.lowTempCorrected); buffer.putFloat(config.highTempCorrected)

        buffer.putFloat(config.fcQ1_flow); buffer.putFloat(config.fcQ1_error); buffer.putFloat(config.fcQ1_temperature)
        buffer.putFloat(config.fcQ2_flow); buffer.putFloat(config.fcQ2_error); buffer.putFloat(config.fcQ2_temperature)
        buffer.putFloat(config.fcQ0_35_flow); buffer.putFloat(config.fcQ0_35_error); buffer.putFloat(config.fcQ0_35_temperature)
        buffer.putFloat(config.fcQ1_00_flow); buffer.putFloat(config.fcQ1_00_error); buffer.putFloat(config.fcQ1_00_temperature)
        buffer.putFloat(config.fcQ10_00_flow); buffer.putFloat(config.fcQ10_00_error); buffer.putFloat(config.fcQ10_00_temperature)
        buffer.putFloat(config.fcQ3_flow); buffer.putFloat(config.fcQ3_error); buffer.putFloat(config.fcQ3_temperature)

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

        return EngineeringData(vol, volU, tempU, flowU, ttof, dtof, std, time, luxTh, lux, rak, last, temp)
    }
}
