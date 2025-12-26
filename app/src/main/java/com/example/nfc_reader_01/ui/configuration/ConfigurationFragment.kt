package com.example.nfc_reader_01.ui.configuration

import android.app.AlertDialog
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
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
import com.example.nfc_reader_01.utils.LogManager
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ConfigurationFragment : Fragment() {

    // Comandos y Constantes
    companion object {
        // Comandos Estándar
        const val COMMAND_READ_CONFIG = 0x03.toByte()
        const val COMMAND_WRITE_CONFIG = 0x04.toByte()
        const val COMMAND_FACTORY_RESET = 0x0A.toByte()

        // Comandos Especiales de Control
        const val COMMAND_SHUTDOWN = 0x10.toByte()       // Apagado
        const val COMMAND_RESET_VOLUME = 0x11.toByte()    // Reset de Volumen
        const val COMMAND_NORMAL_MODE = 0x12.toByte()     // Modo Normal
        const val COMMAND_CALIBRATE_FLOW = 0x13.toByte()  // Calibración
        const val COMMAND_SET_VOLUME = 0x14.toByte()      // Setear Volumen
        const val COMMAND_ENTER_ENGINEERING_MODE = 0x15.toByte() // Modo Ingeniería
        const val COMMAND_CLEAR_FLAGS = 0x16.toByte()     // NUEVO: Limpiar Banderas

        // Configuración de Datos
        const val CONFIG_BYTE_SIZE = 96
        const val FLOAT_FORMAT = "%.4f"
        const val NUM_CONFIG_FIELDS = 11
        private const val TAG = "ConfigFragment"
    }

    private val sharedNfcViewModel: SharedNfcViewModel by activityViewModels()

    private var _binding: FragmentConfigurationBinding? = null
    private val binding get() = _binding!!

    // Almacena la última configuración leída para poder modificarla y guardarla (CRÍTICO)
    private var currentConfigData: ConfigurationData? = null

    private var listener: NfcInteractionListener? = null

    // Launcher para selección de archivos
    private val fileLoaderLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            readAndProcessConfigFile(it)
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is NfcInteractionListener) {
            listener = context
        } else {
            Log.wtf(TAG, "${context.toString()} debe implementar NfcInteractionListener")
        }
    }

    override fun onDetach() {
        super.onDetach()
        listener = null
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentConfigurationBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.textViewLastConfigurationDate.setText("Sin datos de lectura")
        setupListeners()
        observeViewModel()
    }

    /**
     * Configura los listeners para TODOS los botones.
     */
    private fun setupListeners() {
        // --- 1. GUARDAR CONFIGURACIÓN ---
        binding.saveConfigButton.setOnClickListener {
            val baseConfig = currentConfigData
            if (baseConfig == null) {
                Toast.makeText(requireContext(), "ERROR: Debe LEER la configuración actual (0x03) antes de intentar GUARDAR.", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            val newConfigData = updateConfigDataFromUI(baseConfig)
            if (newConfigData == null) {
                Toast.makeText(requireContext(), "Revise los campos con errores de formato.", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            val configBytes = NfcDataParser.serializeConfigData(newConfigData)
            if (configBytes.size != CONFIG_BYTE_SIZE) {
                Toast.makeText(requireContext(), "Error interno de serialización.", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            sharedNfcViewModel.setConfigDataToWrite(configBytes)
            listener?.requestWriteConfig() ?: run {
                Toast.makeText(requireContext(), "Error: Activity no lista.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val timestamp = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date())
            binding.textViewLastConfigurationDate.setText("PENDIENTE DE GUARDAR ($timestamp)")
        }

        // --- 2. LEER CONFIGURACIÓN ---
        binding.readConfigButton.setOnClickListener {
            sharedNfcViewModel.setConfigDataToWrite(null)
            listener?.requestNextCommand(COMMAND_READ_CONFIG)
        }

        // --- 3. RESTABLECER CONFIGURACIÓN DE FÁBRICA ---
        binding.factoryResetButton.setOnClickListener {
            sharedNfcViewModel.setConfigDataToWrite(null)
            listener?.requestNextCommand(COMMAND_FACTORY_RESET)
        }

        // --- 4. CARGAR DESDE ARCHIVO ---
        binding.loadFromFileButton.setOnClickListener {
            fileLoaderLauncher.launch("text/*")
        }

        // --- 5. COMANDOS ESPECIALES DE CONTROL ---

        binding.shutdownButton.setOnClickListener {
            sharedNfcViewModel.setConfigDataToWrite(null)
            listener?.requestNextCommand(COMMAND_SHUTDOWN)
        }

        binding.resetDeviceButton.setOnClickListener {
            sharedNfcViewModel.setConfigDataToWrite(null)
            listener?.requestNextCommand(COMMAND_RESET_VOLUME)
        }

        binding.normalModeButton.setOnClickListener {
            sharedNfcViewModel.setConfigDataToWrite(null)
            listener?.requestNextCommand(COMMAND_NORMAL_MODE)
        }

        binding.calibrateFlowButton.setOnClickListener {
            sharedNfcViewModel.setConfigDataToWrite(null)
            listener?.requestNextCommand(COMMAND_CALIBRATE_FLOW)
        }

        binding.setVolumeButton.setOnClickListener {
            showSetVolumeDialog()
        }

        binding.enterEngineeringModeButton.setOnClickListener {
            sharedNfcViewModel.setConfigDataToWrite(null)
            listener?.requestEnterEngineeringMode()
        }

        // NUEVO: Listener para Limpiar Banderas (0x16)
        binding.clearFlagsButton.setOnClickListener {
            sharedNfcViewModel.setConfigDataToWrite(null)
            listener?.requestNextCommand(COMMAND_CLEAR_FLAGS)
        }
    }

    private fun showSetVolumeDialog() {
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle("Establecer Volumen (0x14)")
        builder.setMessage("Ingrese el nuevo valor de volumen (Float):")
        val input = EditText(requireContext())
        input.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        builder.setView(input)

        builder.setPositiveButton("Aceptar") { _, _ ->
            val text = input.text.toString()
            val volumeValue = text.toFloatOrNull()
            if (volumeValue != null) {
                val buffer = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
                buffer.putFloat(volumeValue)
                val volumeBytes = buffer.array()
                sharedNfcViewModel.setConfigDataToWrite(volumeBytes)
                listener?.requestSetVolume()
            } else {
                Toast.makeText(requireContext(), "Valor inválido. Ingrese un número decimal.", Toast.LENGTH_SHORT).show()
            }
        }
        builder.setNegativeButton("Cancelar") { dialog, _ -> dialog.cancel() }
        builder.show()
    }

    private fun readAndProcessConfigFile(uri: Uri) {
        lifecycleScope.launch {
            try {
                val fileContent = withContext(Dispatchers.IO) {
                    val contentResolver = requireContext().contentResolver
                    contentResolver.openInputStream(uri)?.use { inputStream ->
                        BufferedReader(InputStreamReader(inputStream)).use { reader ->
                            reader.readText()
                        }
                    } ?: throw IllegalStateException("No se pudo abrir el stream.")
                }
                handleFileContent(fileContent)
            } catch (e: Exception) {
                Log.e(TAG, "Error archivo: ${e.message}", e)
                Toast.makeText(requireContext(), "ERROR al cargar: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun handleFileContent(content: String) {
        var allValid = true
        val floatMap = mutableMapOf<String, Float>()
        val requiredFields = listOf(
            "kMeter", "lowTempUnscaled", "highTempUnscaled", "lowTempCorrected",
            "highTempCorrected", "fcQ1_error", "fcQ2_error", "fcQ0_35_error",
            "fcQ1_00_error", "fcQ10_00_error", "fcQ3_error"
        )
        try {
            val json = JSONObject(content)
            for (key in requiredFields) {
                if (!json.has(key)) {
                    Toast.makeText(requireContext(), "Falta campo '$key' en JSON.", Toast.LENGTH_LONG).show()
                    allValid = false
                    break
                }
                floatMap[key] = json.getDouble(key).toFloat()
            }
        } catch (e: JSONException) {
            Toast.makeText(requireContext(), "Error JSON inválido.", Toast.LENGTH_LONG).show()
            return
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Error inesperado: ${e.message}", Toast.LENGTH_LONG).show()
            return
        }

        if (!allValid) return
        var updatedConfig = currentConfigData ?: NfcDataParser.DEFAULT_CONFIG_DATA
        updatedConfig = updatedConfig.copy(
            kMeter = floatMap["kMeter"]!!,
            lowTempUnscaled = floatMap["lowTempUnscaled"]!!,
            highTempUnscaled = floatMap["highTempUnscaled"]!!,
            lowTempCorrected = floatMap["lowTempCorrected"]!!,
            highTempCorrected = floatMap["highTempCorrected"]!!,
            fcQ1_error = floatMap["fcQ1_error"]!!,
            fcQ2_error = floatMap["fcQ2_error"]!!,
            fcQ0_35_error = floatMap["fcQ0_35_error"]!!,
            fcQ1_00_error = floatMap["fcQ1_00_error"]!!,
            fcQ10_00_error = floatMap["fcQ10_00_error"]!!,
            fcQ3_error = floatMap["fcQ3_error"]!!,
            lastConfigurationDate = (System.currentTimeMillis() / 1000).toInt()
        )
        currentConfigData = updatedConfig
        displayConfigData(updatedConfig)
        Toast.makeText(requireContext(), "Configuración cargada desde archivo.", Toast.LENGTH_SHORT).show()
    }

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
            val floatValue = text.toFloatOrNull()
            if (floatValue == null) {
                field.error = "Valor inválido"
                allValid = false
            } else {
                field.error = null
                updateAction(floatValue)
            }
        }
        if (allValid) {
            updatedConfig = updatedConfig.copy(
                lastConfigurationDate = (System.currentTimeMillis() / 1000).toInt()
            )
        }
        return if (allValid) updatedConfig else null
    }

    private fun displayConfigData(config: ConfigurationData) {
        binding.editTextKMeter.setText(String.format(Locale.US, FLOAT_FORMAT, config.kMeter))
        binding.editTextTempRawLow.setText(String.format(Locale.US, FLOAT_FORMAT, config.lowTempUnscaled))
        binding.editTextTempRawHigh.setText(String.format(Locale.US, FLOAT_FORMAT, config.highTempUnscaled))
        binding.editTextTempCalLow.setText(String.format(Locale.US, FLOAT_FORMAT, config.lowTempCorrected))
        binding.editTextTempCalHigh.setText(String.format(Locale.US, FLOAT_FORMAT, config.highTempCorrected))
        binding.editTextFcqQ1Error.setText(String.format(Locale.US, FLOAT_FORMAT, config.fcQ1_error))
        binding.editTextFcqQ2Error.setText(String.format(Locale.US, FLOAT_FORMAT, config.fcQ2_error))
        binding.editTextFcq035Error.setText(String.format(Locale.US, FLOAT_FORMAT, config.fcQ0_35_error))
        binding.editTextFcq100Error.setText(String.format(Locale.US, FLOAT_FORMAT, config.fcQ1_00_error))
        binding.editTextFcq10LmError.setText(String.format(Locale.US, FLOAT_FORMAT, config.fcQ10_00_error))
        binding.editTextFcqQ3Error.setText(String.format(Locale.US, FLOAT_FORMAT, config.fcQ3_error))

        val dateMillis = config.lastConfigurationDate.toLong() * 1000
        val date = Date(dateMillis)
        val dateFormatter = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
        binding.textViewLastConfigurationDate.setText(dateFormatter.format(date))
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                sharedNfcViewModel.configurationResponseData.collect { bytes ->
                    if (bytes != null) {
                        if (bytes.size >= CONFIG_BYTE_SIZE) {
                            val usefulBytes = bytes.sliceArray(0 until CONFIG_BYTE_SIZE)
                            try {
                                val config = NfcDataParser.parseConfigData(usefulBytes)
                                currentConfigData = config
                                displayConfigData(config)
                            } catch (e: Exception) {
                                Toast.makeText(requireContext(), "ERROR al parsear Configuración (0x83).", Toast.LENGTH_LONG).show()
                            }
                        } else {
                            Toast.makeText(requireContext(), "ERROR: Datos incompletos.", Toast.LENGTH_LONG).show()
                        }
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
