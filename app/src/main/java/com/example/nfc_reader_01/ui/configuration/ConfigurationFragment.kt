package com.example.nfc_reader_01.ui.configuration

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.nfc_reader_01.NfcInteractionListener
import com.example.nfc_reader_01.SharedNfcViewModel
import com.example.nfc_reader_01.data.ConfigurationData
import com.example.nfc_reader_01.data.NfcDataParser
import com.example.nfc_reader_01.databinding.FragmentConfigurationBinding
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A [Fragment] that displays the configuration data and allows the user to read, write, and reset the configuration.
 */
class ConfigurationFragment : Fragment() {

    companion object {
        const val COMMAND_READ_CONFIG = 0x03.toByte()
        const val COMMAND_WRITE_CONFIG = 0x04.toByte()
        const val COMMAND_FACTORY_RESET = 0x0A.toByte()
        // This is the USEFUL size of the configuration data (96 bytes)
        const val CONFIG_BYTE_SIZE = 96
        const val FLOAT_FORMAT = "%.4f"
    }

    private val sharedNfcViewModel: SharedNfcViewModel by activityViewModels()

    private var _binding: FragmentConfigurationBinding? = null
    private val binding get() = _binding!!

    // Stores the last read configuration to be able to modify and save it (CRITICAL)
    private var currentConfigData: ConfigurationData? = null

    // Reference to the activity's listener (MainActivity)
    private var listener: NfcInteractionListener? = null

    // --- Lifecycle for the Listener ---

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is NfcInteractionListener) {
            listener = context
        } else {
            // Log.wtf is used for critical Activity configuration errors
            Log.wtf("ConfigFragment", "$context must implement NfcInteractionListener")
        }
    }

    override fun onDetach() {
        super.onDetach()
        listener = null
    }

    // --- Views and Logic ---

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentConfigurationBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.textViewLastConfigurationDate.text = "No data read"

        setupListeners()
        observeViewModel()
    }

    /**
     * Sets up the listeners for the action buttons (Read, Save, Reset).
     */
    private fun setupListeners() {
        // --- 1. SAVE CONFIGURATION (Write to TAG) ---
        binding.saveConfigButton.setOnClickListener {
            // CRITICAL: We must have the base configuration read to not overwrite the un-displayed fields.
            val baseConfig = currentConfigData
            if (baseConfig == null) {
                // Improved message: Action required
                Toast.makeText(
                    requireContext(),
                    "ERROR: You must READ the current configuration (0x03) before trying to SAVE.",
                    Toast.LENGTH_LONG
                ).show()
                return@setOnClickListener
            }

            // 1. Read the 11 UI fields and update the ConfigurationData object.
            val newConfigData = updateConfigDataFromUI(baseConfig)
            if (newConfigData == null) {
                // The field validation error is already shown in the respective text field
                Toast.makeText(requireContext(), "Review the fields with format errors (they must be valid numbers).", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            // 2. Serialize the complete 96-byte object (including un-displayed fields and updated timestamp).
            val configBytes = NfcDataParser.serializeConfigData(newConfigData)

            if (configBytes.size != CONFIG_BYTE_SIZE) {
                Log.e("ConfigFragment", "Serialization error: Expected size $CONFIG_BYTE_SIZE, got ${configBytes.size}")
                // Improved message: Internal protocol error
                Toast.makeText(requireContext(), "Internal error: Failed to serialize the write package.", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            // 3. Set the data to be written in the next scan
            sharedNfcViewModel.setConfigDataToWrite(configBytes)

            // 4. Request the Activity to execute the write command
            listener?.requestWriteConfig() ?: run {
                Toast.makeText(requireContext(), "Error: The activity is not ready for NFC.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 5. Visually update the date as "Pending Save"
            val timestamp = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date())
            binding.textViewLastConfigurationDate.text = "PENDING SAVE ($timestamp)"
        }

        // --- 2. READ CONFIGURATION (Read from TAG) ---
        binding.readConfigButton.setOnClickListener {
            // 1. Clear previous write data and request the read command
            sharedNfcViewModel.setConfigDataToWrite(null)
            listener?.requestNextCommand(COMMAND_READ_CONFIG) ?: run {
                Toast.makeText(requireContext(), "Error: The activity is not ready for NFC.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
        }

        // --- 3. FACTORY RESET CONFIGURATION ---
        binding.factoryResetButton.setOnClickListener {
            // 1. Clear write data (just in case) and request the reset command
            sharedNfcViewModel.setConfigDataToWrite(null)
            listener?.requestNextCommand(COMMAND_FACTORY_RESET) ?: run {
                Toast.makeText(requireContext(), "Error: The activity is not ready for NFC.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
        }
    }


    /**
     * Reads the 11 floating-point text fields from the UI and creates a new ConfigurationData
     * from the base, updating only the visible fields.
     * @param baseConfig The ConfigurationData previously read from the TAG.
     * @return The new ConfigurationData with the updated fields, or null if there is a format error.
     */
    private fun updateConfigDataFromUI(baseConfig: ConfigurationData): ConfigurationData? {
        var updatedConfig = baseConfig
        var allValid = true

        val uiUpdates: List<Triple<TextInputEditText, Float, (Float) -> Unit>> = listOf(
            Triple(binding.editTextKMeter, baseConfig.kMeter) { v -> updatedConfig = updatedConfig.copy(kMeter = v) },
            Triple(binding.editTextTempRawLow, baseConfig.lowTempUnscaled) { v -> updatedConfig = updatedConfig.copy(lowTempUnscaled = v) },
            Triple(binding.editTextTempRawHigh, baseConfig.highTempUnscaled) { v -> updatedConfig = updatedConfig.copy(highTempUnscaled = v) },
            Triple(binding.editTextTempCalLow, baseConfig.lowTempCorrected) { v -> updatedConfig = updatedConfig.copy(lowTempCorrected = v) },
            Triple(binding.editTextTempCalHigh, baseConfig.highTempCorrected) { v -> updatedConfig = updatedConfig.copy(highTempCorrected = v) },
            Triple(binding.editTextFcqQ1Error, baseConfig.fcQ1_error) { v -> updatedConfig = updatedConfig.copy(fcQ1_error = v) },
            Triple(binding.editTextFcqQ2Error, baseConfig.fcQ2_error) { v -> updatedConfig = updatedConfig.copy(fcQ2_error = v) },
            Triple(binding.editTextFcq035Error, baseConfig.fcQ0_35_error) { v -> updatedConfig = updatedConfig.copy(fcQ0_35_error = v) },
            Triple(binding.editTextFcq100Error, baseConfig.fcQ1_00_error) { v -> updatedConfig = updatedConfig.copy(fcQ1_00_error = v) },
            Triple(binding.editTextFcq10LmError, baseConfig.fcQ10_00_error) { v -> updatedConfig = updatedConfig.copy(fcQ10_00_error = v) },
            Triple(binding.editTextFcqQ3Error, baseConfig.fcQ3_error) { v -> updatedConfig = updatedConfig.copy(fcQ3_error = v) }
        )

        for ((field, _, updateAction) in uiUpdates) {
            val text = field.text?.toString()?.trim() ?: ""
            // If the field is empty, we consider 0.0f by default if the TAG allows it, or we force an error.
            // For configuration, it is better to force an error if it is not valid.
            val floatValue = text.toFloatOrNull()
            if (floatValue == null) {
                field.error = "Must be a valid numeric value"
                allValid = false
            } else {
                field.error = null
                updateAction(floatValue)
            }
        }

        return if (allValid) updatedConfig else null
    }

    /**
     * Displays the complete ConfigurationData in the 11 available UI fields.
     * @param config The ConfigurationData already parsed from the TAG.
     */
    private fun displayConfigData(config: ConfigurationData) {

        // 1. Fill UI fields
        binding.editTextKMeter.setText(String.format(Locale.getDefault(), FLOAT_FORMAT, config.kMeter))
        binding.editTextTempRawLow.setText(String.format(Locale.getDefault(), FLOAT_FORMAT, config.lowTempUnscaled))
        binding.editTextTempRawHigh.setText(String.format(Locale.getDefault(), FLOAT_FORMAT, config.highTempUnscaled))
        binding.editTextTempCalLow.setText(String.format(Locale.getDefault(), FLOAT_FORMAT, config.lowTempCorrected))
        binding.editTextTempCalHigh.setText(String.format(Locale.getDefault(), FLOAT_FORMAT, config.highTempCorrected))
        binding.editTextFcqQ1Error.setText(String.format(Locale.getDefault(), FLOAT_FORMAT, config.fcQ1_error))
        binding.editTextFcqQ2Error.setText(String.format(Locale.getDefault(), FLOAT_FORMAT, config.fcQ2_error))
        binding.editTextFcq035Error.setText(String.format(Locale.getDefault(), FLOAT_FORMAT, config.fcQ0_35_error))
        binding.editTextFcq100Error.setText(String.format(Locale.getDefault(), FLOAT_FORMAT, config.fcQ1_00_error))
        binding.editTextFcq10LmError.setText(String.format(Locale.getDefault(), FLOAT_FORMAT, config.fcQ10_00_error))
        binding.editTextFcqQ3Error.setText(String.format(Locale.getDefault(), FLOAT_FORMAT, config.fcQ3_error))

        // 2. Display configuration date
        val dateMillis = config.lastConfigurationDate.toLong() * 1000
        val date = Date(dateMillis)
        val dateFormatter = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
        val formattedDate = dateFormatter.format(date)
        binding.textViewLastConfigurationDate.text = formattedDate
    }


    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                sharedNfcViewModel.configurationResponseData.collect { bytes ->
                    if (bytes != null) {
                        // PADDING ADJUSTMENT CRITERION: The received buffer (e.g., 127 bytes)
                        // must be AT LEAST the size of the useful data (96 bytes).
                        if (bytes.size >= CONFIG_BYTE_SIZE) {

                            // We extract ONLY the first 96 bytes which are the configuration data.
                            val usefulBytes = bytes.sliceArray(0 until CONFIG_BYTE_SIZE)

                            if (bytes.size != CONFIG_BYTE_SIZE) {
                                Log.i("ConfigFragment", "Received buffer with padding (${bytes.size} bytes). Processing the first $CONFIG_BYTE_SIZE bytes.")
                            }

                            try {
                                // 1. Parse the complete 96 bytes
                                val config = NfcDataParser.parseConfigData(usefulBytes)
                                // 2. Store the complete object for future writes
                                currentConfigData = config
                                // 3. Display only the relevant fields in the UI
                                displayConfigData(config)
                            } catch (e: Exception) {
                                Log.e("ConfigFragment", "Error parsing 0x83 data: ${e.message}")
                                // Improved message: Processing error
                                Toast.makeText(requireContext(), "ERROR: Failed to process Configuration data (0x83).", Toast.LENGTH_LONG).show()
                            }
                        } else {
                            // This case occurs if less than 96 bytes are received (incomplete data)
                            // Improved message: Size error
                            Toast.makeText(
                                requireContext(),
                                "ERROR: Incomplete data. Received ${bytes.size} bytes, expected $CONFIG_BYTE_SIZE.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                        // Clear the data after consuming it
                        sharedNfcViewModel.clearConfigurationResponseData()
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
