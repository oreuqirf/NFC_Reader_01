package com.example.nfc_reader_01.ui.configuration

import android.app.AlertDialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
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
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.nfc_reader_01.NfcConstants
import com.example.nfc_reader_01.NfcInteractionListener
import com.example.nfc_reader_01.NfcState
import com.example.nfc_reader_01.R
import com.example.nfc_reader_01.SharedNfcViewModel
import com.example.nfc_reader_01.data.ConfigurationData
import com.example.nfc_reader_01.data.NfcDataParser
import com.example.nfc_reader_01.databinding.FragmentConfigurationBinding
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ConfigurationFragment : Fragment() {


    companion object {
        const val COMMAND_READ_CONFIG = 0x03.toByte()
        const val COMMAND_WRITE_CONFIG = 0x04.toByte()
        // const val COMMAND_FACTORY_RESET = 0x0A.toByte() // Opcional si no se usa

        // --- CONSTANTES QUE FALTABAN ---
        const val COMMAND_SHUTDOWN = 0x10.toByte()
        const val COMMAND_RESET_VOLUME = 0x11.toByte()
        const val COMMAND_NORMAL_MODE = 0x12.toByte()
        // const val COMMAND_CALIBRATE_FLOW = 0x13.toByte() // Se usa en CalibrationFragment
        const val COMMAND_SET_VOLUME = 0x14.toByte()
        const val COMMAND_ENTER_ENGINEERING_MODE = 0x15.toByte()
        const val COMMAND_CLEAR_FLAGS = 0x16.toByte()

        const val CONFIG_BYTE_SIZE = 80
        const val FLOAT_FORMAT = "%.4f"
        private const val TAG = "ConfigFragment"
    }


    private val sharedNfcViewModel: SharedNfcViewModel by activityViewModels()
    private var _binding: FragmentConfigurationBinding? = null
    private val binding get() = _binding!!

    private var currentConfigData: ConfigurationData? = null
    private var listener: NfcInteractionListener? = null

    // LANZADORES DE ARCHIVO
    private val fileLoaderLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { readAndProcessConfigFile(it) }
    }

    private val createFileLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        // Al momento de que el sistema crea el archivo y nos da la URI, exportamos los datos actuales
        uri?.let { exportConfigToUri(it) }
        enableAllButtons()
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is NfcInteractionListener) {
            listener = context
        } else {
            Log.e(TAG, "$context debe implementar NfcInteractionListener")
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

        // --- CAMBIO: Si no hay datos cargados, mostramos fecha vacía, pero los campos son editables
        if (currentConfigData == null) {
            binding.textViewLastConfigurationDate.text = getString(R.string.text_no_data)
        }

        setupListeners()
        observeViewModel()
    }

    // --- (Funciones setButtonState y enableAllButtons se mantienen IGUAL) ---
    private fun setButtonState(view: View, isEnabled: Boolean) {
        if (view !is MaterialButton) return
        view.isEnabled = isEnabled

        val isOutlinedButton = view.id == R.id.loadFromFileButton || view.id == R.id.exportToFileButton

        if (isOutlinedButton) {
            view.backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
            val color = if (isEnabled) {
                ContextCompat.getColor(requireContext(), R.color.teal_700)
            } else {
                Color.GRAY
            }
            view.strokeColor = ColorStateList.valueOf(color)
            view.setTextColor(color)

        } else {
            val primaryColor = ContextCompat.getColor(requireContext(), R.color.purple_500)
            val disabledColor = Color.GRAY

            val activeColor = when (view.id) {
                R.id.shutdownButton, R.id.resetDeviceButton -> Color.parseColor("#D32F2F")
                R.id.enterEngineeringModeButton -> Color.parseColor("#FF9800")
                R.id.setVolumeButton, R.id.normalModeButton,
                R.id.clearFlagsButton -> ContextCompat.getColor(requireContext(), R.color.teal_700)
                else -> primaryColor
            }

            if (isEnabled) {
                view.backgroundTintList = ColorStateList.valueOf(activeColor)
            } else {
                view.backgroundTintList = ColorStateList.valueOf(disabledColor)
            }
        }
    }

    private fun enableAllButtons() {
        setButtonState(binding.readConfigButton, true)
        setButtonState(binding.saveConfigButton, true)
        setButtonState(binding.loadFromFileButton, true)
        setButtonState(binding.exportToFileButton, true)
        setButtonState(binding.shutdownButton, true)
        setButtonState(binding.resetDeviceButton, true)
        setButtonState(binding.normalModeButton, true)
        setButtonState(binding.setVolumeButton, true)
        setButtonState(binding.enterEngineeringModeButton, true)
        setButtonState(binding.clearFlagsButton, true)
    }

    // --------------------------------------------------

    private fun setupListeners() {
        // LEER (Sin cambios)
        binding.readConfigButton.setOnClickListener {
            setButtonState(it, false)
            sharedNfcViewModel.setConfigDataToWrite(null)
            listener?.requestNextCommand(COMMAND_READ_CONFIG)
        }


        // GUARDAR (ESCRIBIR EN NFC)
        binding.saveConfigButton.setOnClickListener {
            Log.d(TAG, "¡BOTÓN GUARDAR CLICKEADO!") // <--- NUEVO LOG DE PRUEBA

            val baseConfig = currentConfigData ?: createBlankConfiguration()
            val newConfigData = updateConfigDataFromUI(baseConfig)

            if (newConfigData == null) {
                Toast.makeText(requireContext(), getString(R.string.msg_error_invalid_fields), Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            currentConfigData = newConfigData

            // Convertimos a bytes
            val configBytes = NfcDataParser.serializeConfigData(newConfigData)

            // --- EL DETECTIVE DE BYTES ---
            if (configBytes.size != CONFIG_BYTE_SIZE) {
                val errorMsg = "Error interno: El parser generó ${configBytes.size} bytes, pero se esperaban $CONFIG_BYTE_SIZE."
                Log.e(TAG, errorMsg)
                Toast.makeText(requireContext(), errorMsg, Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            Log.d(TAG, "Bytes serializados correctamente (${configBytes.size} bytes). Asignando al ViewModel...") // <--- NUEVO LOG
            setButtonState(it, false)
            sharedNfcViewModel.setConfigDataToWrite(configBytes)

            if (listener == null) {
                Log.e(TAG, "¡ERROR CRÍTICO! El listener es NULL en ConfigurationFragment. No se puede enviar el comando.")
            } else {
                Log.d(TAG, "Enviando comando COMMAND_WRITE_CONFIG al listener...")
            }

            listener?.requestNextCommand(COMMAND_WRITE_CONFIG)
        }


        // CARGAR (Sin cambios)
        binding.loadFromFileButton.setOnClickListener {
            fileLoaderLauncher.launch("application/json")
        }

        // EXPORTAR (GUARDAR EN ARCHIVO)
        binding.exportToFileButton.setOnClickListener {
            // --- CAMBIO CRÍTICO: Permitir exportar lo que está en pantalla sin leer antes ---

            // 1. Obtenemos base (actual o vacía)
            val baseConfig = currentConfigData ?: createBlankConfiguration()

            // 2. Intentamos parsear lo que hay en pantalla
            val configToExport = updateConfigDataFromUI(baseConfig)

            if (configToExport == null) {
                // Hay errores en los campos de texto (letras, vacíos, etc)
                Toast.makeText(requireContext(), getString(R.string.msg_error_invalid_fields), Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            // 3. Actualizamos el objeto actual con lo que hay en pantalla para que el Launcher lo use
            currentConfigData = configToExport

            setButtonState(it, false)
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
            val fileName = "config_nfc_$timeStamp.json"
            createFileLauncher.launch(fileName)
        }

        // COMANDOS DE CONTROL (Sin cambios)
        val commandButtons = mapOf(
            binding.shutdownButton to COMMAND_SHUTDOWN,
            binding.resetDeviceButton to COMMAND_RESET_VOLUME,
            binding.normalModeButton to COMMAND_NORMAL_MODE,
            binding.clearFlagsButton to COMMAND_CLEAR_FLAGS,
            binding.enterEngineeringModeButton to COMMAND_ENTER_ENGINEERING_MODE
        )

        commandButtons.forEach { (btn, cmd) ->
            btn.setOnClickListener {
                setButtonState(it, false)
                sharedNfcViewModel.setConfigDataToWrite(null)
                if (cmd == COMMAND_ENTER_ENGINEERING_MODE) {
                    listener?.requestEnterEngineeringMode()
                } else {
                    listener?.requestNextCommand(cmd)
                }
            }
        }

        // SET VOLUME (Sin cambios)
        binding.setVolumeButton.setOnClickListener {
            showSetVolumeDialog()
        }


        binding.btnSaveToDatabase.setOnClickListener {

            // 1. Extraemos los textos EXACTOS de la identidad
            val displaySerial = binding.editTextSerialNumber.text.toString()
            val displayFirmware = binding.editTextFirmwareVersion.text.toString()

            // 2. ¡Magia del Fragmento! Leemos TODO lo que hay en la pantalla usando tu propia función
            val baseConfig = currentConfigData ?: createBlankConfiguration()
            val configToSave = updateConfigDataFromUI(baseConfig)

            // Si updateConfigDataFromUI devuelve null, significa que el usuario dejó un campo vacío o puso letras
            if (configToSave == null) {
                Toast.makeText(requireContext(), getString(R.string.msg_error_invalid_fields), Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            // 3. Validamos que haya un equipo leído (Serial válido)
            if (displaySerial.isNotEmpty() && displaySerial != "ERROR PARSE") {

                // 4. Enviamos a guardar a la base de datos
                sharedNfcViewModel.saveInstrumentDataToDatabase(
                    context = requireContext(),
                    serial = displaySerial,
                    firmware = displayFirmware,
                    config = configToSave // <-- ¡Aquí pasamos el objeto perfecto y 100% tipado!
                )

            } else {
                Toast.makeText(requireContext(), "No hay un número de serie válido para guardar", Toast.LENGTH_SHORT).show()
            }
        }

        // --- NUEVO: EXPORTAR BASE DE DATOS A CSV ---
        binding.btnExportCsv.setOnClickListener {
            exportDatabaseToCSV()
        }

    }

    private fun createBlankConfiguration(): ConfigurationData {
        return ConfigurationData(
            kMeter = 0f,
            lowStability = 0f,
            highStability = 0f,
            lowTempUnscaled = 0f, highTempUnscaled = 0f,
            lowTempCorrected = 0f, highTempCorrected = 0f,

            // Curva de 12 puntos
            fcQ1_error = 0f, fcQ2_error = 0f,
            fcP3_error = 0f, fcP4_error = 0f,
            fcP5_error = 0f, fcP6_error = 0f,
            fcP7_error = 0f, fcP8_error = 0f,
            fc10_00_error = 0f, fcP10_error = 0f,
            fcQ3_error = 0f, fcQ4_error = 0f,

            lastConfigurationDate = (System.currentTimeMillis() / 1000).toInt()
        )
    }

    // ----------------------------

    private fun showSetVolumeDialog() {
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle(getString(R.string.dialog_title_volume))
        builder.setMessage(getString(R.string.dialog_msg_volume))

        val input = EditText(requireContext())
        input.inputType = InputType.TYPE_CLASS_NUMBER
        builder.setView(input)

        builder.setPositiveButton(getString(R.string.dialog_btn_accept)) { _, _ ->
            val text = input.text.toString()
            val volumeLong = text.toLongOrNull()

            if (volumeLong != null) {
                setButtonState(binding.setVolumeButton, false)
                val buffer = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
                buffer.putInt(volumeLong.toInt())
                sharedNfcViewModel.setConfigDataToWrite(buffer.array())
                listener?.requestSetVolume()
            } else {
                Toast.makeText(requireContext(), getString(R.string.msg_invalid_value), Toast.LENGTH_SHORT).show()
            }
        }
        builder.setNegativeButton(getString(R.string.dialog_btn_cancel)) { dialog, _ -> dialog.cancel() }
        builder.show()
    }

    // --- PROCESAMIENTO DE DATOS ---

    private fun displayConfigData(config: ConfigurationData) {
        try {
            binding.editTextKMeter.setText(String.format(Locale.US, FLOAT_FORMAT, config.kMeter))

            binding.editTextLowStability.setText(String.format(Locale.US, FLOAT_FORMAT, config.lowStability))
            binding.editTextHighStability.setText(String.format(Locale.US, FLOAT_FORMAT, config.highStability))

            binding.editTextTempRawLow.setText(String.format(Locale.US, FLOAT_FORMAT, config.lowTempUnscaled))
            binding.editTextTempRawHigh.setText(String.format(Locale.US, FLOAT_FORMAT, config.highTempUnscaled))
            binding.editTextTempCalLow.setText(String.format(Locale.US, FLOAT_FORMAT, config.lowTempCorrected))
            binding.editTextTempCalHigh.setText(String.format(Locale.US, FLOAT_FORMAT, config.highTempCorrected))

            // --- Curva de Error de Flujo (12 Puntos) ---
            binding.editTextFcqQ1Error.setText(String.format(Locale.US, FLOAT_FORMAT, config.fcQ1_error))
            binding.editTextFcqQ2Error.setText(String.format(Locale.US, FLOAT_FORMAT, config.fcQ2_error))
            binding.editTextFcqP3Error.setText(String.format(Locale.US, FLOAT_FORMAT, config.fcP3_error))
            binding.editTextFcqP4Error.setText(String.format(Locale.US, FLOAT_FORMAT, config.fcP4_error))
            binding.editTextFcqP5Error.setText(String.format(Locale.US, FLOAT_FORMAT, config.fcP5_error))
            binding.editTextFcqP6Error.setText(String.format(Locale.US, FLOAT_FORMAT, config.fcP6_error))
            binding.editTextFcqP7Error.setText(String.format(Locale.US, FLOAT_FORMAT, config.fcP7_error))
            binding.editTextFcqP8Error.setText(String.format(Locale.US, FLOAT_FORMAT, config.fcP8_error))
            binding.editTextFcq10LmError.setText(String.format(Locale.US, FLOAT_FORMAT, config.fc10_00_error))
            binding.editTextFcqP10Error.setText(String.format(Locale.US, FLOAT_FORMAT, config.fcP10_error))
            binding.editTextFcqQ3Error.setText(String.format(Locale.US, FLOAT_FORMAT, config.fcQ3_error))
            binding.editTextFcqQ4Error.setText(String.format(Locale.US, FLOAT_FORMAT, config.fcQ4_error))

            val dateMillis = config.lastConfigurationDate.toLong() * 1000
            val date = Date(dateMillis)
            val dateFormatter = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
            binding.textViewLastConfigurationDate.text = dateFormatter.format(date)
        } catch (e: Exception) {
            Log.e(TAG, "Error mostrando datos: ${e.message}")
        }
    }

    private fun updateConfigDataFromUI(baseConfig: ConfigurationData): ConfigurationData? {
        var updatedConfig = baseConfig
        var allValid = true

        // 1. PROCESAMIENTO DE FLOATS (K Meter, Temperaturas, Tablas de Error - 12 Puntos)
        val uiUpdates: List<Triple<TextInputEditText, Float, (Float) -> Unit>> = listOf(
            Triple(binding.editTextKMeter, baseConfig.kMeter) { v -> updatedConfig = updatedConfig.copy(kMeter = v) },

            Triple(binding.editTextLowStability, baseConfig.lowStability) { v -> updatedConfig = updatedConfig.copy(lowStability = v) },
            Triple(binding.editTextHighStability, baseConfig.highStability) { v -> updatedConfig = updatedConfig.copy(highStability = v) },

            Triple(binding.editTextTempRawLow, baseConfig.lowTempUnscaled) { v -> updatedConfig = updatedConfig.copy(lowTempUnscaled = v) },
            Triple(binding.editTextTempRawHigh, baseConfig.highTempUnscaled) { v -> updatedConfig = updatedConfig.copy(highTempUnscaled = v) },
            Triple(binding.editTextTempCalLow, baseConfig.lowTempCorrected) { v -> updatedConfig = updatedConfig.copy(lowTempCorrected = v) },
            Triple(binding.editTextTempCalHigh, baseConfig.highTempCorrected) { v -> updatedConfig = updatedConfig.copy(highTempCorrected = v) },

            // --- Curva de Error de Flujo (12 Puntos en orden estricto) ---
            Triple(binding.editTextFcqQ1Error, baseConfig.fcQ1_error) { v -> updatedConfig = updatedConfig.copy(fcQ1_error = v) },
            Triple(binding.editTextFcqQ2Error, baseConfig.fcQ2_error) { v -> updatedConfig = updatedConfig.copy(fcQ2_error = v) },
            Triple(binding.editTextFcqP3Error, baseConfig.fcP3_error) { v -> updatedConfig = updatedConfig.copy(fcP3_error = v) },
            Triple(binding.editTextFcqP4Error, baseConfig.fcP4_error) { v -> updatedConfig = updatedConfig.copy(fcP4_error = v) },
            Triple(binding.editTextFcqP5Error, baseConfig.fcP5_error) { v -> updatedConfig = updatedConfig.copy(fcP5_error = v) },
            Triple(binding.editTextFcqP6Error, baseConfig.fcP6_error) { v -> updatedConfig = updatedConfig.copy(fcP6_error = v) },
            Triple(binding.editTextFcqP7Error, baseConfig.fcP7_error) { v -> updatedConfig = updatedConfig.copy(fcP7_error = v) },
            Triple(binding.editTextFcqP8Error, baseConfig.fcP8_error) { v -> updatedConfig = updatedConfig.copy(fcP8_error = v) },
            Triple(binding.editTextFcq10LmError, baseConfig.fc10_00_error) { v -> updatedConfig = updatedConfig.copy(fc10_00_error = v) },
            Triple(binding.editTextFcqP10Error, baseConfig.fcP10_error) { v -> updatedConfig = updatedConfig.copy(fcP10_error = v) },
            Triple(binding.editTextFcqQ3Error, baseConfig.fcQ3_error) { v -> updatedConfig = updatedConfig.copy(fcQ3_error = v) },
            Triple(binding.editTextFcqQ4Error, baseConfig.fcQ4_error) { v -> updatedConfig = updatedConfig.copy(fcQ4_error = v) }
        )

        for ((field, _, updateAction) in uiUpdates) {
            val text = field.text?.toString()?.trim() ?: ""
            val cleanText = text.replace(",", ".")
            val floatValue = cleanText.toFloatOrNull()

            if (floatValue == null) {
                val fieldName = resources.getResourceEntryName(field.id)
                Log.e("NFC_VALIDATION", "¡Fallo por texto inválido! Campo: $fieldName | Texto detectado: '$text'")
                field.error = getString(R.string.msg_invalid_value)
                allValid = false
            } else {
                if (field == binding.editTextLowStability && (floatValue < 0f || floatValue > 100f)) {
                    field.error = "0 - 100"
                    allValid = false
                } else if (field == binding.editTextHighStability && floatValue < 0f) {
                    field.error = "Inválido"
                    allValid = false
                } else {
                    field.error = null
                    updateAction(floatValue)
                }
            }
        }

        if (allValid) {
            updatedConfig = updatedConfig.copy(
                lastConfigurationDate = (System.currentTimeMillis() / 1000).toInt()
            )
        }

        return if (allValid) updatedConfig else null
    }




    private fun observeViewModel() {
        // (Sin cambios, solo observamos respuestas)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    sharedNfcViewModel.configurationResponseData.collect { bytes ->
                        if (bytes != null) {
                            enableAllButtons()
                            if (bytes.size >= NfcConstants.CONFIG_BYTE_SIZE) {
                                try {
                                    val config = NfcDataParser.parseConfigData(bytes)
                                    currentConfigData = config
                                    displayConfigData(config)
                                    Toast.makeText(requireContext(), getString(R.string.msg_config_read), Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    Toast.makeText(requireContext(), getString(R.string.msg_error_parse), Toast.LENGTH_SHORT).show()
                                    Log.e(TAG, "Parsing error", e)
                                }
                            } else {
                                Toast.makeText(requireContext(), getString(R.string.msg_error_data_size), Toast.LENGTH_LONG).show()
                            }
                            sharedNfcViewModel.clearConfigurationResponseData()
                        }
                    }
                }

                // --- NUEVO: OBSERVADOR DE IDENTIDAD EN SEGUNDO PLANO ---
                // Escuchamos exactamente la misma variable que usa el Dashboard
                launch {
                    sharedNfcViewModel.identityResponseData.collect { data ->
                        if (data != null && data.isNotEmpty()) {
                            try {
                                // Reutilizamos tu excelente lógica de parseo
                                val identity = NfcDataParser.parseIdentityData(data)
                                val highPart = (identity.deviceId shr 16) and 0xFFFF
                                val lowPart = identity.deviceId and 0xFFFF
                                val formattedSerial = String.format(Locale.US, "%05d-%05d", highPart, lowPart)

                                // Lo pintamos silenciosamente en las cajas ocultas/bloqueadas
                                binding.editTextSerialNumber.setText(formattedSerial)
                                binding.editTextFirmwareVersion.setText(identity.firmwareVersion)

                            } catch (e: Exception) {
                                Log.e(TAG, "Error parseando identidad en segundo plano: ${e.message}")
                            }
                        }
                    }
                }

                // ---------------------------------------------------------

                launch {
                    sharedNfcViewModel.writeStatus.collect { state ->
                        if (state !is NfcState.Loading && state !is NfcState.Idle) {
                            enableAllButtons()
                        }
                        when (state) {
                            is NfcState.Success -> {
                                Toast.makeText(requireContext(), getString(R.string.msg_command_success), Toast.LENGTH_SHORT).show()
                                if (binding.textViewLastConfigurationDate.text.toString().startsWith(getString(R.string.text_pending_save))) {
                                    val timestamp = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date())
                                    binding.textViewLastConfigurationDate.text = timestamp
                                }
                            }
                            is NfcState.Error -> {
                                enableAllButtons()
                                Toast.makeText(requireContext(), state.errorMessage, Toast.LENGTH_LONG).show()
                            }
                            is NfcState.Loading -> { }
                            is NfcState.Idle -> { }
                        }
                    }
                }
            }
        }
    }

    // CARGA DE ARCHIVO (Sin cambios)
    private fun readAndProcessConfigFile(uri: Uri) {
        lifecycleScope.launch {
            try {
                val fileContent = withContext(Dispatchers.IO) {
                    requireContext().contentResolver.openInputStream(uri)?.use {
                        BufferedReader(InputStreamReader(it)).readText()
                    } ?: ""
                }
                handleFileContent(fileContent)
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun handleFileContent(content: String) {
        try {
            val json = JSONObject(content)
            val newConfig = ConfigurationData(
                kMeter = json.optDouble("kMeter", 0.0).toFloat(),

                lowStability = json.optDouble("low_stability", 0.0).toFloat(),
                highStability = json.optDouble("high_stability", 0.0).toFloat(),

                lowTempUnscaled = json.optDouble("lowTempUnscaled", 0.0).toFloat(),
                highTempUnscaled = json.optDouble("highTempUnscaled", 0.0).toFloat(),
                lowTempCorrected = json.optDouble("lowTempCorrected", 0.0).toFloat(),
                highTempCorrected = json.optDouble("highTempCorrected", 0.0).toFloat(),

                // Curva de 8 puntos
                fcQ1_error = json.optDouble("fcQ1_error", 0.0).toFloat(),
                fcQ2_error = json.optDouble("fcQ2_error", 0.0).toFloat(),
                fcP3_error = json.optDouble("fcP3_error", 0.0).toFloat(),
                fcP4_error = json.optDouble("fcP4_error", 0.0).toFloat(),
                fcP5_error = json.optDouble("fcP5_error", 0.0).toFloat(),
                fcP6_error = json.optDouble("fcP6_error", 0.0).toFloat(),
                fcP7_error = json.optDouble("fcP7_error", 0.0).toFloat(),
                fcP8_error = json.optDouble("fcP8_error", 0.0).toFloat(),
                fc10_00_error = json.optDouble("fc10_00_error", 0.0).toFloat(),
                fcP10_error = json.optDouble("fcP10_error", 0.0).toFloat(),
                fcQ3_error = json.optDouble("fcQ3_error", 0.0).toFloat(),
                fcQ4_error = json.optDouble("fcQ4_error", 0.0).toFloat(),

                lastConfigurationDate = (System.currentTimeMillis() / 1000).toInt()
            )
            currentConfigData = newConfig
            displayConfigData(newConfig)
            Toast.makeText(requireContext(), getString(R.string.msg_file_loaded), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "Error parseando JSON: ${e.message}")
            Toast.makeText(requireContext(), getString(R.string.msg_error_json), Toast.LENGTH_SHORT).show()
        }
    }

    // EXPORTAR (Actualizado para los 8 puntos y nuevas variables)
    private fun exportConfigToUri(uri: Uri) {
        val config = currentConfigData ?: return

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val jsonObject = JSONObject()
                jsonObject.put("kMeter", config.kMeter)

                jsonObject.put("low_stability", config.lowStability)
                jsonObject.put("high_stability", config.highStability)

                jsonObject.put("lowTempUnscaled", config.lowTempUnscaled)
                jsonObject.put("highTempUnscaled", config.highTempUnscaled)
                jsonObject.put("lowTempCorrected", config.lowTempCorrected)
                jsonObject.put("highTempCorrected", config.highTempCorrected)

                // Curva de Error de Flujo (8 Puntos)
                jsonObject.put("fcQ1_error", config.fcQ1_error)
                jsonObject.put("fcQ2_error", config.fcQ2_error)
                jsonObject.put("fcP3_error", config.fcP3_error)
                jsonObject.put("fcP4_error", config.fcP4_error)
                jsonObject.put("fcP5_error", config.fcP5_error)
                jsonObject.put("fcP6_error", config.fcP6_error)
                jsonObject.put("fcP7_error", config.fcP7_error)
                jsonObject.put("fcP8_error", config.fcP8_error)
                jsonObject.put("fc10_00_error", config.fc10_00_error)
                jsonObject.put("fcP10_error", config.fcP10_error)
                jsonObject.put("fcQ3_error", config.fcQ3_error)
                jsonObject.put("fcQ4_error", config.fcQ4_error)

                val jsonString = jsonObject.toString(4)

                requireContext().contentResolver.openOutputStream(uri)?.use { outputStream ->
                    OutputStreamWriter(outputStream).use { writer ->
                        writer.write(jsonString)
                    }
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), getString(R.string.msg_export_success), Toast.LENGTH_SHORT).show()
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error exportando: ${e.message}")
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), getString(R.string.msg_export_error_write), Toast.LENGTH_LONG).show()
                }
            }
        }
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }


    private fun exportDatabaseToCSV() {
        // Necesitamos deshabilitar el botón mientras se procesa
        setButtonState(binding.btnExportCsv, false)

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // 1. Obtener la base de datos y leer todos los registros
                val database = com.example.nfc_reader_01.collection.AppDatabase.getDatabase(requireContext())
                val records = database.recordDao().getAllRecords()

                if (records.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "No hay registros en el lote para exportar", Toast.LENGTH_SHORT).show()
                        setButtonState(binding.btnExportCsv, true)
                    }
                    return@launch
                }

                // 2. Crear el archivo CSV en la caché
                val exportDir = java.io.File(requireContext().cacheDir, "exports")
                if (!exportDir.exists()) exportDir.mkdirs()

                val file = java.io.File(exportDir, "Lote_Configuraciones_${System.currentTimeMillis()}.csv")
                val writer = java.io.FileWriter(file)

                // 3. Escribir Encabezados (Actualizados con TODAS las columnas corregidas y los 8 puntos)
                writer.append("ID,Serial_Number,Firmware,K_Meter,Low_Stability,High_Stability,Temp_Raw_Low,Temp_Raw_High,Temp_Cal_Low,Temp_Cal_High,Q1_Error,Q2_Error,P3_Error,P4_Error,10.0L_Error,P5_Error,Q3_Error,Q4_Error,Device_Config_Date,Timestamp\n")

                // 4. Escribir Datos (Usando los nombres NUEVOS de la base de datos)
                for (record in records) {
                    writer.append("${record.id},")
                    writer.append("${record.serialNumber},")
                    writer.append("${record.firmwareVersion},")
                    writer.append("${record.kMeter},")
                    writer.append("${record.lowStability},")
                    writer.append("${record.highStability},")
                    writer.append("${record.tempRawLow},")
                    writer.append("${record.tempRawHigh},")
                    writer.append("${record.tempCalLow},")
                    writer.append("${record.tempCalHigh},")

                    // Curva de Error de Flujo (8 Puntos)
                    writer.append("${record.fcQ1Error},")
                    writer.append("${record.fcQ2Error},")
                    writer.append("${record.fcP3Error},")
                    writer.append("${record.fcP4Error},")
                    writer.append("${record.fcP5Error},")
                    writer.append("${record.fcP6Error},")
                    writer.append("${record.fcP7Error},")
                    writer.append("${record.fcP8Error},")
                    writer.append("${record.fc10LmError},")
                    writer.append("${record.fcP10Error},")
                    writer.append("${record.fcQ3Error},")
                    writer.append("${record.fcQ4Error},")

                    // Formatear las fechas para que sean legibles
                    val deviceDate = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(record.deviceLastConfigDate))
                    val exportDate = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(record.timestamp))

                    writer.append("$deviceDate,")
                    writer.append("$exportDate\n")
                }
                writer.flush()
                writer.close()

                // 5. Compartir el archivo
                withContext(Dispatchers.Main) {
                    shareCsvFile(file)
                    setButtonState(binding.btnExportCsv, true)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error exportando CSV", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Error exportando CSV", Toast.LENGTH_SHORT).show()
                    setButtonState(binding.btnExportCsv, true)
                }
            }
        }
    }

    private fun shareCsvFile(file: java.io.File) {
        try {
            // IMPORTANTE: Asegúrate de tener el provider configurado en AndroidManifest.xml
            val uri = androidx.core.content.FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.fileprovider",
                file
            )

            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            startActivity(android.content.Intent.createChooser(intent, "Exportar Lote de Configuraciones"))
        } catch (e: Exception) {
            Log.e(TAG, "Error compartiendo archivo", e)
            Toast.makeText(requireContext(), "Falta configurar FileProvider en el Manifest", Toast.LENGTH_LONG).show()
        }
    }

}

