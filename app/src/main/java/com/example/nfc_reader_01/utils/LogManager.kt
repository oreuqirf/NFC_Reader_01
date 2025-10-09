package com.example.nfc_reader_01.utils

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Centralized singleton for managing protocol logs and application events.
 *
 * It uses Kotlin SharedFlow to emit the complete log history to all subscribers
 * (like NotificationsFragment) in a reactive and thread-safe way.
 */
object LogManager {

    // Private variable to store and mutate the log history (giant String)
    private var _logHistory: String = ""

    // MutableSharedFlow that emits the log history. replay = 1 is used so that
    // new collectors immediately receive the current state of the log.
    private val _protocolLog = MutableSharedFlow<String>(replay = 1)

    // Exposure of the SharedFlow as an immutable flow so that only LogManager
    // can emit new values.
    val protocolLog: SharedFlow<String> = _protocolLog.asSharedFlow()

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    /**
     * Adds a new log message to the history and emits it to all active collectors.
     * @param message The log message to add.
     */
    suspend fun log(message: String) {
        // 1. Create the new formatted message with a timestamp
        val timestamp = timeFormat.format(Date())
        val newMessage = "[$timestamp] $message\n"

        // 2. Add to the history
        // It is added at the beginning so that the newest log is seen at the top.
        _logHistory = newMessage + _logHistory

        // 3. Emit the complete history.
        _protocolLog.emit(_logHistory)
    }

    /**
     * Clears the log history and notifies subscribers.
     */
    suspend fun clearLogs() {
        _logHistory = ""
        _protocolLog.emit("")
    }
}
