package com.example.nfc_reader_01

import android.app.PendingIntent
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import android.nfc.tech.NfcV // ISO 15693 Protocol
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.example.nfc_reader_01.databinding.ActivityMainBinding
import com.example.nfc_reader_01.utils.LogManager
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.charset.StandardCharsets
import kotlin.experimental.and

// --------------------------------------------------------------------------
// --- PROTOCOL AND NDEF CONSTANTS ---
// --------------------------------------------------------------------------
private val CMD_READ_IDENTITY: Byte = 0x01
private val CMD_READ_PROCESS: Byte = 0x02
private val CMD_READ_ENGINEERING: Byte = 0x05
private val CMD_READ_CONFIG: Byte = 0x03 // Command to read configuration
private val CMD_WRITE_CONFIG: Byte = 0x04 // Command to write configuration (requires 96 bytes of payload)
private val CMD_FACTORY_RESET: Byte = 0x0A
private val CMD_GET_SYSTEM_INFO: Byte = 0x2B.toByte()

// MIME Type to send the command to the TAG (First Scan)
private const val MIME_COMMAND_TYPE = "application/x-cmd"
// MIME Type to wait for the data response from the TAG (Second Scan)
private const val MIME_RESPONSE_TYPE = "application/x-data"

/**
 * Main activity that handles NFC initialization and dual-scan NDEF communication.
 */
class MainActivity : AppCompatActivity(), NfcInteractionListener {

    private val TAG = "NFC_MainActivity"
    private lateinit var binding: ActivityMainBinding
    private var nfcAdapter: NfcAdapter? = null
    private lateinit var pendingIntent: PendingIntent
    private val sharedViewModel: SharedNfcViewModel by viewModels {
        ViewModelProvider.AndroidViewModelFactory(application)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupNavigation()
        setupNfc()
        setupViewModelObservers()
        handleIntent(intent)
    }

    private fun setupNavigation() {
        val navView: BottomNavigationView = binding.navView
        val navController = findNavController(R.id.nav_host_fragment_activity_main)
        val appBarConfiguration = AppBarConfiguration(
            setOf(R.id.navigation_home, R.id.navigation_dashboard, R.id.navigation_configuration, R.id.navigation_notifications)
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)
    }

    // --------------------------------------------------------------------------
    // --- NFCINTERACTIONLISTENER IMPLEMENTATION ---
    // --------------------------------------------------------------------------

    override fun navigateToDashboard() {
        findNavController(R.id.nav_host_fragment_activity_main).navigate(R.id.navigation_dashboard)
    }

    /** Requests the execution of a command (First Scan) */
    override fun requestNextCommand(commandId: Byte) {
        // For read/reset commands, the payload is just the ID.
        sharedViewModel.setConfigDataToWrite(null) // We clear any pending write data
        sharedViewModel.sendCommand(commandId)
        sharedViewModel.setUiMessage("First Scan: TAG Ready. Waiting for NDEF to write command (0x${commandId.toHexString()}).")
    }

    override fun requestWriteConfig() {
        // The fragment should have already placed the 96 bytes in configDataToWrite.
        sharedViewModel.sendCommand(CMD_WRITE_CONFIG) // We set 0x04
        sharedViewModel.setUiMessage("First Scan: TAG Ready. Waiting for NDEF to write command (0x${CMD_WRITE_CONFIG.toHexString()} + 96 bytes).")
    }

    // --------------------------------------------------------------------------
    // --- NFC LOGIC AND LIFECYCLE ---
    // --------------------------------------------------------------------------

    private fun setupNfc() {
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        if (nfcAdapter == null) {
            lifecycleScope.launch { LogManager.log("NFC: Device not compatible with NFC.") }
        }

        val intent = Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)

        pendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        } else {
            @Suppress("DEPRECATION")
            PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
        }
    }

    private fun setupViewModelObservers() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // 1. Observer for the Pending Command (updates UI Message)
                launch {
                    sharedViewModel.pendingCommand.collect { command ->
                        if (command != null) {
                            sharedViewModel.setUiMessage("First Scan: Command 0x${command.toHexString()} waiting for NDEF write...")
                        } else if (sharedViewModel.nfcTagInfo.value == null) {
                            sharedViewModel.setUiMessage("TAG Ready: Waiting for first NDEF scan (to write command)...")
                        }
                    }
                }

                // 2. Observer for Toast/SnackBar Messages (writeStatus SharedFlow)
                // This observer is the one that converts the ViewModel emissions into Toasts.
                launch {
                    sharedViewModel.writeStatus.collect { status ->
                        Toast.makeText(this@MainActivity, status, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    /**
     * Enables foreground dispatch to prioritize the detection of NDEF and other technologies.
     */
    override fun onResume() {
        super.onResume()

        val intentFilters = arrayOf(
            IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED),
            IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED),
            IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED)
        )

        // We include all key technologies, including NfcV (confirmed by the user)
        val techList = arrayOf(
            arrayOf(Ndef::class.java.name),
            arrayOf(NdefFormatable::class.java.name),
            arrayOf(NfcV::class.java.name)
        )

        nfcAdapter?.enableForegroundDispatch(this, pendingIntent, intentFilters, techList)
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.disableForegroundDispatch(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /**
     * Displays in the log all the technologies detected in the TAG.
     */
    private fun logTagTechnologies(tag: Tag) {
        val techList = tag.techList.joinToString(", ")
        lifecycleScope.launch {
            LogManager.log("TAG DIAGNOSTIC: ID=${tag.id.toHexString()}, Techs Detected=[$techList]")
        }
    }

    /**
     * Processes the TAG discovery Intent.
     */
    private fun handleIntent(intent: Intent) {
        val tag: Tag? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
        }

        if (tag == null) {
            sharedViewModel.setNfcTagInfo(null)
            return
        }

        sharedViewModel.setNfcTagInfo(tag.id.toHexString())
        logTagTechnologies(tag)

        lifecycleScope.launch {
            processNdefTransaction(tag)
        }
    }

    /**
     * Handles the NDEF transaction, prioritizing NDEF/NdefFormatable, and using NfcV as a forced format fallback.
     * **Contains the global try/catch block to avoid unexpected application closures.**
     */
    private suspend fun processNdefTransaction(tag: Tag) = withContext(Dispatchers.IO) {
        // Global try/catch block to catch any exception that propagates,
        // ensuring that the coroutine does not fail and does not close the application.
        try {
            val commandId = sharedViewModel.pendingCommand.value
            val ndef = Ndef.get(tag)
            val ndefFormatable = NdefFormatable.get(tag)
            val nfcV = NfcV.get(tag) // We get the NfcV object for the fallback

            // Diagnostic logging
            lifecycleScope.launch {
                LogManager.log("DBG NDEF Check: Ndef.get(tag) = ${if(ndef != null) "OK" else "NULL"}")
                LogManager.log("DBG NDEF Check: NdefFormatable.get(tag) = ${if(ndefFormatable != null) "OK" else "NULL"}")
                LogManager.log("DBG Fallback Check: NfcV.get(tag) = ${if(nfcV != null) "OK" else "NULL"}")
            }

            // --- CASE 1: PENDING COMMAND (First Scan) ---
            if (commandId != null) {
                var success = false

                // 1. Try standard NDEF write (TAG is already a valid and writable NDEF)
                if (ndef != null) {
                    success = executeNdefWriteCommand(ndef, commandId)
                }

                // 2. Try formatting and NDEF write (TAG is formattable, but the CC could be corrupt)
                if (!success && ndefFormatable != null) {
                    // If this fails (returns false), we move on to the advanced fallback (step 3)
                    success = executeNdefFormatAndWriteCommand(ndefFormatable, commandId)
                }

                // 3. Advanced Fallback: If NDEF/NdefFormatable failed, but NfcV is available, force CC.
                if (!success && nfcV != null) {
                    lifecycleScope.launch { LogManager.log("Activating Advanced Fallback: Low-level CC formatting by NfcV.") }

                    val formatSuccess = executeNfcVForceFormatOnlyFallback(nfcV)

                    if (formatSuccess) {
                        sharedViewModel.setUiMessage("First FORCED Scan (NfcV) OK: TAG formatted to NDEF. Bring the TAG **immediately** to write the command 0x${commandId.toHexString()}!")
                        // SUCCESSFUL FALLBACK TOAST EMISSION
                        sharedViewModel.emitWriteStatus("FALLBACK NfcV OK: TAG formatted. Scan again to write.")
                    } else {
                        val errorMsg = "ERROR: Failed to force write/format command 0x${commandId.toHexString()}. TAG without NfcV support or locked."
                        sharedViewModel.setUiMessage(errorMsg)
                        // FAILED FALLBACK TOAST EMISSION
                        sharedViewModel.emitWriteStatus("ERROR NfcV: Failed to force formatting. Locked or unsupported.")
                    }
                    // Exit after the forced formatting attempt (a new scan is required for the NDEF write)
                    return@withContext
                }

                // If the NDEF write (step 1 or 2) was successful, the command has already been cleared within the write method
                // to avoid double scanning.
                if (!success) {
                    sharedViewModel.setUiMessage("ERROR: Failed to process command. TAG not NDEF and without support for forced format.")
                }
                return@withContext
            }

            // --- CASE 2: NO PENDING COMMAND (Second Scan) ---
            if (ndef != null) {
                try {
                    ndef.connect()
                    executeNdefReadResponse(ndef)
                } catch (e: IOException) {
                    // This try-catch already handles the 'I/O ERROR' specific to NDEF reading.
                    val errorMsg = "NDEF read error (I/O): ${e.message}"
                    lifecycleScope.launch { LogManager.log(errorMsg) }
                    sharedViewModel.setUiMessage(errorMsg)
                    // sharedViewModel.emitWriteStatus("ERROR NDEF I/O: Failed to read response.") // Toast for I/O
                } finally {
                    if (ndef.isConnected) {
                        try { ndef.close() } catch (_: IOException) {}
                    }
                }
            } else {
                // The TAG is not a valid NDEF and there is no pending command.
                sharedViewModel.setUiMessage("Warning: Invalid/unformatted TAG. Ready to receive command.")
            }

        } catch (e: Exception) {
            // ** GLOBAL CAPTURE TO AVOID APP CLOSURE **
            val errorMsg = "CRITICAL Uncaught Coroutine/IO Error: ${e.message}"
            lifecycleScope.launch { LogManager.log("CRASH_PREVENTION_CATCH: $errorMsg") }
            sharedViewModel.setUiMessage("CRITICAL ERROR: General coroutine failure. Check the Log.")
            // WE EMIT A GENERIC UNEXPECTED ERROR TOAST TO THE USER
            sharedViewModel.emitWriteStatus("CRITICAL ERROR: Uncaught general failure.")
        }
    }

    /**
     * **Helper:** Creates the full NDEF command payload, including the 96 bytes of
     * configuration if the command is CMD_WRITE_CONFIG (0x04).
     *
     * @return ByteArray? The full command payload, or null if configuration data is missing.
     */
    private fun createNdefCommandPayload(commandId: Byte): ByteArray? {
        return if (commandId == CMD_WRITE_CONFIG) {
            val configDataBytes = sharedViewModel.configDataToWrite.value
            if (configDataBytes == null || configDataBytes.size != 96) {
                // Generate error message and Toast
                sharedViewModel.setUiMessage("ERROR: Command 0x${CMD_WRITE_CONFIG.toHexString()} requested, but the 96-byte data is not ready in the ViewModel.")
                sharedViewModel.emitWriteStatus("ERROR: Configuration data (96B) not available for writing.")
                null
            } else {
                // CRITICAL: Concatenate the command byte (0x04) + the 96 bytes of data. (97 bytes total)
                byteArrayOf(commandId) + configDataBytes
            }
        } else {
            // Read/reset commands (1-byte payload)
            byteArrayOf(commandId)
        }
    }


    /**
     * **First Scan (NfcV Fallback):** Tries to force NDEF formatting
     * by writing only the Capability Container (CC) in Block 0.
     *
     * If successful, the next scan will allow Android to detect Ndef or NdefFormatable.
     * The command *is not* written in this step.
     */
    private fun executeNfcVForceFormatOnlyFallback(nfcV: NfcV): Boolean {
        lifecycleScope.launch { LogManager.log("NfcV: Writing only the Capability Container (CC) in Block 0 to force NDEF format.") }

        // --- ISO 15693 operation parameters ---
        val flags: Byte = 0x02 // Flags: Simple addressing (no UID), Data Rate High
        val cmdWriteSingleBlock: Byte = 0x21.toByte()
        val NDEF_MAX_SIZE: Byte = 0x40 // NDEF size of 64 bytes (0x40)

        // NDEF Capability Container (CC) block (4 bytes)
        // [E1] (Magic Byte), [40] (Version 1.0, Read/Write), [00] (Max Size High), [40] (Max Size Low: 64 bytes)
        val ccBlock = byteArrayOf(
            0xE1.toByte(),
            0x40.toByte(),
            0x00,
            NDEF_MAX_SIZE
        )

        try {
            if (!nfcV.isConnected) nfcV.connect()

            // Command: [Flags, CMD_WRITE_SINGLE_BLOCK, Block Address (0x00), Data (CC Block)]
            val writeCCCommand = byteArrayOf(flags, cmdWriteSingleBlock, 0x00) + ccBlock
            val response = nfcV.transceive(writeCCCommand)

            // ISO 15693 response validation (empty or status byte 0x00 indicating success)
            if (response.isEmpty() || (response.size == 1 && response[0].and(0x01) == 0.toByte())) {
                return true
            } else {
                lifecycleScope.launch { LogManager.log("ERROR NfcV CC: The chip returned an error on CC write. Code: ${response.toHexString()}") }
                return false
            }

        } catch (e: IOException) {
            val errorMsg = "NfcV Error (ISO 15693) when forcing CC formatting: ${e.message}. The chip could be completely locked."
            lifecycleScope.launch { LogManager.log(errorMsg) }
            sharedViewModel.setUiMessage(errorMsg)
            // NfcV ERROR TOAST EMISSION
            sharedViewModel.emitWriteStatus("NfcV ERROR: I/O failure. The chip could be completely locked.")
            return false
        } finally {
            if (nfcV.isConnected) {
                try { nfcV.close() } catch (_: IOException) {}
            }
        }
    }


    /**
     * **First Scan (High-Level Forced):** Formats the TAG and writes the command's NDEF message.
     * (Only used if NdefFormatable is available)
     */
    private fun executeNdefFormatAndWriteCommand(ndefFormatable: NdefFormatable, commandId: Byte): Boolean {

        // --- NDEF PAYLOAD PREPARATION (Using the helper function) ---
        val fullPayload = createNdefCommandPayload(commandId)
        if (fullPayload == null) {
            // The helper has already emitted the Toast/UI Message of missing data error.
            return false
        }

        val logMessage = if (commandId == CMD_WRITE_CONFIG) {
            "Writing command 0x${commandId.toHexString()} with 96 bytes of configuration..."
        } else {
            "Writing command 0x${commandId.toHexString()} (1 byte)..."
        }

        lifecycleScope.launch { LogManager.log("First FORCED Scan (High-Level): TAG will be formatted and written. $logMessage") }

        val commandRecord = NdefRecord.createMime(MIME_COMMAND_TYPE, fullPayload)
        val message = NdefMessage(commandRecord)

        try {
            ndefFormatable.connect()
            ndefFormatable.format(message)

            // Write success
            sharedViewModel.setUiMessage("First FORCED HIGH-LEVEL Scan OK: Formatted and $logMessage sent. Bring the TAG close again for the Second Scan!")
            // SUCCESSFUL FORCED WRITE TOAST EMISSION
            sharedViewModel.emitWriteStatus("Forced Write OK: TAG formatted and command sent.")

            // Clear the command immediately to avoid double scanning/Toast
            sharedViewModel.clearCommand()

            // Clear write data if the operation was successful
            if (commandId == CMD_WRITE_CONFIG) {
                sharedViewModel.setConfigDataToWrite(null)
            }
            return true
        } catch (e: IOException) {
            val errorMsg = "NDEF error when formatting/writing: ${e.message}. Resorting to NfcV."
            lifecycleScope.launch { LogManager.log(errorMsg) }
            // ERROR TOAST EMISSION
            sharedViewModel.emitWriteStatus("NDEF Format ERROR: I/O failure. Resorting to NfcV.")
            return false
        } finally {
            if (ndefFormatable.isConnected) {
                try { ndefFormatable.close() } catch (_: IOException) {}
            }
        }
    }


    /**
     * **First Scan (Standard):** Writes the command as an NDEF message (application/x-cmd).
     * Now handles 1-byte commands and the WRITE command (0x04) with a 96-byte payload.
     * (Only used if Ndef is available)
     */
    private fun executeNdefWriteCommand(ndef: Ndef, commandId: Byte): Boolean {
        // --- NDEF PAYLOAD PREPARATION (Using the helper function) ---
        val fullPayload = createNdefCommandPayload(commandId)
        if (fullPayload == null) {
            // The helper has already emitted the Toast/UI Message of missing data error.
            return false
        }

        val logMessage = if (commandId == CMD_WRITE_CONFIG) {
            "Writing command 0x${commandId.toHexString()} with 96 bytes of configuration..."
        } else {
            "Writing command 0x${commandId.toHexString()} (1 byte)..."
        }

        val commandRecord = NdefRecord.createMime(MIME_COMMAND_TYPE, fullPayload)
        val message = NdefMessage(commandRecord)

        lifecycleScope.launch {
            LogManager.log("First Scan: $logMessage")
        }

        // --- WRITE LOGIC WITH ROBUST ERROR HANDLING ---
        try {
            // 2. Try to connect
            if (!ndef.isConnected) ndef.connect()

            // 3. Perform pre-write checks
            if (!ndef.isWritable) {
                val errorMsg = "ERROR: NDEF TAG is not writable. Check the TAG's lock."
                lifecycleScope.launch { LogManager.log(errorMsg) }
                sharedViewModel.setUiMessage(errorMsg)
                // LOCK ERROR TOAST EMISSION
                sharedViewModel.emitWriteStatus("ERROR: The TAG is locked. The command cannot be written.")
                return false
            }
            // CRITICAL: Size check for the 97-byte message
            if (ndef.maxSize < message.toByteArray().size) {
                val errorMsg = "ERROR: Command message (${message.toByteArray().size} B) is too large for the TAG (Max ${ndef.maxSize} B)."
                lifecycleScope.launch { LogManager.log(errorMsg) }
                sharedViewModel.setUiMessage(errorMsg)
                // SIZE ERROR TOAST EMISSION
                sharedViewModel.emitWriteStatus("ERROR: The message is too large for the TAG.")
                return false
            }

            // 4. Perform the write operation
            ndef.writeNdefMessage(message)

            // 5. Success
            sharedViewModel.setUiMessage("First Scan OK: $logMessage sent. Bring the TAG close again for the Second Scan (Read Response)!")
            // STANDARD SUCCESS TOAST EMISSION
            sharedViewModel.emitWriteStatus("NDEF Write OK. Command 0x${commandId.toHexString()} sent. Ready for Scan 2.")

            // Clear the command immediately to avoid double scanning/Toast
            sharedViewModel.clearCommand()

            // If the write is successful, clear the write data.
            if (commandId == CMD_WRITE_CONFIG) {
                sharedViewModel.setConfigDataToWrite(null)
            }
            return true

            // --- CATCH BLOCK FOR CONNECTION OR SECURITY PROBLEMS ---
        } catch (e: SecurityException) {
            // Catch: java.lang.SecurityException: Tag is out of date.
            val errorMsg = "Security Error (TAG Lost): The TAG has moved or disconnected. Bring the TAG close again to retry."
            lifecycleScope.launch { LogManager.log("NFC_WRITE SecurityException (Stale Tag): ${e.message}") }
            sharedViewModel.setUiMessage(errorMsg)
            // CONNECTION ERROR TOAST EMISSION
            sharedViewModel.emitWriteStatus("NDEF I/O ERROR: Connection lost. Retry.")
            return false

        } catch (e: IOException) {
            // Catch: Thrown by connect(), writeNdefMessage(), or any other technology call
            val errorMsg = "Connection Error (I/O): The TAG moved or communication failed during the operation. Bring the TAG close again to retry."
            lifecycleScope.launch { LogManager.log("NFC_WRITE IOException (Connection Lost): ${e.message}") }
            sharedViewModel.setUiMessage(errorMsg)
            // CONNECTION ERROR TOAST EMISSION
            sharedViewModel.emitWriteStatus("NDEF I/O ERROR: Connection lost. Retry.")
            return false

        } catch (e: Exception) {
            // Catch any other unexpected exception
            val errorMsg = "Unexpected Error: An unexpected error occurred during NDEF write: ${e.message}"
            lifecycleScope.launch { LogManager.log("NFC_WRITE Unexpected error: ${e.message}") }
            sharedViewModel.setUiMessage(errorMsg)
            // UNEXPECTED ERROR TOAST EMISSION
            sharedViewModel.emitWriteStatus("Unexpected Error: NDEF write failure.")
            return false

        } finally {
            // 6. Always close the connection
            try {
                if (ndef.isConnected) {
                    ndef.close()
                }
            } catch (closeE: Exception) {
                // Ignore errors during closing, as the main operation has already finished or failed.
                lifecycleScope.launch { LogManager.log("Warning: Error closing Ndef connection: ${closeE.message}") }
            }
        }
    }

    /**
     * **Second Scan:** Reads the NDEF message and looks for the response (application/x-data).
     */
    private fun executeNdefReadResponse(ndef: Ndef) {
        lifecycleScope.launch { LogManager.log("Second Scan: Trying to read NDEF response (application/x-data)...") }

        try {
            val ndefMessage = ndef.getNdefMessage()
            if (ndefMessage == null) {
                sharedViewModel.setUiMessage("Second Scan: Empty or unformatted NDEF. Did the TAG process the command?")
                return
            }

            var responseFound = false
            for (record in ndefMessage.records) {
                if (record.tnf == NdefRecord.TNF_MIME_MEDIA) {
                    val recordType = record.type.toString(StandardCharsets.US_ASCII)

                    if (recordType == MIME_RESPONSE_TYPE) {
                        val payload = record.payload
                        if (payload.isNotEmpty()) {
                            val originalCommandId = payload[0]
                            val data = payload.copyOfRange(1, payload.size)

                            lifecycleScope.launch {
                                LogManager.log("Second Scan OK: Response from 0x${originalCommandId.toHexString()} (Payload size: ${data.size} bytes)")
                            }

                            // The ViewModel will distribute and emit the success Toast (0x81, 0x84, etc.)
                            sharedViewModel.distributeResponseData(originalCommandId, data)
                            sharedViewModel.setUiMessage("Second Scan OK: Data from 0x${originalCommandId.toHexString()} received. Ready for new command.")
                            responseFound = true
                            break
                        }
                    } else if (recordType == MIME_COMMAND_TYPE) {
                        sharedViewModel.setUiMessage("Second Scan: Synchronization error. The TAG still contains the COMMAND (0x${record.payload[0].toHexString()}) and not the RESPONSE. Rescan in a few seconds.")
                        // SYNCHRONIZATION ERROR TOAST EMISSION
                        sharedViewModel.emitWriteStatus("SYNC ERROR: TAG still has the command. Scan again in 2s.")
                        responseFound = true
                        break
                    }
                }
            }

            if (!responseFound) {
                sharedViewModel.setUiMessage("Second Scan: NDEF message found, but the RESPONSE record ($MIME_RESPONSE_TYPE) was not found.")
                // RESPONSE ERROR TOAST EMISSION
                sharedViewModel.emitWriteStatus("NDEF ERROR: Response Record not found.")
            }

        } catch (e: Exception) {
            val errorMsg = "NDEF error when reading (Logic): ${e.message}"
            lifecycleScope.launch { LogManager.log(errorMsg) }
            sharedViewModel.setUiMessage(errorMsg)
            // UNEXPECTED ERROR TOAST EMISSION
            sharedViewModel.emitWriteStatus("UNEXPECTED ERROR when reading NDEF response.")
        }
    }
}

// --------------------------------------------------------------------------
// Utility functions
// --------------------------------------------------------------------------

/** Utility function to convert ByteArray to Hexadecimal String */
fun ByteArray.toHexString() = joinToString(separator = " ") {
    String.format("%02X", it)
}

/** Utility function to convert a Byte to a two-digit Hexadecimal String. */
fun Byte.toHexString() = String.format("%02X", this)
