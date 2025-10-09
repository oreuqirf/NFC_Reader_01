package com.example.nfc_reader_01.ui.dashboard

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.nfc_reader_01.SharedNfcViewModel
import com.example.nfc_reader_01.data.NfcDataParser
import com.example.nfc_reader_01.databinding.FragmentDashboardBinding
import com.example.nfc_reader_01.ui.configuration.ConfigurationFragment.Companion.CONFIG_BYTE_SIZE
import kotlinx.coroutines.launch
import kotlin.ExperimentalStdlibApi

/**
 * Fragment to display the data read from the NFC tag (response 0x8X) in structured fields.
 */
@OptIn(ExperimentalStdlibApi::class) // <-- ANNOTATION APPLIED TO USE Float.fromBits()
class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null

    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!

    // Injects the activity-level shared ViewModel
    private val sharedViewModel: SharedNfcViewModel by activityViewModels()

    // User-defined command codes
    private val CMD_READ_IDENTITY: Byte = 0x01
    private val CMD_READ_PROCESS: Byte = 0x02
    private val CMD_READ_CONFIG: Byte = 0x03
    private val CMD_READ_ENGINEERING: Byte = 0x05

    // --- EXPECTED USEFUL PAYLOAD SIZES (assuming the rest are padding bytes) ---
    // The TAG sends 127 bytes, but only this portion contains useful data.
    private val IDENTITY_PAYLOAD_SIZE = 12 // 12 bytes for ID, FW Version, and Timestamp
    private val PROCESS_PAYLOAD_SIZE = 36 // 36 bytes for 8 fields of 4 bytes (Float/Int)
    private val ENGINEERING_PAYLOAD_SIZE = 44 // 44 bytes for 11 fields of 4 bytes (Float/Int)
    // --------------------------------------------------------------------------------------------------

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupListeners()
        setupObservers()
    }

    /**
     * Sets up the listeners for the data request buttons.
     */
    private fun setupListeners() {
        binding.requestIdentityButton.setOnClickListener {
            sharedViewModel.sendCommand(CMD_READ_IDENTITY)
            Log.d(TAG, "Requesting Identity (0x${CMD_READ_IDENTITY.toHexString()})")
            sharedViewModel.setUiMessage("Command 0x${CMD_READ_IDENTITY.toHexString()} (Identity) prepared. Bring the TAG closer.")
        }

        binding.requestProcessButton.setOnClickListener {
            sharedViewModel.sendCommand(CMD_READ_PROCESS)
            Log.d(TAG, "Requesting Process (0x${CMD_READ_PROCESS.toHexString()})")
            sharedViewModel.setUiMessage("Command 0x${CMD_READ_PROCESS.toHexString()} (Process) prepared. Bring the TAG closer.")
        }

        binding.requestConfigButton.setOnClickListener {
            sharedViewModel.sendCommand(CMD_READ_CONFIG)
            Log.d(TAG, "Requesting Configuration (0x${CMD_READ_CONFIG.toHexString()})")
            sharedViewModel.setUiMessage("Command 0x${CMD_READ_CONFIG.toHexString()} (Configuration) prepared. Bring the TAG closer.")
        }

        binding.requestEngineeringButton.setOnClickListener {
            sharedViewModel.sendCommand(CMD_READ_ENGINEERING)
            Log.d(TAG, "Requesting Engineering (0x${CMD_READ_ENGINEERING.toHexString()})")
            sharedViewModel.setUiMessage("Command 0x${CMD_READ_ENGINEERING.toHexString()} (Engineering) prepared. Bring the TAG closer.")
        }
    }

    /**
     * Sets up the observers for the ViewModel's data flows, calling
     * the appropriate parsing function for each response.
     */
    private fun setupObservers() {
        // Observes the general message from the ViewModel for feedback
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                sharedViewModel.uiMessage.collect { message ->
                    try {
                        // The message is displayed in the Dashboard UI (not in a Toast)
                        binding.editTextMessage.setText(message)
                    } catch (e: Exception) {
                        Log.d(TAG, "NFC Status: $message")
                    }
                }
            }
        }

        // --- OBSERVER 0x81: Identity (identityResponseData) ---
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                sharedViewModel.identityResponseData.collect { data ->
                    if (data != null && data.isNotEmpty()) {
                        Log.i(TAG, "0x81 Data (Identity) received: ${data.size} bytes. Hex: ${data.toHexString()}")

                        if (data.size >= IDENTITY_PAYLOAD_SIZE) {
                            // Extract only the useful bytes, ignoring the padding at the end
                            val usefulBytes = data.sliceArray(0 until IDENTITY_PAYLOAD_SIZE)
                            Log.d(TAG, "Processing ${usefulBytes.size} useful bytes for Identity.")
                            parseIdentityData(usefulBytes)
                            sharedViewModel.setUiMessage("Response 0x81: Identity Data loaded successfully.")
                        } else {
                            Log.e(TAG, "ERROR: Incomplete Identity Data. Expected: $IDENTITY_PAYLOAD_SIZE, Received: ${data.size}.")
                            sharedViewModel.setUiMessage("ERROR 0x81: Incomplete response. (${data.size}/${IDENTITY_PAYLOAD_SIZE} bytes).")
                        }
                    }
                }
            }
        }

        // --- OBSERVER 0x82: Process (processResponseData) ---
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                sharedViewModel.processResponseData.collect { data ->
                    if (data != null && data.isNotEmpty()) {
                        Log.i(TAG, "0x82 Data (Process) received: ${data.size} bytes. Hex: ${data.toHexString()}")

                        if (data.size >= PROCESS_PAYLOAD_SIZE) {
                            val usefulBytes = data.sliceArray(0 until PROCESS_PAYLOAD_SIZE)
                            Log.d(TAG, "Processing ${usefulBytes.size} useful bytes for Process.")
                            parseProcessData(usefulBytes)
                            sharedViewModel.setUiMessage("Response 0x82: Process Data updated.")
                        } else {
                            Log.e(TAG, "ERROR: Incomplete Process Data. Expected: $PROCESS_PAYLOAD_SIZE, Received: ${data.size}.")
                            sharedViewModel.setUiMessage("ERROR 0x82: Incomplete response. (${data.size}/${PROCESS_PAYLOAD_SIZE} bytes).")
                        }
                    }
                }
            }
        }

        // --- OBSERVER 0x83: Configuration (configurationResponseData) ---
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                sharedViewModel.configurationResponseData.collect { data ->
                    if (data != null && data.isNotEmpty()) {
                        Log.i(TAG, "0x83 Data (Configuration) received: ${data.size} bytes. Hex: ${data.toHexString()}")

                        if (data.size >= CONFIG_BYTE_SIZE) {
                            val usefulBytes = data.sliceArray(0 until CONFIG_BYTE_SIZE)
                            Log.d(TAG, "Processing ${usefulBytes.size} useful bytes for Configuration.")
                            parseConfigData(usefulBytes)
                            sharedViewModel.setUiMessage("Response 0x83: Configuration Data loaded successfully.")
                            sharedViewModel.clearConfigurationResponseData()
                        } else {
                            Log.e(TAG, "ERROR: Incomplete Configuration Data. Expected: $CONFIG_BYTE_SIZE, Received: ${data.size}.")
                            sharedViewModel.setUiMessage("ERROR 0x83: Incomplete response. (${data.size}/${CONFIG_BYTE_SIZE} bytes).")
                            sharedViewModel.clearConfigurationResponseData()
                        }
                    }
                }
            }
        }

        // --- OBSERVER 0x85: Engineering (engineeringResponseData) ---
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                sharedViewModel.engineeringResponseData.collect { data ->
                    if (data != null && data.isNotEmpty()) {
                        Log.i(TAG, "0x85 Data (Engineering) received: ${data.size} bytes. Hex: ${data.toHexString()}")

                        if (data.size >= ENGINEERING_PAYLOAD_SIZE) {
                            val usefulBytes = data.sliceArray(0 until ENGINEERING_PAYLOAD_SIZE)
                            Log.d(TAG, "Processing ${usefulBytes.size} useful bytes for Engineering.")
                            parseEngineeringData(usefulBytes)
                            sharedViewModel.setUiMessage("Response 0x85: Engineering Data loaded.")
                        } else {
                            Log.e(TAG, "ERROR: Incomplete Engineering Data. Expected: $ENGINEERING_PAYLOAD_SIZE, Received: ${data.size}.")
                            sharedViewModel.setUiMessage("ERROR 0x85: Incomplete response. (${data.size}/${ENGINEERING_PAYLOAD_SIZE} bytes).")
                        }
                    }
                }
            }
        }

        // --- CORRECTED OBSERVER: SharedFlow of Write Status (writeStatus) for Toast ---
        // This observer is activated ONLY when the ViewModel emits a new event.
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                sharedViewModel.writeStatus.collect { status ->
                    Log.i(TAG, "Write Status (0x84 or similar) received: $status")
                    sharedViewModel.setUiMessage(status)
                }
            }
        }
    }

    /**
     * Parses ONLY the IDENTITY data (Response 0x81).
     * Calls the centralized parsing function in the NfcDataParser object.
     * @param data The data to parse.
     */
    private fun parseIdentityData(data: ByteArray) {
        try {
            val identity = NfcDataParser.parseIdentityData(data)
            binding.editTextSerialNumber.setText(identity.deviceId.toString())
            binding.editTextFirmwareVersion.setText(identity.firmwareVersion)
            binding.editTextLastConfigDate.setText(identity.lastConfigurationDate)
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Payload size error (0x81): ${e.message}")
            binding.editTextSerialNumber.setText("ERROR: Size: ${data.size} bytes. Parsing failed.")
        } catch (e: Exception) {
            Log.e(TAG, "CRITICAL error parsing Identity data: ${e.message}", e)
            binding.editTextSerialNumber.setText("ERROR: Parsing 0x81 failed.")
        }

        clearProcessValueFields()
        clearFlowPeriodFields()
        clearEngineeringFields()
    }


    /**
     * Parses ONLY the PROCESS data (Response 0x82).
     * Note: Converts the Ints (raw 4-byte fields) to Float for display.
     * @param data The data to parse.
     */
    private fun parseProcessData(data: ByteArray) {
        try {
            val process = NfcDataParser.parseProcessData(data)

            val floatVolume = Float.fromBits(process.volume)
            val floatFlow = Float.fromBits(process.flowRate)
            val floatTemp = Float.fromBits(process.temperature)

            binding.editTextVolume.setText("${floatVolume.format(3)} m³")
            binding.editTextFlow.setText("${floatFlow.format(2)} L/h")
            binding.editTextTemperature.setText("${floatTemp.format(1)} °C")

            binding.editTextBattery.setText("${process.battery}%")
            binding.editTextStatus.setText("0x${process.statusFlags.toHexString()}")

            binding.editTextDirectFlowPeriod.setText("${process.directFlowPeriod} sec")
            binding.editTextReverseFlowPeriod.setText("${process.reverseFlowPeriod} sec")
            binding.editTextNoFlowPeriod.setText("${process.noFlowPeriod} sec")
            binding.editTextLeakagePeriod.setText("${process.leakageFlowPeriod} sec")

        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Payload size error (0x82): ${e.message}")
            binding.editTextVolume.setText("ERROR: Size: ${data.size} bytes. Parsing failed.")
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing Process data: ${e.message}", e)
            binding.editTextVolume.setText("ERROR: Parsing 0x82 failed.")
        }

        clearIdentityFields()
        clearEngineeringFields()
    }

    /**
     * Parses ONLY the CONFIGURATION data (Response 0x83).
     * @param data The data to parse.
     */
    private fun parseConfigData(data: ByteArray) {
        try {
            NfcDataParser.parseConfigData(data)
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Payload size error (0x83): ${e.message}")
            binding.editTextDirectFlowPeriod.setText("ERROR: Size: ${data.size} bytes. Parsing failed.")
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing Configuration data: ${e.message}", e)
            binding.editTextDirectFlowPeriod.setText("ERROR: Parsing 0x83 failed.")
        }

        clearIdentityFields(keepLastConfigDate = false)
        clearProcessValueFields()
        clearFlowPeriodFields()
        clearEngineeringFields()
    }


    /**
     * Parses ONLY the ENGINEERING data (Response 0x85).
     * Note: Converts the Ints (raw 4-byte fields) to Float for display.
     * @param data The data to parse.
     */
    private fun parseEngineeringData(data: ByteArray) {
        try {
            val engineering = NfcDataParser.parseEngineeringData(data)

            val floatVolL = Float.fromBits(engineering.volumeLiters)
            val floatVolLU = Float.fromBits(engineering.volumeLitersUncal)
            val floatTempU = Float.fromBits(engineering.temperatureUncal)
            val floatFlowU = Float.fromBits(engineering.flowUncal)
            val floatTtof = Float.fromBits(engineering.ttof)
            val floatDtof = Float.fromBits(engineering.dtof)
            val floatStdDev = Float.fromBits(engineering.stdDev)
            val floatChipTemp = Float.fromBits(engineering.chipTemperature)
            val floatLux = Float.fromBits(engineering.lux)

            binding.editTextVolumeLiters.setText("${floatVolL.format(2)} L")
            binding.editTextVolumeLitersUncal.setText("${floatVolLU.format(2)} L")
            binding.editTextTemperatureUncal.setText("${floatTempU.format(1)} °C")
            binding.editTextFlowUncal.setText("${floatFlowU.format(2)} L/h")
            binding.editTextTtof.setText(floatTtof.format(4))
            binding.editTextDtof.setText(floatDtof.format(4))
            binding.editTextStdDev.setText(floatStdDev.format(4))
            binding.editTextTime.setText("${engineering.time} sec")
            binding.editTextChipTemperature.setText("${floatChipTemp.format(1)} °C")
            binding.editTextLux.setText("${floatLux.format(0)} mV")
            binding.editTextRakFrameCounter.setText("${engineering.rakFrameCounter}")

        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Payload size error (0x85): ${e.message}")
            binding.editTextVolumeLiters.setText("ERROR: Size: ${data.size} bytes. Parsing failed.")
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing Engineering data: ${e.message}", e)
            binding.editTextVolumeLiters.setText("ERROR: Parsing 0x85 failed.")
        }

        clearIdentityFields()
        clearProcessValueFields()
        clearFlowPeriodFields()
    }

    // --- Helper functions to clear fields (Refactored) ---

    /** Clears the Identity fields (ID, FW, and optionally Date) */
    private fun clearIdentityFields(keepLastConfigDate: Boolean = false) {
        binding.editTextSerialNumber.setText("")
        binding.editTextFirmwareVersion.setText("")
        if (!keepLastConfigDate) {
            binding.editTextLastConfigDate.setText("")
        }
    }

    /** Clears the Process value fields (Volume, Flow, Temp, Battery, Status) */
    private fun clearProcessValueFields() {
        binding.editTextVolume.setText("")
        binding.editTextFlow.setText("")
        binding.editTextTemperature.setText("")
        binding.editTextBattery.setText("")
        binding.editTextStatus.setText("")
    }

    /** Clears the Flow Period fields (Shared/Relevant for Process and Config) */
    private fun clearFlowPeriodFields() {
        binding.editTextDirectFlowPeriod.setText("")
        binding.editTextReverseFlowPeriod.setText("")
        binding.editTextNoFlowPeriod.setText("")
        binding.editTextLeakagePeriod.setText("")
    }

    /** Clears the Engineering fields */
    private fun clearEngineeringFields() {
        binding.editTextVolumeLiters.setText("")
        binding.editTextVolumeLitersUncal.setText("")
        binding.editTextTemperatureUncal.setText("")
        binding.editTextFlowUncal.setText("")
        binding.editTextTtof.setText("")
        binding.editTextDtof.setText("")
        binding.editTextStdDev.setText("")
        binding.editTextTime.setText("")
        binding.editTextChipTemperature.setText("")
        binding.editTextLux.setText("")
        binding.editTextRakFrameCounter.setText("")
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val TAG = "DashboardFragment"
    }
}

/** Utility function to convert ByteArray to Hexadecimal String */
fun ByteArray.toHexString() = joinToString(separator = " ") {
    String.format("%02X", it)
}

/** Utility function to convert an Int to a Hexadecimal String (e.g., for Status Flags) */
fun Int.toHexString() = String.format("%08X", this) // 4 bytes = 8 digits

/** Utility function to format a Float with precision */
fun Float.format(digits: Int) = "%.${digits}f".format(this)
