package com.example.nfc_reader_01

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Objeto que contiene funciones de utilidad para dar formato a las fechas.
 */
object DateFormater {
    /**
     * Convierte un timestamp Epoch (segundos) a una cadena de fecha y hora formateada.
     * @param timestamp El valor Epoch en segundos (Long).
     * @return La fecha formateada (String) o "Nunca configurado" si es 0.
     */
    fun formatEpochTimestamp(timestamp: Long): String {
        // El valor 0 se usa para indicar que nunca ha sido configurado
        if (timestamp == 0L) {
            return "Nunca configurado"
        }

        // Asumiendo que el timestamp es en SEGUNDOS (común en protocolos embebidos)
        val milliseconds = timestamp * 1000L

        // Formato: día/mes/año hora:minuto:segundo
        // Usamos Locale.getDefault() para respetar la configuración regional del usuario
        val sdf = SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault())
        val date = Date(milliseconds)
        return sdf.format(date)
    }
}
