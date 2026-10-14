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
    val lowStability: Float,
    val highStability: Float,

    // Temperaturas
    val tempRawLow: Float,
    val tempRawHigh: Float,
    val tempCalLow: Float,
    val tempCalHigh: Float,

    // Errores de Flujo (Curva de 8 Puntos)
    val fcQ1Error: Float,
    val fcQ2Error: Float,
    val fcP3Error: Float,      // Reemplaza fcQ035Error
    val fcP4Error: Float,      // Reemplaza fcQ100Error
    val fcP5Error: Float,      // Reemplaza fcQ035Error
    val fcP6Error: Float,      // Reemplaza fcQ100Error
    val fcP7Error: Float,      // Reemplaza fcQ035Error
    val fcP8Error: Float,      // Reemplaza fcQ100Error
    val fc10LmError: Float,    // Ajustado para coincidir con la función de guardado
    val fcP10Error: Float,     // Nuevo
    val fcQ3Error: Float,
    val fcQ4Error: Float,      // Nuevo

    // Fechas
    val deviceLastConfigDate: Long,
    val timestamp: Long = System.currentTimeMillis()
)