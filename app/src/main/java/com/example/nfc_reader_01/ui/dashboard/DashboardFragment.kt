package com.example.nfc_reader_01.ui.dashboard

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
import com.example.nfc_reader_01.SharedNfcViewModel
import com.example.nfc_reader_01.data.NfcDataParser
import com.example.nfc_reader_01.databinding.FragmentDashboardBinding
import com.example.nfc_reader_01.ui.configuration.ConfigurationFragment.Companion.CONFIG_BYTE_SIZE
import com.example.nfc_reader_01.utils.LogManager
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.ExperimentalStdlibApi

/**
 * Fragmento para mostrar los datos leídos de la etiqueta NFC (respuesta 0x8X) en campos estructurados.
 */
@OptIn(ExperimentalStdlibApi::class)
class DashboardFragment : Fragment() {

    private val TAG = "DashboardFragment"
    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    private val sharedViewModel: SharedNfcViewModel by activityViewModels()

    // Códigos de comando
    private val CMD_READ_IDENTITY: Byte = 0x01
    private val CMD_READ_PROCESS: Byte = 0x02
    private val CMD_READ_CONFIG: Byte = 0x03
    private val CMD_READ_ENGINEERING: Byte = 0x05

    // --- TAMAÑOS ÚTILES ESPERADOS DEL PAYLOAD ---
    private val IDENTITY_PAYLOAD_SIZE = 12 // 3 Ints (12 bytes)
    private val PROCESS_PAYLOAD_SIZE = 36  // 9 Ints (36 bytes)

    // CORRECCIÓN: Actualizado a 44 bytes para incluir el rakFrameCounter (11 Ints * 4)
    private val ENGINEERING_PAYLOAD_SIZE = 44
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

    private fun setupListeners() {
        binding.requestIdentityButton.setOnClickListener {
            sharedViewModel.sendCommand(CMD_READ_IDENTITY)
            sharedViewModel.setUiMessage("Comando 0x${CMD_READ_IDENTITY.toHexString()} (Identidad) preparado. Acerque el TAG.")
        }

        binding.requestProcessButton.setOnClickListener {
            sharedViewModel.sendCommand(CMD_READ_PROCESS)
            sharedViewModel.setUiMessage("Comando 0x${CMD_READ_PROCESS.toHexString()} (Proceso) preparado. Acerque el TAG.")
        }

        binding.requestConfigButton.setOnClickListener {
            sharedViewModel.sendCommand(CMD_READ_CONFIG)
            sharedViewModel.setUiMessage("Comando 0x${CMD_READ_CONFIG.toHexString()} (Configuración) preparado. Acerque el TAG.")
        }

        binding.requestEngineeringButton.setOnClickListener {
            sharedViewModel.sendCommand(CMD_READ_ENGINEERING)
            sharedViewModel.setUiMessage("Comando 0x${CMD_READ_ENGINEERING.toHexString()} (Ingeniería) preparado. Acerque el TAG.")
        }
    }

    private fun setupObservers() {
        // Observa mensaje de UI
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                sharedViewModel.uiMessage.collect { message ->
                    try {
                        // CORRECCIÓN: Usar setText() para asignar el String al TextInputEditText
                        binding.textMessage.setText(message)
                    } catch (e: Exception) {
                        Log.d(TAG, "Estado NFC: $message")
                    }
                }
            }
        }

        // Observador 0x81 (Identidad)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                sharedViewModel.identityResponseData.collect { data ->
                    if (data != null && data.isNotEmpty()) {
                        if (data.size >= IDENTITY_PAYLOAD_SIZE) {
                            val usefulBytes = data.sliceArray(0 until IDENTITY_PAYLOAD_SIZE)
                            parseIdentityData(usefulBytes)
                            sharedViewModel.setUiMessage("Respuesta 0x81: Datos de Identidad cargados con éxito.")
                        } else {
                            sharedViewModel.setUiMessage("ERROR 0x81: Respuesta incompleta (${data.size}/$IDENTITY_PAYLOAD_SIZE).")
                        }
                    }
                }
            }
        }

        // Observador 0x82 (Proceso)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                sharedViewModel.processResponseData.collect { data ->
                    if (data != null && data.isNotEmpty()) {
                        if (data.size >= PROCESS_PAYLOAD_SIZE) {
                            val usefulBytes = data.sliceArray(0 until PROCESS_PAYLOAD_SIZE)
                            parseProcessData(usefulBytes)
                            sharedViewModel.setUiMessage("Respuesta 0x82: Datos de Proceso actualizados.")
                        } else {
                            sharedViewModel.setUiMessage("ERROR 0x82: Respuesta incompleta (${data.size}/$PROCESS_PAYLOAD_SIZE).")
                        }
                    }
                }
            }
        }

        // Observador 0x83 (Configuración)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                sharedViewModel.configurationResponseData.collect { data ->
                    if (data != null && data.isNotEmpty()) {
                        if (data.size >= CONFIG_BYTE_SIZE) {
                            val usefulBytes = data.sliceArray(0 until CONFIG_BYTE_SIZE)
                            parseConfigData(usefulBytes)
                            sharedViewModel.setUiMessage("Respuesta 0x83: Datos de Configuración cargados correctamente.")
                            sharedViewModel.clearConfigurationResponseData()
                        } else {
                            sharedViewModel.setUiMessage("ERROR 0x83: Respuesta incompleta (${data.size}/$CONFIG_BYTE_SIZE).")
                            sharedViewModel.clearConfigurationResponseData()
                        }
                    }
                }
            }
        }

        // Observador 0x85 (Ingeniería)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                sharedViewModel.engineeringResponseData.collect { data ->
                    if (data != null && data.isNotEmpty()) {
                        // Usamos el nuevo tamaño corregido (44 bytes)
                        if (data.size >= ENGINEERING_PAYLOAD_SIZE) {
                            val usefulBytes = data.sliceArray(0 until ENGINEERING_PAYLOAD_SIZE)
                            parseEngineeringData(usefulBytes)
                            sharedViewModel.setUiMessage("Respuesta 0x85: Datos de Ingeniería cargados.")
                        } else {
                            sharedViewModel.setUiMessage("ERROR 0x85: Respuesta incompleta (${data.size}/$ENGINEERING_PAYLOAD_SIZE).")
                        }
                    }
                }
            }
        }

        // Observador de Toast
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                sharedViewModel.writeStatus.collect { status ->
                    if (status != null) {
                        Toast.makeText(context, status, Toast.LENGTH_LONG).show()
                        sharedViewModel.setUiMessage(status)
                    }
                }
            }
        }
    }

    private fun parseIdentityData(data: ByteArray) {
        try {
            val identity = NfcDataParser.parseIdentityData(data)

            val highPart = (identity.deviceId shr 16) and 0xFFFF
            val lowPart = identity.deviceId and 0xFFFF
            val formattedSerial = String.format(Locale.US, "%05d-%05d", highPart, lowPart)

            binding.editTextSerialNumber.setText(formattedSerial)
            binding.editTextFirmwareVersion.setText(identity.firmwareVersion)
            binding.editTextLastConfigDate.setText(identity.lastConfigurationDate)

        } catch (e: Exception) {
            Log.e(TAG, "Error parseIdentityData: ${e.message}")
            binding.editTextSerialNumber.setText("ERROR PARSEO")
        }
        clearProcessValueFields()
        clearFlowPeriodFields()
        clearEngineeringFields()
    }

    private fun parseProcessData(data: ByteArray) {
        try {
            val process = NfcDataParser.parseProcessData(data)

            val floatVolume = process.volume / 1000.0f
            val floatFlowRate = process.flowRate.toFloat()
            val floatTemperature = process.temperature.toFloat()
            val floatBattery = process.battery.toFloat()

            binding.editTextVolume.setText(String.format(Locale.US, "%.3f m³", floatVolume))
            binding.editTextFlow.setText(String.format(Locale.US, "%.1f L/h", floatFlowRate))
            binding.editTextTemperature.setText(String.format(Locale.US, "%.1f °C", floatTemperature))
            binding.editTextBattery.setText(String.format(Locale.US, "%.1f %%", floatBattery))
            binding.editTextStatus.setText("0x${process.statusFlags.toHexString()}")

            binding.editTextDirectFlowPeriod.setText("${process.directFlowPeriod} sec")
            binding.editTextReverseFlowPeriod.setText("${process.reverseFlowPeriod} sec")
            binding.editTextNoFlowPeriod.setText("${process.noFlowPeriod} sec")
            binding.editTextLeakagePeriod.setText("${process.leakageFlowPeriod} sec")

        } catch (e: Exception) {
            Log.e(TAG, "Error parseProcessData: ${e.message}")
            binding.editTextVolume.setText("ERROR PARSEO")
        }
        clearIdentityFields()
        clearEngineeringFields()
    }

    private fun parseConfigData(data: ByteArray) {
        try {
            // Lógica de visualización de configuración (si fuera necesario en este fragmento)
        } catch (e: Exception) {
            Log.e(TAG, "Error parseConfigData: ${e.message}")
        }
        clearIdentityFields(keepLastConfigDate = false)
        clearProcessValueFields()
        clearFlowPeriodFields()
        clearEngineeringFields()
    }

    private fun parseEngineeringData(data: ByteArray) {
        try {
            val engineering = NfcDataParser.parseEngineeringData(data)

            // CORRECCIÓN: Usar .toFloat() en lugar de Float.fromBits()
            // Y aplicar factores de escala: Volumen / 1000, Temperatura / 10
            val floatVolL = engineering.volumeLiters.toFloat() / 1000.0f
            val floatVolLU = engineering.volumeLitersUncal.toFloat() / 1000.0f
            val floatTempU = engineering.temperatureUncal.toFloat() / 10.0f

            val floatFlowU = engineering.flowUncal.toFloat()
            val floatTtof = engineering.ttof.toFloat()
            val floatDtof = engineering.dtof.toFloat()
            val floatStdDev = engineering.stdDev.toFloat()
            val floatChipTemp = engineering.chipTemperature.toFloat()
            val floatLux = engineering.lux.toFloat()

            binding.editTextVolumeLiters.setText(String.format(Locale.US, "%.3f L", floatVolL)) // Ajustado a 3 decimales
            binding.editTextVolumeLitersUncal.setText(String.format(Locale.US, "%.3f L", floatVolLU)) // Ajustado a 3 decimales
            binding.editTextTemperatureUncal.setText(String.format(Locale.US, "%.2f °C", floatTempU))

            binding.editTextFlowUncal.setText(String.format(Locale.US, "%.2f L/h", floatFlowU))
            binding.editTextTtof.setText(String.format(Locale.US, "%.2f usec", floatTtof))
            binding.editTextDtof.setText(String.format(Locale.US, "%.2f psec", floatDtof))
            binding.editTextStdDev.setText(String.format(Locale.US, "%.2f psec", floatStdDev))
            binding.editTextTime.setText("${engineering.time} sec")
            binding.editTextChipTemperature.setText(String.format(Locale.US, "%.2f °C", floatChipTemp))
            binding.editTextLux.setText(String.format(Locale.US, "%.2f mV", floatLux))

            // Nuevo campo
            binding.editTextRakFrameCounter.setText("${engineering.rakFrameCounter}")

        } catch (e: Exception) {
            Log.e(TAG, "Error parseEngineeringData: ${e.message}")
            // Muestra un error más descriptivo en la UI si falla el parseo por tamaño
            val msg = if (e is IllegalArgumentException) "Error Tamaño" else "Error Parseo"
            binding.editTextVolumeLiters.setText(msg)
        }

        clearIdentityFields()
        clearProcessValueFields()
        clearFlowPeriodFields()
    }

    // --- Funciones de Limpieza ---
    private fun clearIdentityFields(keepLastConfigDate: Boolean = false) {
        binding.editTextSerialNumber.setText("")
        binding.editTextFirmwareVersion.setText("")
        if (!keepLastConfigDate) binding.editTextLastConfigDate.setText("")
    }

    private fun clearProcessValueFields() {
        binding.editTextVolume.setText("")
        binding.editTextFlow.setText("")
        binding.editTextTemperature.setText("")
        binding.editTextBattery.setText("")
        binding.editTextStatus.setText("")
    }

    private fun clearFlowPeriodFields() {
        binding.editTextDirectFlowPeriod.setText("")
        binding.editTextReverseFlowPeriod.setText("")
        binding.editTextNoFlowPeriod.setText("")
        binding.editTextLeakagePeriod.setText("")
    }

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
}

// Extensiones útiles
fun ByteArray.toHexString() = joinToString(separator = " ") { String.format("%02X", it) }
fun Byte.toHexString() = String.format("%02X", this)
