package com.example.nfc_reader_01.ui.configuration

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.nfc_reader_01.ConfigurationData
import com.example.nfc_reader_01.SharedNfcViewModel
import com.example.nfc_reader_01.databinding.FragmentConfigurationBinding
import java.util.*
// Importar la función de utilidad de fecha desde donde esté definida
// Asumimos que la función formatEpochTimestamp está disponible globalmente o a través de una importación estática.
import com.example.nfc_reader_01.DateFormater.formatEpochTimestamp // Importación de ejemplo


class ConfigurationFragment : Fragment() {

    private var _binding: FragmentConfigurationBinding? = null
    private val binding get() = _binding!!

    private lateinit var sharedNfcViewModel: SharedNfcViewModel

    // Una referencia mutable a la configuración actual para facilitar las actualizaciones en la UI
    // Esta variable se inicializa a partir del ViewModel y se modifica localmente.
    private var currentConfig: ConfigurationData? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentConfigurationBinding.inflate(inflater, container, false)
        sharedNfcViewModel =
            ViewModelProvider(requireActivity()).get(SharedNfcViewModel::class.java)

        setupListeners()
        setupObservers()

        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    /**
     * Inicializa los observadores para reaccionar a los cambios en los datos del TAG
     * o en el estado de escritura.
     */
    private fun setupObservers() {
        // 1. Observador de Datos de Configuración (ConfigurationData)
        sharedNfcViewModel.configData.observe(viewLifecycleOwner) { config ->
            currentConfig = config
            if (config != null) {
                // Actualiza la UI (EditTexts y TextViews) con el objeto ConfigurationData.
                updateUIWithConfig(config)
            } else {
                // Muestra un estado de "No hay datos"
                clearUIFields()
            }
        }

        // 2. Observador del Estado de Escritura (para mostrar Toasts)
        sharedNfcViewModel.writeStatus.observe(viewLifecycleOwner) { status ->
            // CORRECCIÓN: Usar status?.isNotBlank() == true para manejar String?
            if (status?.isNotBlank() == true) {
                // Muestra el mensaje de estado de escritura (éxito, error, o preparando)
                Toast.makeText(context, status, Toast.LENGTH_SHORT).show()
                // Resetear el estado después de mostrarlo para evitar repeticiones
                sharedNfcViewModel.setWriteStatus(null) // Lo reseteamos a null o ""
            }
        }
    }

    /**
     * Inicializa los listeners para los botones.
     */
    private fun setupListeners() {
        // Listener para los campos de error: Usamos doAfterTextChanged para actualizar
        // la variable local 'currentConfig' inmediatamente, reflejando el estado de edición.
        setupInputListeners()

        binding.saveConfigButton.setOnClickListener {
            // Se asume que 'currentConfig' ha sido actualizado por los doAfterTextChanged listeners.
            val configToSave = currentConfig

            if (configToSave == null) {
                Toast.makeText(
                    context,
                    "Error: No hay configuración cargada para guardar.",
                    Toast.LENGTH_LONG
                ).show()
                return@setOnClickListener
            }

            // 1. Actualizar el LiveData del ViewModel con la configuración local (editada)
            sharedNfcViewModel.setNdefRecords(config = configToSave)

            // 2. Solicitar al ViewModel que prepare el mensaje de escritura (Comando 0x04)
            // y notifique a MainActivity.
            sharedNfcViewModel.requestWriteConfig()
            Toast.makeText(
                context,
                "Solicitud de escritura enviada. Aproxime el TAG para guardar.",
                Toast.LENGTH_LONG
            ).show()
        }


        binding.factoryResetButton.setOnClickListener {
            // 1. Crear el objeto ConfigurationData con valores de fábrica
            val defaultConfigData = ConfigurationData(
                kMeter = 100.0f,
                lowTempUnscaled = 0.0f, highTempUnscaled = 30.0f,
                lowTempCorrected = 0.0f, highTempCorrected = 30.0f,
                fcQ1_flow = 16.666f, fcQ1_error = 27.0f, fcQ1_temperature = 20.0f,
                fcQ2_flow = 26.666f, fcQ2_error = 25.0f, fcQ2_temperature = 20.0f,
                fcQ0_35_flow = 58.333f, fcQ0_35_error = 19.0f, fcQ0_35_temperature = 20.0f,
                fcQ1_00_flow = 166.666f, fcQ1_00_error = 9.0f, fcQ1_00_temperature = 20.0f,
                fcQ10_00_flow = 1666.666f, fcQ10_00_error = 0.0f, fcQ10_00_temperature = 20.0f,
                fcQ3_flow = 6944.444f, fcQ3_error = -4.0f, fcQ3_temperature = 20.0f,
                lastConfigurationDate = 0, // 0 es el valor para "Nunca configurado"
            )

            // 2. Actualizar el LiveData del ViewModel. Esto activará el observador y la UI.
            sharedNfcViewModel.setNdefRecords(config = defaultConfigData)

            Toast.makeText(
                context,
                "Valores de fábrica restaurados en memoria. Presione 'Guardar Configuración' para transferir al TAG.",
                Toast.LENGTH_LONG
            ).show()
        }

        // Listener para solicitar la lectura del bloque de configuración (0x03)
        binding.readConfigButton.setOnClickListener {
            val COMMAND_CONFIG_DATA: Byte = 0x03.toByte()
            sharedNfcViewModel.requestNextCommand(COMMAND_CONFIG_DATA)
            Toast.makeText(
                context,
                "Solicitando Bloque 0x03. Aproxime el TAG para leer.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    /**
     * Configura listeners para que cualquier cambio en los campos editables
     * se refleje inmediatamente en el objeto local 'currentConfig'.
     */
    private fun setupInputListeners() {
        // --- Helpers para obtener Float de forma segura ---
        fun String.toFloatOrZero(): Float = this.toFloatOrNull() ?: 0.0f

        // Función genérica para actualizar un campo Float del ConfigurationData
        fun updateConfig(update: (ConfigurationData) -> ConfigurationData) {
            currentConfig?.let {
                currentConfig = update(it)
            }
        }

        // --- KMeter ---
        binding.editTextKMeter.doAfterTextChanged { text ->
            updateConfig { it.copy(kMeter = text.toString().toFloatOrZero()) }
        }

        // --- Temp Raw/Cal ---
        binding.editTextTempRawLow.doAfterTextChanged { text ->
            updateConfig { it.copy(lowTempUnscaled = text.toString().toFloatOrZero()) }
        }
        binding.editTextTempRawHigh.doAfterTextChanged { text ->
            updateConfig { it.copy(highTempUnscaled = text.toString().toFloatOrZero()) }
        }
        binding.editTextTempCalLow.doAfterTextChanged { text ->
            updateConfig { it.copy(lowTempCorrected = text.toString().toFloatOrZero()) }
        }
        binding.editTextTempCalHigh.doAfterTextChanged { text ->
            updateConfig { it.copy(highTempCorrected = text.toString().toFloatOrZero()) }
        }

        // --- Flow Correction Errors ---
        binding.editTextFcqQ1Error.doAfterTextChanged { text ->
            updateConfig { it.copy(fcQ1_error = text.toString().toFloatOrZero()) }
        }
        binding.editTextFcqQ2Error.doAfterTextChanged { text ->
            updateConfig { it.copy(fcQ2_error = text.toString().toFloatOrZero()) }
        }
        binding.editTextFcq035Error.doAfterTextChanged { text ->
            updateConfig { it.copy(fcQ0_35_error = text.toString().toFloatOrZero()) }
        }
        binding.editTextFcq100Error.doAfterTextChanged { text ->
            updateConfig { it.copy(fcQ1_00_error = text.toString().toFloatOrZero()) }
        }
        binding.editTextFcq10LmError.doAfterTextChanged { text ->
            updateConfig { it.copy(fcQ10_00_error = text.toString().toFloatOrZero()) }
        }
        binding.editTextFcqQ3Error.doAfterTextChanged { text ->
            updateConfig { it.copy(fcQ3_error = text.toString().toFloatOrZero()) }
        }
    }

    /**
     * Actualiza todos los campos de la UI basándose en un objeto ConfigurationData.
     */
    private fun updateUIWithConfig(config: ConfigurationData) {
        // --- Función auxiliar para formatear la salida ---
        fun formatFlow(flow: Float): String = String.format(Locale.US, "%.3f L/h", flow * 0.3600f)
        fun formatTemp(temp: Float): String = String.format(Locale.US, "%.1f °C", temp)

        Toast.makeText(context, "Datos de Configuracion Actualizados", Toast.LENGTH_SHORT).show()

        // --- Parámetros Generales ---
        binding.editTextKMeter.setText(config.kMeter.toString())
        binding.editTextTempRawLow.setText(config.lowTempUnscaled.toString())
        binding.editTextTempRawHigh.setText(config.highTempUnscaled.toString())
        binding.editTextTempCalLow.setText(config.lowTempCorrected.toString())
        binding.editTextTempCalHigh.setText(config.highTempCorrected.toString())

        // --- Puntos de Corrección (Q1) ---
        binding.editTextFcqQ1Error.setText(config.fcQ1_error.toString()) // Editable
        binding.textViewFcqQ1Flow.text = formatFlow(config.fcQ1_flow)
        binding.textViewFcqQ1Temperature.text = formatTemp(config.fcQ1_temperature)

        // --- Puntos de Corrección (Q2) ---
        binding.editTextFcqQ2Error.setText(config.fcQ2_error.toString()) // Editable
        binding.textViewFcqQ2Flow.text = formatFlow(config.fcQ2_flow)
        binding.textViewFcqQ2Temperature.text = formatTemp(config.fcQ2_temperature)

        // --- Puntos de Corrección (Q0.35) ---
        binding.editTextFcq035Error.setText(config.fcQ0_35_error.toString()) // Editable
        binding.textViewFcq035Flow.text = formatFlow(config.fcQ0_35_flow)
        binding.textViewFcq035Temperature.text = formatTemp(config.fcQ0_35_temperature)

        // --- Puntos de Corrección (Q1.00) ---
        binding.editTextFcq100Error.setText(config.fcQ1_00_error.toString()) // Editable
        binding.textViewFcq100Flow.text = formatFlow(config.fcQ1_00_flow)
        binding.textViewFcq100Temperature.text = formatTemp(config.fcQ1_00_temperature)

        // --- Puntos de Corrección (Q10.00) ---
        binding.editTextFcq10LmError.setText(config.fcQ10_00_error.toString()) // Editable
        binding.textViewFcq10LmFlow.text = formatFlow(config.fcQ10_00_flow)
        binding.textViewFcq10LmTemperature.text = formatTemp(config.fcQ10_00_temperature)

        // --- Puntos de Corrección (Q3) ---
        binding.editTextFcqQ3Error.setText(config.fcQ3_error.toString()) // Editable
        binding.textViewFcqQ3Flow.text = formatFlow(config.fcQ3_flow)
        binding.textViewFcqQ3Temperature.text = formatTemp(config.fcQ3_temperature)

        // --- Last Configuration Date ---
        // CORRECCIÓN: Usamos setText() en lugar de la propiedad .text = para evitar el error de Editable.
        // Convertir el Int (Epoch seconds) a Long antes de formatear.
        binding.textViewLastConfigurationDate.setText(formatEpochTimestamp(config.lastConfigurationDate.toLong()))

    }

    /**
     * Limpia o establece un estado de "No hay datos" en todos los campos de la UI.
     */
    private fun clearUIFields() {
        val noDataText = "---"

        // Limpiar EditTexts
        binding.editTextKMeter.setText(noDataText)
        binding.editTextTempRawLow.setText(noDataText)
        binding.editTextTempRawHigh.setText(noDataText)
        binding.editTextTempCalLow.setText(noDataText)
        binding.editTextTempCalHigh.setText(noDataText)
        binding.editTextFcqQ1Error.setText(noDataText)
        binding.editTextFcqQ2Error.setText(noDataText)
        binding.editTextFcq035Error.setText(noDataText)
        binding.editTextFcq100Error.setText(noDataText)
        binding.editTextFcq10LmError.setText(noDataText)
        binding.editTextFcqQ3Error.setText(noDataText)

        // Limpiar TextViews auxiliares
        binding.textViewFcqQ1Flow.text = "--- L/h"
        binding.textViewFcqQ1Temperature.text = "--- °C"
        binding.textViewFcqQ2Flow.text = "--- L/h"
        binding.textViewFcqQ2Temperature.text = "--- °C"
        binding.textViewFcq035Flow.text = "--- L/h"
        binding.textViewFcq035Temperature.text = "--- °C"
        binding.textViewFcq100Flow.text = "--- L/h"
        binding.textViewFcq100Temperature.text = "--- °C"
        binding.textViewFcq10LmFlow.text = "--- L/h"
        binding.textViewFcq10LmTemperature.text = "--- °C"
        binding.textViewFcqQ3Flow.text = "--- L/h"
        // CORRECCIÓN: Usamos setText() en lugar de la propiedad .text =
        binding.textViewLastConfigurationDate.setText("--- ")
    }
}


