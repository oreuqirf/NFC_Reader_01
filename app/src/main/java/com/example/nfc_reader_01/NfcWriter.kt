package com.example.nfc_reader_01

import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.NdefMessage
import android.util.Log
import java.io.IOException
import java.lang.SecurityException

/**
 * Clase de utilidad para manejar comandos de escritura en tags NFC.
 *
 * NOTA: Esta implementación prioriza la escritura NDEF. La lógica de fallback
 * a NFCV (ISO 15693) se indica en los comentarios, pero requeriría una
 * implementación detallada de comandos ISO 15693 (como NfcV.get(tag))
 * para ser totalmente funcional. El enfoque aquí es la captura de errores
 * durante la fase crítica de conexión/escritura NDEF.
 */
class NfcWriter {

    private val TAG = "NfcWriter"

    /**
     * Intenta escribir un mensaje NDEF en el tag.
     *
     * @param tag El objeto Tag detectado.
     * @param ndefMessage El mensaje NDEF a escribir.
     * @return Una cadena de texto indicando el resultado de la operación (éxito o error).
     */
    fun executeNdefWriteCommand(tag: Tag, ndefMessage: NdefMessage): String {
        val ndef = Ndef.get(tag)

        // 1. Lógica principal: Intentar escritura NDEF
        if (ndef != null) {
            try {
                // Conexión al tag
                ndef.connect()

                // Comprobar si el tag es escribible y tiene espacio
                if (!ndef.isWritable) {
                    return "Error: El tag no es escribible (NDEF)."
                }
                if (ndef.maxSize < ndefMessage.toByteArray().size) {
                    return "Error: El mensaje es demasiado grande para este tag (NDEF)."
                }

                // Escribir el mensaje NDEF
                ndef.writeNdefMessage(ndefMessage)

                // Si la escritura es exitosa, se desconecta al final del bloque try/finally
                Log.d(TAG, "Escritura NDEF exitosa.")
                return "Escritura NDEF exitosa: ¡Datos guardados correctamente!"

            } catch (e: SecurityException) {
                // Captura la excepción que ocurre si el permiso se pierde (tag movido)
                Log.e(TAG, "SecurityException durante la escritura NDEF: ${e.message}")
                return "Error al escribir: El tag se movió. Acerque el tag de nuevo para completar la operación."

            } catch (e: IOException) {
                // Captura la excepción común cuando la conexión con el tag se pierde
                Log.e(TAG, "IOException durante la escritura NDEF: ${e.message}")
                return "Error al escribir: El tag se movió o la conexión falló. Acerque el tag de nuevo para completar la operación."

            } catch (e: Exception) {
                // Otras excepciones inesperadas
                Log.e(TAG, "Error inesperado durante la escritura NDEF: ${e.message}")
                return "Error inesperado durante la escritura NDEF: ${e.message}"

            } finally {
                // Asegurar que la conexión se cierre
                try {
                    if (ndef.isConnected) {
                        ndef.close()
                    }
                } catch (e: IOException) {
                    Log.e(TAG, "Error al cerrar la conexión NDEF: ${e.message}")
                }
            }
        }

        // 2. Lógica de Fallback: Si no es un tag NDEF, intentar NFCV (ISO 15693)
        // Aquí iría la lógica de fallback a NFCV para la escritura.
        // if (tag.techList.contains(NfcV::class.java.name)) {
        //     val nfcv = NfcV.get(tag)
        //     // ... Implementación de escritura por comandos V.
        //     return "Intento de escritura NFCV completado (Resultado a verificar)..."
        // }


        // Si ni NDEF ni el fallback funcionaron
        return "Error: No se encontró tecnología compatible (NDEF o NFCV) para la escritura."
    }
}

// Ejemplo de uso (simulado, necesitaría el contexto de una Activity para ser real):
// val message = NdefMessage(arrayOf(
//     NdefRecord.createTextRecord("es", "Hola Mundo")
// ))
// val writer = NfcWriter()
// val result = writer.executeNdefWriteCommand(detectedTag, message)
// showToast(result) // Muestra el mensaje de éxito o error al usuario.
