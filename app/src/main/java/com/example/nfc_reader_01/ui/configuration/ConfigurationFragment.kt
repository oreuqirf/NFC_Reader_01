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

        const val CONFIG_BYTE_SIZE = 104
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

            setButtonState(it, false)
            sharedNfcViewModel.setConfigDataToWrite(configBytes)
            listener?.requestWriteConfig()
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

    // --- NUEVA FUNCIÓN AUXILIAR ---
    private fun createBlankConfiguration(): ConfigurationData {
        // Retorna un objeto con todo en 0
        return ConfigurationData(
            kMeter = 0f,
            low_stability = 0f,
            high_stability = 0f,
            lowTempUnscaled = 0f, highTempUnscaled = 0f,
            lowTempCorrected = 0f, highTempCorrected = 0f,
            fcQ1_flow = 0f, fcQ1_temperature = 0f, fcQ1_error = 0f,
            fcQ2_flow = 0f, fcQ2_temperature = 0f, fcQ2_error = 0f,
            fcQ0_35_flow = 0f, fcQ0_35_temperature = 0f, fcQ0_35_error = 0f,
            fcQ1_00_flow = 0f, fcQ1_00_temperature = 0f, fcQ1_00_error = 0f,
            fcQ10_00_flow = 0f, fcQ10_00_temperature = 0f, fcQ10_00_error = 0f,
            fcQ3_flow = 0f, fcQ3_temperature = 0f, fcQ3_error = 0f,
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
        // (Sin cambios, se encarga de rellenar los EditText desde el objeto)
        try {
            binding.editTextKMeter.setText(String.format(Locale.US, FLOAT_FORMAT, config.kMeter))

            binding.editTextLowStability.setText(String.format(Locale.US, FLOAT_FORMAT, config.low_stability))
            binding.editTextHighStability.setText(String.format(Locale.US, FLOAT_FORMAT, config.high_stability))

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
            binding.textViewLastConfigurationDate.text = dateFormatter.format(date)
        } catch (e: Exception) {
            Log.e(TAG, "Error mostrando datos: ${e.message}")
        }
    }


    private fun updateConfigDataFromUI(baseConfig: ConfigurationData): ConfigurationData? {
        var updatedConfig = baseConfig
        var allValid = true

        // 1. PROCESAMIENTO DE FLOATS (K Meter, Temperaturas, Tablas Q)
        val uiUpdates: List<Triple<TextInputEditText, Float, (Float) -> Unit>> = listOf(
            // Triple(binding.editTextKMeter, baseConfig.kMeter) { v -> updatedConfig = updatedConfig.copy(kMeter = v) }, <-- ELIMINADO EL DUPLICADO
            Triple(binding.editTextKMeter, baseConfig.kMeter) { v -> updatedConfig = updatedConfig.copy(kMeter = v) },

            Triple(binding.editTextLowStability, baseConfig.low_stability) { v -> updatedConfig = updatedConfig.copy(low_stability = v) },
            Triple(binding.editTextHighStability, baseConfig.high_stability) { v -> updatedConfig = updatedConfig.copy(high_stability = v) },

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


        // Bucle mágico que valida todo
        for ((field, _, updateAction) in uiUpdates) {
            val text = field.text?.toString()?.trim() ?: ""
            val cleanText = text.replace(",", ".")
            val floatValue = cleanText.toFloatOrNull()

            if (floatValue == null) {
                // DETECTIVE PARA EL LOGCAT: Te dirá el ID exacto del campo que falla
                val fieldName = resources.getResourceEntryName(field.id)
                android.util.Log.e("NFC_VALIDATION", "¡Fallo por texto inválido! Campo: $fieldName | Texto detectado: '$text'")

                field.error = getString(R.string.msg_invalid_value)
                allValid = false
            } else {
                // Validación extra
                if (field == binding.editTextLowStability && (floatValue < 0f || floatValue > 100f)) {
                    android.util.Log.e("NFC_VALIDATION", "Fallo: Estabilidad Baja fuera de rango ($floatValue)")
                    field.error = "0 - 100"
                    allValid = false
                } else if (field == binding.editTextHighStability && floatValue < 0f) {
                    android.util.Log.e("NFC_VALIDATION", "Fallo: Estabilidada Alta fuera de rango ($floatValue)")
                    field.error = "Inválido"
                    allValid = false
                } else {
                    field.error = null
                    updateAction(floatValue)
                }
            }
        }

        // 3. FINALIZAR
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
                            if (bytes.size >= CONFIG_BYTE_SIZE) {
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
        // (Sin cambios, parsea JSON a objeto y actualiza UI)
        try {
            val json = JSONObject(content)
            val newConfig = ConfigurationData(
                kMeter = json.optDouble("kMeter", 0.0).toFloat(),

                low_stability = json.optDouble("low_stability", 0.0).toFloat(),
                high_stability =json.optDouble("high_stability", 0.0).toFloat(),

                lowTempUnscaled = json.optDouble("lowTempUnscaled", 0.0).toFloat(),
                highTempUnscaled = json.optDouble("highTempUnscaled", 0.0).toFloat(),
                lowTempCorrected = json.optDouble("lowTempCorrected", 0.0).toFloat(),
                highTempCorrected = json.optDouble("highTempCorrected", 0.0).toFloat(),

                fcQ1_flow = json.optDouble("fcQ1_flow", 0.0).toFloat(),
                fcQ1_temperature = json.optDouble("fcQ1_temperature", 0.0).toFloat(),
                fcQ1_error = json.optDouble("fcQ1_error", 0.0).toFloat(),

                fcQ2_flow = json.optDouble("fcQ2_flow", 0.0).toFloat(),
                fcQ2_temperature = json.optDouble("fcQ2_temperature", 0.0).toFloat(),
                fcQ2_error = json.optDouble("fcQ2_error", 0.0).toFloat(),

                fcQ0_35_flow = json.optDouble("fcQ0_35_flow", 0.0).toFloat(),
                fcQ0_35_temperature = json.optDouble("fcQ0_35_temperature", 0.0).toFloat(),
                fcQ0_35_error = json.optDouble("fcQ0_35_error", 0.0).toFloat(),

                fcQ1_00_flow = json.optDouble("fcQ1_00_flow", 0.0).toFloat(),
                fcQ1_00_temperature = json.optDouble("fcQ1_00_temperature", 0.0).toFloat(),
                fcQ1_00_error = json.optDouble("fcQ1_00_error", 0.0).toFloat(),

                fcQ10_00_flow = json.optDouble("fcQ10_00_flow", 0.0).toFloat(),
                fcQ10_00_temperature = json.optDouble("fcQ10_00_temperature", 0.0).toFloat(),
                fcQ10_00_error = json.optDouble("fcQ10_00_error", 0.0).toFloat(),

                fcQ3_flow = json.optDouble("fcQ3_flow", 0.0).toFloat(),
                fcQ3_temperature = json.optDouble("fcQ3_temperature", 0.0).toFloat(),
                fcQ3_error = json.optDouble("fcQ3_error", 0.0).toFloat(),

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

    // EXPORTAR (Sin cambios, usa currentConfigData que ya fue actualizado por el listener del botón)
    private fun exportConfigToUri(uri: Uri) {
        val config = currentConfigData ?: return

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val jsonObject = JSONObject()
                jsonObject.put("kMeter", config.kMeter)

                jsonObject.put("stability", config.low_stability)
                jsonObject.put("stopTime", config.high_stability)

                jsonObject.put("lowTempUnscaled", config.lowTempUnscaled)
                jsonObject.put("highTempUnscaled", config.highTempUnscaled)
                jsonObject.put("lowTempCorrected", config.lowTempCorrected)
                jsonObject.put("highTempCorrected", config.highTempCorrected)

                jsonObject.put("fcQ1_flow", config.fcQ1_flow)
                jsonObject.put("fcQ1_temperature", config.fcQ1_temperature)
                jsonObject.put("fcQ1_error", config.fcQ1_error)

                jsonObject.put("fcQ2_flow", config.fcQ2_flow)
                jsonObject.put("fcQ2_temperature", config.fcQ2_temperature)
                jsonObject.put("fcQ2_error", config.fcQ2_error)

                jsonObject.put("fcQ0_35_flow", config.fcQ0_35_flow)
                jsonObject.put("fcQ0_35_temperature", config.fcQ0_35_temperature)
                jsonObject.put("fcQ0_35_error", config.fcQ0_35_error)

                jsonObject.put("fcQ1_00_flow", config.fcQ1_00_flow)
                jsonObject.put("fcQ1_00_temperature", config.fcQ1_00_temperature)
                jsonObject.put("fcQ1_00_error", config.fcQ1_00_error)

                jsonObject.put("fcQ10_00_flow", config.fcQ10_00_flow)
                jsonObject.put("fcQ10_00_temperature", config.fcQ10_00_temperature)
                jsonObject.put("fcQ10_00_error", config.fcQ10_00_error)

                jsonObject.put("fcQ3_flow", config.fcQ3_flow)
                jsonObject.put("fcQ3_temperature", config.fcQ3_temperature)
                jsonObject.put("fcQ3_error", config.fcQ3_error)

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

                // 3. Escribir Encabezados (Actualizados con TODAS las columnas)
                writer.append("ID,Serial_Number,Firmware,K_Meter,Acc_Skip,Temp_Raw_Low,Temp_Raw_High,Temp_Cal_Low,Temp_Cal_High,Q1_Error,Q2_Error,Q0.35_Error,Q1.00_Error,Q10.0L_Error,Q3_Error,Device_Config_Date,Timestamp\n")

                // 4. Escribir Datos (Usando los nombres NUEVOS de la base de datos)
                for (record in records) {
                    writer.append("${record.id},")
                    writer.append("${record.serialNumber},")
                    writer.append("${record.firmwareVersion},")
                    writer.append("${record.kMeter},")
                    writer.append("${record.low_stability},")
                    writer.append("${record.high_stability},")
                    writer.append("${record.tempRawLow},")
                    writer.append("${record.tempRawHigh},")
                    writer.append("${record.tempCalLow},")
                    writer.append("${record.tempCalHigh},")
                    writer.append("${record.fcQ1Error},")
                    writer.append("${record.fcQ2Error},")
                    writer.append("${record.fcQ035Error},")
                    writer.append("${record.fcQ100Error},")
                    writer.append("${record.fcQ10LmError},")
                    writer.append("${record.fcQ3Error},")

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

