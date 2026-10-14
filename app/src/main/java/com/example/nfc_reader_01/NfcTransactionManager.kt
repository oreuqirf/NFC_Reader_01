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


    private suspend fun handleWriteTransaction(
        commandId: Byte,
        ndef: Ndef?,
        ndefFormatable: NdefFormatable?,
        nfcV: NfcV?
    ) {
        var success = false

        // --- SOLUCIÓN: SI ES CONFIGURACIÓN (80 BYTES), USAMOS NFCV POR BLOQUES DIRECTAMENTE ---
        if (commandId == CMD_WRITE_CONFIG && nfcV != null) {
            val configData = viewModel.configDataToWrite.value
            if (configData != null && configData.size == NfcConstants.CONFIG_BYTE_SIZE) {
                scope.launch { LogManager.log("Iniciando escritura robusta por bloques NfcV (${configData.size} bytes)...") }
                success = executeNfcVWriteConfigBlocks(nfcV, commandId, configData)
            }
        }

        // --- SI NO ES CONFIGURACIÓN O FALLÓ, USAMOS NDEF ESTÁNDAR ---
        if (!success) {
            if (ndef != null) {
                success = executeNdefWriteCommand(ndef, commandId)
            } else if (ndefFormatable != null) {
                success = executeNdefFormatAndWriteCommand(ndefFormatable, commandId)
            }
        }

        if (success) {
            viewModel.clearCommand()
            viewModel.setUiMessage("¡Escritura exitosa! Acerque el TAG para leer respuesta.")
            viewModel.emitWriteStatus("Escritura OK. Comando enviado.")
        } else {
            val totalBytesExpected = NfcConstants.CONFIG_BYTE_SIZE + 1
            val errorMsg = "ERROR: No se pudo completar la escritura de $totalBytesExpected bytes. Reintente."
            scope.launch { LogManager.log(errorMsg) }
            viewModel.setUiMessage(errorMsg)
            viewModel.emitWriteStatus("ERROR: Fallo en transacción. Reintente.")
        }
    }


    private fun executeNfcVWriteConfigBlocks(nfcV: NfcV, commandId: Byte, configData: ByteArray): Boolean {
        val fullPayload = byteArrayOf(commandId) + configData // 1 byte de comando + 80 bytes = 81 bytes
        val flags: Byte = 0x02
        val cmdWrite: Byte = 0x21.toByte() // Write Single Block (ISO 15693)
        val startBlock = 3 // Bloque inicial donde el MSP430 espera recibir los datos

        return try {
            if (!nfcV.isConnected) nfcV.connect()

            val blockSize = 4
            val totalBlocks = (fullPayload.size + blockSize - 1) / blockSize

            for (i in 0 until totalBlocks) {
                val blockIndex = startBlock + i
                val startByte = i * blockSize
                val endByte = minOf(startByte + blockSize, fullPayload.size)

                var blockData = fullPayload.copyOfRange(startByte, endByte)
                if (blockData.size < blockSize) {
                    blockData += ByteArray(blockSize - blockData.size) { 0x00 }
                }

                val cmd = byteArrayOf(flags, cmdWrite, blockIndex.toByte()) + blockData
                val resp = nfcV.transceive(cmd)

                if (resp.isEmpty() || resp[0].and(0x01.toByte()) != 0.toByte()) {
                    Log.e(TAG, "Error escribiendo bloque $blockIndex via NfcV")
                    return false
                }

                // Pausa fundamental para evitar desbordes en la EEPROM física
                Thread.sleep(22)
            }

            true
        } catch (e: Exception) {
            Log.e(TAG, "Excepción en escritura NfcV por bloques: ${e.message}", e)
            false
        } finally {
            try { nfcV.close() } catch (_: IOException) {}
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
        val messageSize = message.toByteArray().size

        // LOG MEJORADO PARA DETECTAR PROBLEMAS DE TAMAÑO
        val logMsg = "Escribiendo comando 0x${commandId.toHexString()} (Payload: ${payload.size}b, NDEF Total: ${messageSize}b, Máx Soportado por TAG: ${ndef.maxSize}b)..."
        scope.launch { LogManager.log(logMsg) }

        return try {
            if (!ndef.isConnected) ndef.connect()
            if (!ndef.isWritable) throw IOException("TAG no es escribible")

            // AQUÍ ES DONDE PROBABLEMENTE ESTÁ CHOCANDO
            if (ndef.maxSize < messageSize) {
                throw IOException("Mensaje demasiado grande: requiere $messageSize bytes, el TAG solo soporta ${ndef.maxSize} bytes")
            }

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
        // Bloques 0 y 1: Capability Container de 8KB (E2 40 00 01 00 00 04 00)
        val block0 = byteArrayOf(0xE2.toByte(), 0x40.toByte(), 0x00, 0x01)
        val block1 = byteArrayOf(0x00, 0x00, 0x04.toByte(), 0x00)

        // Bloque 2: TLV NDEF Vacío (0x03=Mensaje NDEF, 0x00=Longitud cero, 0xFE=Terminador)
        val block2 = byteArrayOf(0x03, 0x00, 0xFE.toByte(), 0x00)

        val flags: Byte = 0x02
        val cmdWrite: Byte = 0x21.toByte() // Comando ISO 15693: Write Single Block

        return try {
            if (!nfcV.isConnected) nfcV.connect()

            // --- 1. Escribir Bloque 0 ---
            var cmd = byteArrayOf(flags, cmdWrite, 0x00) + block0
            var resp = nfcV.transceive(cmd)
            // Validar si está vacío o si el bit de error (0x01) está encendido
            if (resp.isEmpty() || resp[0].and(0x01.toByte()) != 0.toByte()) return false

            Thread.sleep(25) // Pausa vital para que la EEPROM física guarde el bloque

            // --- 2. Escribir Bloque 1 ---
            cmd = byteArrayOf(flags, cmdWrite, 0x01) + block1
            resp = nfcV.transceive(cmd)
            if (resp.isEmpty() || resp[0].and(0x01.toByte()) != 0.toByte()) return false

            Thread.sleep(25) // Pausa vital

            // --- 3. Escribir Bloque 2 (NDEF Vacío) ---
            cmd = byteArrayOf(flags, cmdWrite, 0x02) + block2
            resp = nfcV.transceive(cmd)
            if (resp.isEmpty() || resp[0].and(0x01.toByte()) != 0.toByte()) return false

            true
        } catch (e: Exception) {
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
            if (data == null || data.size != NfcConstants.CONFIG_BYTE_SIZE) { // Usamos la constante CONFIG_BYTE_SIZE (80)
                val currentSize = data?.size ?: 0
                val errorMsg = "ERROR: Configuración inválida. Se esperaban ${NfcConstants.CONFIG_BYTE_SIZE} bytes, pero hay $currentSize bytes listos."
                scope.launch { LogManager.log(errorMsg) }
                viewModel.setUiMessage("Error: Tamaño de configuración incorrecto.")
                viewModel.emitWriteStatus("ERROR: Faltan datos o el tamaño no coincide.")
                null
            } else {
                val fullPayload = byteArrayOf(commandId) + data // 1 byte de comando + 80 bytes de config = 81 bytes
                scope.launch { LogManager.log("Payload de configuración preparado: ${fullPayload.size} bytes en total.") }
                fullPayload
            }
        } else {
            // Para calibración y otros comandos cortos
            viewModel.configDataToWrite.value?.let { byteArrayOf(commandId) + it } ?: byteArrayOf(commandId)
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