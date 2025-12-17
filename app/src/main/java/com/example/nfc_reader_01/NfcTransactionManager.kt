package com.example.nfc_reader_01

import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import android.nfc.tech.NfcV
import android.util.Log
import com.example.nfc_reader_01.SharedNfcViewModel
import com.example.nfc_reader_01.utils.LogManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.charset.StandardCharsets
import kotlin.experimental.and

/**
 * Clase responsable de manejar todas las transacciones NFC (lectura y escritura).
 * Encapsula la lógica de NDEF, fallbacks y comunicación con el ViewModel.
 */
class NfcTransactionManager(
    private val viewModel: SharedNfcViewModel,
    private val scope: CoroutineScope // Scope para lanzar logs y actualizaciones de UI
) {

    companion object {
        private const val TAG = "NfcTransactionManager"

        // Constantes de Comandos
        const val CMD_READ_IDENTITY: Byte = 0x01
        const val CMD_READ_PROCESS: Byte = 0x02
        const val CMD_READ_CONFIG: Byte = 0x03
        const val CMD_WRITE_CONFIG: Byte = 0x04
        const val CMD_READ_ENGINEERING: Byte = 0x05
        const val CMD_FACTORY_RESET: Byte = 0x0A
        const val CMD_GET_SYSTEM_INFO: Byte = 0x2B.toByte()

        // Tipos MIME
        private const val MIME_COMMAND_TYPE = "application/x-cmd"
        private const val MIME_RESPONSE_TYPE = "application/x-data"
    }

    /**
     * Procesa la transacción NDEF completa (escritura o lectura) en un hilo IO.
     */
    suspend fun processTag(tag: Tag) = withContext(Dispatchers.IO) {
        try {
            val commandId = viewModel.pendingCommand.value
            val ndef = Ndef.get(tag)
            val ndefFormatable = NdefFormatable.get(tag)
            val nfcV = NfcV.get(tag)

            // Diagnóstico
            scope.launch {
                LogManager.log("DBG NDEF Check: Ndef=${if(ndef != null) "OK" else "NULL"}, " +
                        "Formatable=${if(ndefFormatable != null) "OK" else "NULL"}, " +
                        "NfcV=${if(nfcV != null) "OK" else "NULL"}")
            }

            // --- CASO 1: COMANDO PENDIENTE (Primer Scan - Escritura) ---
            if (commandId != null) {
                handleWriteTransaction(commandId, ndef, ndefFormatable, nfcV)
                return@withContext
            }

            // --- CASO 2: NO HAY COMANDO (Segundo Scan - Lectura) ---
            handleReadTransaction(ndef)

        } catch (e: Exception) {
            val errorMsg = "Error CRÍTICO de Transacción: ${e.message}"
            scope.launch { LogManager.log(errorMsg) }
            viewModel.setUiMessage(errorMsg)
            viewModel.emitWriteStatus("ERROR CRÍTICO: Fallo en transacción NFC.")
        }
    }

    /**
     * Maneja la lógica de escritura con sus respectivos fallbacks.
     */
    private suspend fun handleWriteTransaction(
        commandId: Byte,
        ndef: Ndef?,
        ndefFormatable: NdefFormatable?,
        nfcV: NfcV?
    ) {
        var success = false

        // 1. Intentar escritura NDEF estándar
        if (ndef != null) {
            success = executeNdefWriteCommand(ndef, commandId)
        }

        // 2. Intentar formateo y escritura (si no es NDEF pero es formateable)
        if (!success && ndefFormatable != null) {
            success = executeNdefFormatAndWriteCommand(ndefFormatable, commandId)
        }

        // 3. Fallback Avanzado: Forzar CC con NfcV (si todo lo anterior falla)
        if (!success && nfcV != null) {
            scope.launch { LogManager.log("Activando Fallback Avanzado: Formateo CC via NfcV.") }
            val formatSuccess = executeNfcVForceFormatOnlyFallback(nfcV)

            if (formatSuccess) {
                viewModel.setUiMessage("Primer Scan FORZADO (NfcV) OK. ¡Acerque el TAG nuevamente!")
                viewModel.emitWriteStatus("FALLBACK NfcV OK: TAG formateado. Escanee de nuevo.")
            } else {
                val errorMsg = "ERROR: Fallo al forzar escritura/formato. TAG sin soporte o bloqueado."
                viewModel.setUiMessage(errorMsg)
                viewModel.emitWriteStatus("ERROR NfcV: Fallo forzado.")
            }
            return
        }

        // Resultado final de la escritura NDEF
        if (success) {
            viewModel.clearCommand()
        } else if (!success && nfcV == null) {
            // Solo reportar error si no se intentó el fallback NfcV (que maneja sus propios errores)
            viewModel.setUiMessage("ERROR: Fallo al procesar comando. TAG no soportado.")
        }
    }

    /**
     * Maneja la lectura de la respuesta del TAG.
     */
    private fun handleReadTransaction(ndef: Ndef?) {
        if (ndef != null) {
            try {
                ndef.connect()
                executeNdefReadResponse(ndef)
            } catch (e: IOException) {
                val errorMsg = "Error NDEF Lectura (I/O): ${e.message}"
                scope.launch { LogManager.log(errorMsg) }
                viewModel.setUiMessage(errorMsg)
                viewModel.emitWriteStatus("ERROR I/O: Fallo al leer respuesta.")
            } finally {
                try { ndef.close() } catch (_: IOException) {}
            }
        } else {
            viewModel.setUiMessage("Advertencia: TAG no válido/formateado. Listo para recibir comando.")
        }
    }

    // --- MÉTODOS DE EJECUCIÓN ESPECÍFICOS ---

    private fun executeNdefWriteCommand(ndef: Ndef, commandId: Byte): Boolean {
        val payload = createPayload(commandId) ?: return false
        val message = NdefMessage(NdefRecord.createMime(MIME_COMMAND_TYPE, payload))
        val logMsg = "Escribiendo comando 0x${commandId.toHexString()} (${payload.size} bytes)..."

        scope.launch { LogManager.log(logMsg) }

        return try {
            if (!ndef.isConnected) ndef.connect()
            if (!ndef.isWritable) throw IOException("TAG no es escribible")
            if (ndef.maxSize < message.toByteArray().size) throw IOException("Mensaje demasiado grande")

            ndef.writeNdefMessage(message)

            viewModel.setUiMessage("Primer Scan OK. ¡Acerque el TAG para leer respuesta!")
            viewModel.emitWriteStatus("Escritura NDEF OK. Comando enviado.")

            if (commandId == CMD_WRITE_CONFIG) viewModel.setConfigDataToWrite(null)
            true

        } catch (e: Exception) {
            handleException(e, "Escritura NDEF")
            false
        } finally {
            try { ndef.close() } catch (_: IOException) {}
        }
    }

    private fun executeNdefFormatAndWriteCommand(formatable: NdefFormatable, commandId: Byte): Boolean {
        val payload = createPayload(commandId) ?: return false
        val message = NdefMessage(NdefRecord.createMime(MIME_COMMAND_TYPE, payload))

        scope.launch { LogManager.log("Primer Scan FORZADO (Formatable)...") }

        return try {
            formatable.connect()
            formatable.format(message)

            viewModel.setUiMessage("Primer Scan FORZADO OK. ¡Acerque el TAG para leer respuesta!")
            viewModel.emitWriteStatus("Escritura Forzada OK.")

            if (commandId == CMD_WRITE_CONFIG) viewModel.setConfigDataToWrite(null)
            true

        } catch (e: Exception) {
            handleException(e, "Formato NDEF")
            false
        } finally {
            try { formatable.close() } catch (_: IOException) {}
        }
    }

    private fun executeNfcVForceFormatOnlyFallback(nfcV: NfcV): Boolean {
        // Implementación de bajo nivel para escribir el Capability Container (CC)
        val ccBlock = byteArrayOf(0xE1.toByte(), 0x40.toByte(), 0x00, 0x40) // Max size 64 bytes
        val flags: Byte = 0x02
        val cmdWrite: Byte = 0x21.toByte()

        return try {
            if (!nfcV.isConnected) nfcV.connect()
            val cmd = byteArrayOf(flags, cmdWrite, 0x00) + ccBlock
            val response = nfcV.transceive(cmd)

            if (response.isEmpty() || (response.size == 1 && response[0].and(0x01) == 0.toByte())) {
                true
            } else {
                scope.launch { LogManager.log("ERROR NfcV CC: Código ${response.toHexString()}") }
                false
            }
        } catch (e: Exception) {
            handleException(e, "Fallback NfcV")
            false
        } finally {
            try { nfcV.close() } catch (_: IOException) {}
        }
    }

    private fun executeNdefReadResponse(ndef: Ndef) {
        val message = ndef.ndefMessage ?: return

        for (record in message.records) {
            if (isMimeType(record, MIME_RESPONSE_TYPE)) {
                val payload = record.payload
                if (payload.isNotEmpty()) {
                    val cmdId = payload[0]
                    val data = payload.copyOfRange(1, payload.size)

                    scope.launch { LogManager.log("Segundo Scan OK: 0x${cmdId.toHexString()} (${data.size} bytes)") }
                    viewModel.distributeResponseData(cmdId, data)
                    viewModel.setUiMessage("Segundo Scan OK: Datos recibidos.")
                    return
                }
            } else if (isMimeType(record, MIME_COMMAND_TYPE)) {
                viewModel.emitWriteStatus("ERROR SINCRO: TAG aún tiene el comando. Reintente.")
                return
            }
        }
        viewModel.setUiMessage("Segundo Scan: No se halló respuesta válida.")
    }

    // --- HELPERS ---

    private fun createPayload(commandId: Byte): ByteArray? {
        return if (commandId == CMD_WRITE_CONFIG) {
            val data = viewModel.configDataToWrite.value
            if (data == null || data.size != 96) {
                viewModel.setUiMessage("ERROR: Datos de configuración incompletos.")
                viewModel.emitWriteStatus("ERROR: Configuración no lista.")
                null
            } else {
                byteArrayOf(commandId) + data
            }
        } else {
            byteArrayOf(commandId)
        }
    }

    private fun handleException(e: Exception, context: String) {
        val msg = if (e is SecurityException) "Error Seguridad (Tag perdido)" else "Error I/O (${e.message})"
        scope.launch { LogManager.log("$context: $msg") }
        viewModel.setUiMessage(msg)
        viewModel.emitWriteStatus(msg)
    }

    private fun isMimeType(record: NdefRecord, type: String): Boolean {
        return record.tnf == NdefRecord.TNF_MIME_MEDIA &&
                String(record.type, StandardCharsets.US_ASCII) == type
    }

    // Extension local para Hex
    private fun Byte.toHexString() = String.format("%02X", this)
    private fun ByteArray.toHexString() = joinToString(" ") { "%02X".format(it) }
}