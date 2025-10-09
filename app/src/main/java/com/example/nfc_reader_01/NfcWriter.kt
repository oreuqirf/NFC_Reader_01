package com.example.nfc_reader_01

import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.NdefMessage
import android.util.Log
import java.io.IOException
import java.lang.SecurityException

/**
 * Utility class for handling write commands on NFC tags.
 *
 * NOTE: This implementation prioritizes NDEF writing. The fallback logic
 * to NFCV (ISO 15693) is indicated in the comments but would require a
 * detailed implementation of ISO 15693 commands (like NfcV.get(tag))
 * to be fully functional. The focus here is on error handling
 * during the critical NDEF connection/writing phase.
 */
class NfcWriter {

    private val TAG = "NfcWriter"

    /**
     * Attempts to write an NDEF message to the tag.
     *
     * @param tag The detected Tag object.
     * @param ndefMessage The NDEF message to write.
     * @return A string indicating the result of the operation (success or error).
     */
    fun executeNdefWriteCommand(tag: Tag, ndefMessage: NdefMessage): String {
        val ndef = Ndef.get(tag)

        // 1. Main logic: Attempt NDEF write
        if (ndef != null) {
            try {
                // Connect to the tag
                ndef.connect()

                // Check if the tag is writable and has enough space
                if (!ndef.isWritable) {
                    return "Error: The tag is not writable (NDEF)."
                }
                if (ndef.maxSize < ndefMessage.toByteArray().size) {
                    return "Error: The message is too large for this tag (NDEF)."
                }

                // Write the NDEF message
                ndef.writeNdefMessage(ndefMessage)

                // If the write is successful, it disconnects at the end of the try/finally block
                Log.d(TAG, "NDEF write successful.")
                return "NDEF write successful: Data saved correctly!"

            } catch (e: SecurityException) {
                // Catches the exception that occurs if the permission is lost (tag moved)
                Log.e(TAG, "SecurityException during NDEF write: ${e.message}")
                return "Error writing: The tag was moved. Bring the tag closer again to complete the operation."

            } catch (e: IOException) {
                // Catches the common exception when the connection with the tag is lost
                Log.e(TAG, "IOException during NDEF write: ${e.message}")
                return "Error writing: The tag was moved or the connection failed. Bring the tag closer again to complete the operation."

            } catch (e: Exception) {
                // Other unexpected exceptions
                Log.e(TAG, "Unexpected error during NDEF write: ${e.message}")
                return "Unexpected error during NDEF write: ${e.message}"

            } finally {
                // Ensure the connection is closed
                try {
                    if (ndef.isConnected) {
                        ndef.close()
                    }
                } catch (e: IOException) {
                    Log.e(TAG, "Error closing NDEF connection: ${e.message}")
                }
            }
        }

        // 2. Fallback Logic: If it's not an NDEF tag, try NFCV (ISO 15693)
        // The fallback logic to NFCV for writing would go here.
        // if (tag.techList.contains(NfcV::class.java.name)) {
        //     val nfcv = NfcV.get(tag)
        //     // ... Implementation of writing via V commands.
        //     return "Attempting NFCV write (Result to be verified)..."
        // }


        // If neither NDEF nor the fallback worked
        return "Error: No compatible technology (NDEF or NFCV) found for writing."
    }
}
