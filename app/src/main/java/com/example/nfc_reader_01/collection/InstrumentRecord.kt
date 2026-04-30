package com.example.nfc_reader_01.collection

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "batch_collection_table")
data class InstrumentRecord(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,

    // Identidad del Dispositivo
    val serialNumber: String,
    val firmwareVersion: String,

    // Configuración General
    val kMeter: Float,
    val low_stability: Float,
    val high_stability: Float,

    // Temperaturas
    val tempRawLow: Float,
    val tempRawHigh: Float,
    val tempCalLow: Float,
    val tempCalHigh: Float,

    // Errores de Flujo
    val fcQ1Error: Float,
    val fcQ2Error: Float,
    val fcQ035Error: Float,
    val fcQ100Error: Float,
    val fcQ10LmError: Float,
    val fcQ3Error: Float,

    // Fechas
    val deviceLastConfigDate: Long,
    val timestamp: Long = System.currentTimeMillis()
)