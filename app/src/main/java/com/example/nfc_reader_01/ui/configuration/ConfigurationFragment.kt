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
import com.example.nfc_reader_01.utils.LogManager
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ConfigurationFragment : Fragment() {

    // Comandos
    companion object {
        const val COMMAND_READ_CONFIG = 0x03.toByte()
        const val COMMAND_WRITE_CONFIG = 0x04.toByte()
        const val COMMAND_FACTORY_RESET = 0x0A.toByte()
        // Este es el tamaño ÚTIL de los datos de configuración (96 bytes)
        const val CONFIG_BYTE_SIZE = 96
        const val FLOAT_FORMAT = "%.4f"
    }

    private val sharedNfcViewModel: SharedNfcViewModel by activityViewModels()

    private var _binding: FragmentConfigurationBinding? = null
    private val binding get() = _binding!!

    // Almacena la última configuración leída para poder modificarla y guardarla (CRÍTICO)
    private var currentConfigData: ConfigurationData? = null

    // Referencia al listener de la actividad (MainActivity)
    private var listener: NfcInteractionListener? = null

    // --- Ciclo de vida para el Listener ---

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is NfcInteractionListener) {
            listener = context
        } else {
            // Se usa Log.wtf para errores críticos de configuración de la Activity
            Log.wtf("ConfigFragment", "${context.toString()} debe implementar NfcInteractionListener")
        }
    }

    override fun onDetach() {
        super.onDetach()
        listener = null
    }

    // --- Vistas y Lógica ---

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

        binding.textViewLastConfigurationDate.setText("Sin datos de lectura")

        setupListeners()
        observeViewModel()
    }

    /**
     * Configura los listeners para los botones de acción (Leer, Guardar, Reset).
     */
    private fun setupListeners() {
        // --- 1. GUARDAR CONFIGURACIÓN (Escribir en TAG) ---
        binding.saveConfigButton.setOnClickListener {
            // CRÍTICO: Debemos tener la configuración base leída para no sobrescribir los campos no mostrados.
            val baseConfig = currentConfigData
            if (baseConfig == null) {
                // Mensaje mejorado: Acción requerida
                Toast.makeText(
                    requireContext(),
                    "ERROR: Debe LEER la configuración actual (0x03) antes de intentar GUARDAR.",
                    Toast.LENGTH_LONG
                ).show()
                return@setOnClickListener
            }

            // 1. Leer los 11 campos del UI y actualizar el objeto ConfigurationData.
            val newConfigData = updateConfigDataFromUI(baseConfig)
            if (newConfigData == null) {
                // El error de validación de campos ya se muestra en el campo de texto respectivo
                Toast.makeText(requireContext(), "Revise los campos con errores de formato (deben ser números válidos).", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            // 2. Serializar el objeto completo de 96 bytes (incluyendo campos no mostrados y timestamp actualizado).
            val configBytes = NfcDataParser.serializeConfigData(newConfigData)

            if (configBytes.size != CONFIG_BYTE_SIZE) {
                Log.e("ConfigFragment", "Error de serialización: Tamaño esperado $CONFIG_BYTE_SIZE, obtenido ${configBytes.size}")
                // Mensaje mejorado: Error interno de protocolo
                Toast.makeText(requireContext(), "Error interno: Falló la serialización del paquete de escritura.", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            // 3. Establecer los datos que se escribirán en el próximo escaneo
            sharedNfcViewModel.setConfigDataToWrite(configBytes)

            // 4. Solicitar a la Activity que ejecute el comando de escritura
            listener?.requestWriteConfig() ?: run {
                Toast.makeText(requireContext(), "Error: La actividad no está lista para NFC.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 5. Actualizar la fecha visualmente como "Pendiente de Guardar"
            val timestamp = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date())
            binding.textViewLastConfigurationDate.setText("PENDIENTE DE GUARDAR ($timestamp)")

            // Mensaje mejorado: Instrucción clara para el usuario
            // Toast.makeText(
            //    requireContext(),
            //    "Comando de ESCRITURA (0x${COMMAND_WRITE_CONFIG.toHexString()}) listo. ¡ACERQUE EL TAG AHORA para aplicar los cambios!",
            //    Toast.LENGTH_LONG
            //).show()
        }

        // --- 2. LEER CONFIGURACIÓN (Leer del TAG) ---
        binding.readConfigButton.setOnClickListener {
            // 1. Limpiar datos de escritura previos y solicitar el comando de lectura
            sharedNfcViewModel.setConfigDataToWrite(null)
            listener?.requestNextCommand(COMMAND_READ_CONFIG) ?: run {
                Toast.makeText(requireContext(), "Error: La actividad no está lista para NFC.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Mensaje mejorado: Instrucción clara para el usuario
            // Toast.makeText(
            //    requireContext(),
            //    "Comando de LECTURA (0x${COMMAND_READ_CONFIG.toHexString()}) listo. ¡ACERQUE EL TAG para cargar la configuración!",
            //    Toast.LENGTH_LONG
            //).show()
        }

        // --- 3. RESTABLECER CONFIGURACIÓN DE FÁBRICA ---
        binding.factoryResetButton.setOnClickListener {
            // 1. Limpiar datos de escritura (por si acaso) y solicitar el comando de reset
            sharedNfcViewModel.setConfigDataToWrite(null)
            listener?.requestNextCommand(COMMAND_FACTORY_RESET) ?: run {
                Toast.makeText(requireContext(), "Error: La actividad no está lista para NFC.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Mensaje mejorado: Advertencia y acción requerida
            // Toast.makeText(
            //    requireContext(),
            //    "Comando de RESET (0x${COMMAND_FACTORY_RESET.toHexString()}) listo. ¡ACERQUE EL TAG para RESTABLECER DE FÁBRICA!",
            //    Toast.LENGTH_LONG
            //).show()
        }
    }


    /**
     * Lee los 11 campos de texto flotantes del UI y crea un nuevo ConfigurationData
     * a partir de la base, actualizando solo los campos visibles.
     * @param baseConfig El ConfigurationData leído previamente del TAG.
     * @return El nuevo ConfigurationData con los campos actualizados, o null si hay un error de formato.
     */
    private fun updateConfigDataFromUI(baseConfig: ConfigurationData): ConfigurationData? {
        var updatedConfig = baseConfig
        var allValid = true

        // Tu lista de Triples está correcta, solo hay que usarla de forma limpia.
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
            // Si el campo está vacío, consideramos 0.0f por defecto si el TAG lo permite, o forzamos error.
            // Para configuración, es mejor forzar el error si no es válido.
            val floatValue = text.toFloatOrNull()
            if (floatValue == null) {
                field.error = "Debe ser un valor numérico válido"
                allValid = false
            } else {
                field.error = null
                updateAction(floatValue)
            }
        }

        return if (allValid) updatedConfig else null
    }

    /**
     * Muestra el ConfigurationData completo en los 11 campos disponibles del UI.
     * @param config El ConfigurationData ya parseado del TAG.
     */
    private fun displayConfigData(config: ConfigurationData) {

        // 1. Rellenar campos del UI
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

        // 2. Mostrar fecha de configuración
        val dateMillis = config.lastConfigurationDate.toLong() * 1000
        val date = Date(dateMillis)
        val dateFormatter = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
        val formattedDate = dateFormatter.format(date)
        binding.textViewLastConfigurationDate.setText(formattedDate)
    }


    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                sharedNfcViewModel.configurationResponseData.collect { bytes ->
                    if (bytes != null) {
                        // CRITERIO DE AJUSTE POR PADDING: El buffer recibido (e.g., 127 bytes)
                        // debe ser AL MENOS del tamaño de los datos útiles (96 bytes).
                        if (bytes.size >= CONFIG_BYTE_SIZE) {

                            // Extraemos SOLO los primeros 96 bytes que son los datos de configuración.
                            val usefulBytes = bytes.sliceArray(0 until CONFIG_BYTE_SIZE)

                            if (bytes.size != CONFIG_BYTE_SIZE) {
                                Log.i("ConfigFragment", "Buffer recibido con padding (${bytes.size} bytes). Procesando los primeros $CONFIG_BYTE_SIZE bytes.")
                            }

                            try {
                                // 1. Parsear los 96 bytes completos
                                val config = NfcDataParser.parseConfigData(usefulBytes)
                                // 2. Almacenar el objeto completo para futuras escrituras
                                currentConfigData = config
                                // 3. Mostrar solo los campos relevantes en la UI
                                displayConfigData(config)
                                // Mensaje mejorado: Éxito de la lectura
                                // Toast.makeText(requireContext(), "Configuración (0x83) LEÍDA y cargada en pantalla.", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Log.e("ConfigFragment", "Error al parsear datos 0x83: ${e.message}")
                                // Mensaje mejorado: Error de procesamiento
                                Toast.makeText(requireContext(), "ERROR: Falló datos Configuración (0x83).", Toast.LENGTH_LONG).show()
                            }
                        } else {
                            // Este caso ocurre si se reciben menos de 96 bytes (datos incompletos)
                            // Mensaje mejorado: Error de tamaño
                            Toast.makeText(
                                requireContext(),
                                "ERROR: Datos incompletos. Se recibieron ${bytes.size} bytes, se esperaban $CONFIG_BYTE_SIZE.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                        // Limpiar los datos después de consumirlos
                        sharedNfcViewModel.clearConfigurationResponseData()
                    }
                }
            }
        }
    }


    private fun Byte.toHexString() = String.format("%02X", this)

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
