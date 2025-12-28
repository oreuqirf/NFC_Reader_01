package com.example.nfc_reader_01.utils

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Singleton centralizado para la gestión de logs de protocolo y eventos de la aplicación.
 *
 * Utiliza Kotlin SharedFlow para emitir el historial de logs completo a todos los suscriptores
 * (como NotificationsFragment) de forma reactiva y thread-safe.
 */
object LogManager {

    // Variable privada para almacenar y mutar el historial de logs (String gigante)
    private var _logHistory: String = ""

    // MutableSharedFlow que emite el historial de logs. Se utiliza replay = 1 para que
    // los nuevos colectores reciban inmediatamente el estado actual del log.
    private val _protocolLog = MutableSharedFlow<String>(replay = 1)

    // Exposición del SharedFlow como un flujo inmutable para que solo el LogManager
    // pueda emitir nuevos valores.
    val protocolLog: SharedFlow<String> = _protocolLog.asSharedFlow()

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    /**
     * Agrega un nuevo mensaje de log al historial y lo emite a todos los colectores activos.
     * @param message El mensaje de log a añadir.
     */
    suspend fun log(message: String) {
        // 1. Crear el nuevo mensaje formateado con timestamp
        val timestamp = timeFormat.format(Date())
        val newMessage = "[$timestamp] $message\n"

        // 2. Añadir al historial
        // Se añade al inicio (al principio) para que el log más nuevo se vea arriba.
        _logHistory = newMessage + _logHistory

        // 3. Emitir el historial completo.
        // Usa tryEmit para no suspender si no hay colectores, aunque con replay=1 no suele ser un problema.
        _protocolLog.emit(_logHistory)
    }

    /**
     * Limpia el historial de logs y notifica a los suscriptores.
     */
    suspend fun clearLogs() {
        _logHistory = ""
        _protocolLog.emit("")
    }
}
