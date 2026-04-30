package com.example.nfc_reader_01.ui.dashboard

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.nfc_reader_01.SharedNfcViewModel
import com.example.nfc_reader_01.data.NfcDataParser
import com.example.nfc_reader_01.databinding.FragmentDashboardBinding
import com.example.nfc_reader_01.NfcState
import com.example.nfc_reader_01.R
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.ExperimentalStdlibApi

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

    // Tamaños MÍNIMOS esperados
    private val IDENTITY_MIN_SIZE = 12
    private val PROCESS_MIN_SIZE = 36
    private val ENGINEERING_MIN_SIZE = 48
    private val CONFIG_MIN_SIZE = 96

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupListeners()
        setupFlowToggles()
        setupObservers()
    }

    // --- Lógica de conmutación L/h <-> L/min ---
    private fun setupFlowToggles() {
        val flowFields = listOf(
            binding.editTextFlow,
            binding.editTextFlowUncal,
            binding.editTextLastTripFlow
        )

        for (field in flowFields) {
            field.setOnClickListener { view ->
                toggleFlowUnit(view as TextView)
            }
        }
    }

    private fun toggleFlowUnit(textView: TextView) {
        val baseValueLph = textView.tag as? Float ?: return
        val currentText = textView.text.toString()

        if (currentText.contains("L/h")) {
            val valueLpm = baseValueLph / 60.0f
            textView.text = String.format(Locale.US, "%.3f L/min", valueLpm)
        } else {
            textView.text = String.format(Locale.US, "%.2f L/h", baseValueLph)
        }
    }

    // --- GESTIÓN DE ESTADO DE BOTONES ---

    private fun setButtonState(button: View, isEnabled: Boolean) {
        if (button !is MaterialButton) return
        button.isEnabled = isEnabled
        val primaryColor = ContextCompat.getColor(requireContext(), R.color.purple_500)
        val disabledColor = Color.GRAY
        button.backgroundTintList = ColorStateList.valueOf(if (isEnabled) primaryColor else disabledColor)
    }

    private fun enableAllButtons() {
        setButtonState(binding.requestIdentityButton, true)
        setButtonState(binding.requestProcessButton, true)
        setButtonState(binding.requestEngineeringButton, true)
        // Eliminado: setButtonState(binding.requestConfigButton, true)
    }

    private fun setupListeners() {
        binding.requestIdentityButton.setOnClickListener {
            setButtonState(it, false)
            sharedViewModel.sendCommand(CMD_READ_IDENTITY)
            sharedViewModel.setUiMessage(getString(R.string.status_scan_pending, CMD_READ_IDENTITY.toHexString()))
        }

        binding.requestProcessButton.setOnClickListener {
            setButtonState(it, false)
            sharedViewModel.sendCommand(CMD_READ_PROCESS)
            sharedViewModel.setUiMessage(getString(R.string.status_scan_pending, CMD_READ_PROCESS.toHexString()))
        }

        binding.requestEngineeringButton.setOnClickListener {
            setButtonState(it, false)
            sharedViewModel.sendCommand(CMD_READ_ENGINEERING)
            sharedViewModel.setUiMessage(getString(R.string.status_scan_pending, CMD_READ_ENGINEERING.toHexString()))
        }

        // Eliminado el Listener de requestConfigButton
    }

    private fun setupObservers() {
        // Mensaje UI
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                sharedViewModel.uiMessage.collect { message ->
                    try {
                        binding.textMessage.setText(message)
                    } catch (_: Exception) { }
                }
            }
        }

        // 0x01 Identidad
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                sharedViewModel.identityResponseData.collect { data ->
                    if (data != null && data.isNotEmpty()) {
                        enableAllButtons()
                        if (data.size >= IDENTITY_MIN_SIZE) {
                            parseIdentityData(data)
                            sharedViewModel.setUiMessage(getString(R.string.msg_read_success_identity))
                        } else {
                            sharedViewModel.setUiMessage("ERROR 0x01: Tamaño ${data.size} < $IDENTITY_MIN_SIZE")
                        }
                    }
                }
            }
        }

        // 0x02 Proceso
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                sharedViewModel.processResponseData.collect { data ->
                    if (data != null && data.isNotEmpty()) {
                        enableAllButtons()
                        if (data.size >= PROCESS_MIN_SIZE) {
                            parseProcessData(data)
                            sharedViewModel.setUiMessage(getString(R.string.msg_read_success_process))
                        } else {
                            sharedViewModel.setUiMessage("ERROR 0x02: Tamaño ${data.size} < $PROCESS_MIN_SIZE")
                        }
                    }
                }
            }
        }

        // 0x03 Configuración (Mantenemos el observer por si se lee desde otro lado, pero no se activa por botón aquí)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                sharedViewModel.configurationResponseData.collect { data ->
                    if (data != null && data.isNotEmpty()) {
                        enableAllButtons()
                        if (data.size >= CONFIG_MIN_SIZE) {
                            parseConfigData(data)
                            sharedViewModel.setUiMessage(getString(R.string.msg_read_success_config))
                            sharedViewModel.clearConfigurationResponseData()
                        } else {
                            sharedViewModel.setUiMessage("ERROR 0x03: Tamaño ${data.size} < $CONFIG_MIN_SIZE")
                            sharedViewModel.clearConfigurationResponseData()
                        }
                    }
                }
            }
        }

        // 0x05 Ingeniería
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                sharedViewModel.engineeringResponseData.collect { data ->
                    if (data != null && data.isNotEmpty()) {
                        enableAllButtons()
                        if (data.size >= ENGINEERING_MIN_SIZE) {
                            parseEngineeringData(data)
                            sharedViewModel.setUiMessage(getString(R.string.msg_read_success_engineering) + " (${data.size} B)")
                        } else {
                            sharedViewModel.setUiMessage("ERROR 0x05: Tamaño ${data.size} < $ENGINEERING_MIN_SIZE")
                        }
                    }
                }
            }
        }

        // Estado General
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                sharedViewModel.writeStatus.collect { state ->
                    if (state !is NfcState.Loading && state !is NfcState.Idle) {
                        enableAllButtons()
                    }
                    when (state) {
                        is NfcState.Success -> {
                            Toast.makeText(context, getString(R.string.msg_command_success), Toast.LENGTH_SHORT).show()
                        }
                        is NfcState.Error -> {
                            enableAllButtons()
                            Toast.makeText(context, state.errorMessage, Toast.LENGTH_LONG).show()
                            sharedViewModel.setUiMessage(state.errorMessage)
                        }
                        is NfcState.Loading -> { }
                        is NfcState.Idle -> { }
                    }
                }
            }
        }
    }

    // --- PARSERS ---

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
            binding.editTextSerialNumber.setText("ERROR PARSE")
        }
        clearProcessValueFields()
        clearFlowPeriodFields()
        clearEngineeringFields()
    }

    private fun parseProcessData(data: ByteArray) {
        try {
            val process = NfcDataParser.parseProcessData(data)
            val floatVolume = process.volume / 1000.0f
            val floatFlowRate = process.flowRate.toFloat() / 100.0f
            val floatTemperature = process.temperature.toFloat() / 10.0f
            val floatBattery = process.battery.toFloat()

            binding.editTextVolume.setText(String.format(Locale.US, "%.3f m³", floatVolume))

            binding.editTextFlow.tag = floatFlowRate
            binding.editTextFlow.setText(String.format(Locale.US, "%.2f L/h", floatFlowRate))

            binding.editTextTemperature.setText(String.format(Locale.US, "%.1f °C", floatTemperature))
            binding.editTextBattery.setText(String.format(Locale.US, "%.1f %%", floatBattery))
            binding.editTextStatus.setText("0x${process.statusFlags.toHexString()}")

            binding.editTextDirectFlowPeriod.setText("${process.directFlowPeriod} m")
            binding.editTextReverseFlowPeriod.setText("${process.reverseFlowPeriod} m")
            binding.editTextNoFlowPeriod.setText("${process.noFlowPeriod} m")
            binding.editTextLeakagePeriod.setText("${process.leakageFlowPeriod} m")

        } catch (e: Exception) {
            Log.e(TAG, "Error parseProcessData: ${e.message}")
            binding.editTextVolume.setText("ERROR PARSE")
        }
        clearIdentityFields()
        clearEngineeringFields()
    }

    private fun parseConfigData(data: ByteArray) {
        try {
            NfcDataParser.parseConfigData(data)
        } catch (e: Exception) {
            Log.e(TAG, "Config parse check failed: ${e.message}")
        }
        clearIdentityFields(keepLastConfigDate = false)
        clearProcessValueFields()
        clearFlowPeriodFields()
        clearEngineeringFields()
    }

    private fun parseEngineeringData(data: ByteArray) {
        try {
            val engineering = NfcDataParser.parseEngineeringData(data)

            val floatVolL = engineering.volumeLiters.toFloat() / 1000.0f
            val floatVolLU = engineering.volumeLitersUncal.toFloat() / 1000.0f
            val floatTempU = engineering.temperatureUncal.toFloat() / 10.0f
            val floatFlowU = engineering.flowUncal.toFloat() / 100.0f

            val floatTtof = engineering.ttof.toFloat() / 1000.0f
            val floatDtof = engineering.dtof.toFloat() / 1000.0f
            val floatStdDev = engineering.stdDev.toFloat()

            val floatLuxThreshold = engineering.lux_threshold.toFloat()
            val floatLux = engineering.lux.toFloat()
            val floatLastTripFlow = engineering.lastTripFlow.toFloat() / 100.0f
            val floatTempCal = engineering.temperature.toFloat() / 10.0f

            binding.editTextVolumeLiters.setText(String.format(Locale.US, "%.3f L", floatVolL))
            binding.editTextVolumeLitersUncal.setText(String.format(Locale.US, "%.3f L", floatVolLU))
            binding.editTextTemperatureUncal.setText(String.format(Locale.US, "%.2f °C", floatTempU))

            binding.editTextFlowUncal.tag = floatFlowU
            binding.editTextFlowUncal.setText(String.format(Locale.US, "%.2f L/h", floatFlowU))

            binding.editTextTtof.setText(String.format(Locale.US, "%.3f us", floatTtof))
            binding.editTextDtof.setText(String.format(Locale.US, "%.3f ps", floatDtof))
            binding.editTextStdDev.setText(String.format(Locale.US, "%.1f ps", floatStdDev))
            binding.editTextTime.setText("${engineering.time} s")

            binding.editTextTempCalibrated.setText(String.format(Locale.US, "%.2f °C", floatTempCal))
            binding.editTextLuxThreshold.setText(String.format(Locale.US, "%.0f", floatLuxThreshold))

            binding.editTextLux.setText(String.format(Locale.US, "%.0f", floatLux))
            binding.editTextRakFrameCounter.setText("${engineering.rakFrameCounter}")

            binding.editTextLastTripFlow.tag = floatLastTripFlow
            binding.editTextLastTripFlow.setText(String.format(Locale.US, "%.2f L/h", floatLastTripFlow))

        } catch (e: Exception) {
            Log.e(TAG, "Error parseEngineeringData: ${e.message}")
            binding.editTextVolumeLiters.setText("ERROR PARSE")
        }

        clearIdentityFields()
        clearProcessValueFields()
        clearFlowPeriodFields()
    }

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
        binding.editTextFlow.tag = null
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
        binding.editTextTempCalibrated.setText("")
        binding.editTextLuxThreshold.setText("")
        binding.editTextLux.setText("")
        binding.editTextRakFrameCounter.setText("")
        binding.editTextLastTripFlow.setText("")
        binding.editTextFlowUncal.tag = null
        binding.editTextLastTripFlow.tag = null
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

fun ByteArray.toHexString() = joinToString(" ") { "%02X".format(it) }
fun Byte.toHexString() = String.format("%02X", this.toInt() and 0xFF)
fun Int.toHexString() = String.format("%04X", this)
