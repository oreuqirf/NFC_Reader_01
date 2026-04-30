package com.example.nfc_reader_01.ui.calibration

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.nfc_reader_01.NfcInteractionListener
import com.example.nfc_reader_01.NfcState
import com.example.nfc_reader_01.R
import com.example.nfc_reader_01.SharedNfcViewModel
import com.example.nfc_reader_01.data.NfcDataParser
import com.example.nfc_reader_01.databinding.FragmentCalibrationBinding
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import kotlin.ExperimentalStdlibApi

import kotlinx.coroutines.delay

@OptIn(ExperimentalStdlibApi::class)
class CalibrationFragment : Fragment() {

    companion object {
        private const val TAG = "CalibrationFragment"
        private const val MIN_ENGINEERING_PAYLOAD_SIZE = 48

        // Comandos
        private const val CMD_READ_ENGINEERING: Byte = 0x05
        private const val CMD_CALIBRATE_FLOW: Byte = 0x13
        private const val CMD_RESET_VOLUME: Byte = 0x11

        // Índices
        private const val IDX_TEMP = 0
        private const val IDX_FLOW_10L = 1
        private const val IDX_FLOW_1L = 2
        private const val IDX_FLOW_035L = 3
        private const val IDX_FLOW_Q2 = 4
        private const val IDX_FLOW_Q1 = 5
        private const val IDX_FLOW_Q3 = 6

        // --- NUEVO: ESTADOS DEL BOTÓN RESET ---
        private const val STATE_IDLE = 0    // Rojo (Normal)
        private const val STATE_PENDING = 1 // Gris (Esperando acercar)
        private const val STATE_SUCCESS = 2 // Verde (Éxito)
    }

    private var _binding: FragmentCalibrationBinding? = null
    private val binding get() = _binding!!

    private val sharedViewModel: SharedNfcViewModel by activityViewModels()
    private var listener: NfcInteractionListener? = null

    private var detectedCalibrationType: Byte = 0

    // SEMÁFORO DE SEGURIDAD (Para calibración principal)
    private var isTransactionLocked = false
    private var waitingForTransferResult = false

    // --- NUEVO: Estado del botón Reset ---
    private var resetVolumeState = STATE_IDLE

    private var isManualMode = false
    private var dynamicManualOptions: List<Pair<String, Int>> = emptyList()
    private var selectedManualId: Int? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is NfcInteractionListener) {
            listener = context
        } else {
            Log.e(TAG, "$context debe implementar NfcInteractionListener")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCalibrationBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.textVersion.text = com.example.nfc_reader_01.BuildConfig.VERSION_NAME

        setupDropdownAdapter()
        setManualMode(false)
        showStep1()
        setupListeners()
        setupObservers()
        setupLanguageButtons()
    }

    // ... (setupDropdownAdapter, setupLanguageButtons, setAppLocale se mantienen IGUAL) ...

    private fun setupDropdownAdapter() {
        val orderedIds = listOf(IDX_TEMP, IDX_FLOW_Q1, IDX_FLOW_Q2, IDX_FLOW_035L, IDX_FLOW_1L, IDX_FLOW_10L, IDX_FLOW_Q3)
        dynamicManualOptions = orderedIds.map { id -> Pair(getCalibrationTypeName(id), id) }
        val optionNames = dynamicManualOptions.map { it.first }
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, optionNames)
        binding.autoCompleteFlowSelector.setAdapter(adapter)
        binding.autoCompleteFlowSelector.setOnItemClickListener { _, _, position, _ ->
            selectedManualId = dynamicManualOptions[position].second
            binding.inputLayoutManualFlow.error = null
        }
    }

    private fun setupLanguageButtons() {
        try {
            binding.btnLangEs.setOnClickListener { setAppLocale("es") }
            binding.btnLangEn.setOnClickListener { setAppLocale("en") }
            binding.btnLangZh.setOnClickListener { setAppLocale("zh-CN") }
        } catch (e: Exception) { Log.e(TAG, "Error idiomas: ${e.message}") }
    }

    private fun setAppLocale(languageCode: String) {
        val appLocale: LocaleListCompat = LocaleListCompat.forLanguageTags(languageCode)
        AppCompatDelegate.setApplicationLocales(appLocale)
    }

    private fun setManualMode(enableManual: Boolean) {
        isManualMode = enableManual
        val activeColor = ContextCompat.getColor(requireContext(), R.color.purple_500)
        val inactiveColor = Color.GRAY
        showStep1()
        if (isManualMode) {
            binding.inputLayoutManualFlow.visibility = View.VISIBLE
            binding.btnReadMaster.text = getString(R.string.btn_continue_manual)
            binding.btnReadMaster.icon = null
            binding.btnModeManual.backgroundTintList = ColorStateList.valueOf(activeColor)
            binding.btnModeManual.setTextColor(Color.WHITE)
            binding.btnModeAuto.backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
            binding.btnModeAuto.setTextColor(inactiveColor)
            binding.textDetectedFlowType.text = "Manual Mode"
            binding.autoCompleteFlowSelector.text.clear()
            selectedManualId = null
        } else {
            binding.inputLayoutManualFlow.visibility = View.GONE
            binding.btnReadMaster.text = getString(R.string.btn_read_pattern)
            binding.btnModeAuto.backgroundTintList = ColorStateList.valueOf(activeColor)
            binding.btnModeAuto.setTextColor(Color.WHITE)
            binding.btnModeManual.backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
            binding.btnModeManual.setTextColor(inactiveColor)
            binding.textDetectedFlowType.text = "-"
        }
    }

    private fun showStep1() {
        binding.layoutStep1.visibility = View.VISIBLE
        binding.layoutStep2.visibility = View.GONE
        binding.layoutSuccess.visibility = View.GONE
        binding.editTextVolPatron.setText("")
        binding.editTextTempPatron.setText("")
        binding.autoCompleteFlowSelector.setText("")
        selectedManualId = null
        setButtonState(binding.btnReadMaster, true)

        waitingForTransferResult = false
        isTransactionLocked = false

        // --- NUEVO: Resetear estado del botón de volumen al volver al inicio ---
        updateResetButtonUI(STATE_IDLE)
    }

    private fun showStep2() {
        binding.layoutStep1.visibility = View.GONE
        binding.layoutStep2.visibility = View.VISIBLE
        binding.layoutSuccess.visibility = View.GONE
        setButtonState(binding.btnStartCalibration, true)
        waitingForTransferResult = false
        isTransactionLocked = false
    }

    private fun showSuccess() {
        binding.layoutStep1.visibility = View.GONE
        binding.layoutStep2.visibility = View.GONE
        binding.layoutSuccess.visibility = View.VISIBLE
        waitingForTransferResult = false
    }

    private fun prepareNextDeviceSameValues() {
        isTransactionLocked = false
        waitingForTransferResult = false
        setButtonState(binding.btnStartCalibration, true)
        binding.layoutStep1.visibility = View.GONE
        binding.layoutStep2.visibility = View.VISIBLE
        binding.layoutSuccess.visibility = View.GONE
        Toast.makeText(context, "Listo para el siguiente equipo", Toast.LENGTH_SHORT).show()
    }

    private fun setButtonState(button: MaterialButton, isEnabled: Boolean) {
        button.isEnabled = isEnabled
        val primaryColor = ContextCompat.getColor(requireContext(), R.color.purple_500)
        val disabledColor = Color.GRAY
        button.backgroundTintList = ColorStateList.valueOf(if (isEnabled) primaryColor else disabledColor)
    }



    // --- FUNCIÓN ACTUALIZADA CON TEXTOS TRADUCIBLES ---
    private fun updateResetButtonUI(state: Int) {
        resetVolumeState = state
        val btn = binding.btnResetVolume

        when (state) {
            STATE_IDLE -> {
                // Estado Normal: Rojo
                val redColor = Color.parseColor("#D32F2F")
                btn.isEnabled = true
                btn.strokeColor = ColorStateList.valueOf(redColor)
                btn.setTextColor(redColor)
                btn.iconTint = ColorStateList.valueOf(redColor)
                btn.backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)

                // USAMOS getString PARA SOPORTAR IDIOMAS
                btn.text = getString(R.string.btn_reset_volume)
            }
            STATE_PENDING -> {
                // Estado Pendiente: Gris
                val grayColor = Color.GRAY
                btn.isEnabled = true
                btn.strokeColor = ColorStateList.valueOf(grayColor)
                btn.setTextColor(grayColor)
                btn.iconTint = ColorStateList.valueOf(grayColor)
                btn.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#F0F0F0"))

                // USAMOS getString PARA SOPORTAR IDIOMAS
                btn.text = getString(R.string.btn_reset_pending)
            }
            STATE_SUCCESS -> {
                // Estado Éxito: Verde
                val greenColor = Color.parseColor("#4CAF50")
                btn.isEnabled = true // Lo dejamos habilitado aunque sea éxito
                btn.strokeColor = ColorStateList.valueOf(greenColor)
                btn.setTextColor(greenColor)
                btn.iconTint = ColorStateList.valueOf(greenColor)
                btn.backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)

                // USAMOS getString PARA SOPORTAR IDIOMAS
                btn.text = getString(R.string.btn_reset_success)
            }
        }
    }


    // --- LISTENERS ---

    private fun setupListeners() {
        binding.btnModeAuto.setOnClickListener { setManualMode(false) }
        binding.btnModeManual.setOnClickListener { setManualMode(true) }

        // PASO 1
        binding.btnReadMaster.setOnClickListener {
            if (isManualMode) {
                processManualEntry()
            } else {
                setButtonState(binding.btnReadMaster, false)
                waitingForTransferResult = false
                listener?.requestNextCommand(CMD_READ_ENGINEERING)
            }
        }

        // --- NUEVO: LÓGICA TOGGLE PARA BOTÓN RESET ---
        binding.btnResetVolume.setOnClickListener {
            if (resetVolumeState == STATE_PENDING) {
                // CANCELAR: Si ya estaba pendiente y lo tocan, cancelamos.
                updateResetButtonUI(STATE_IDLE)

                // Enviamos un comando "seguro" (Lectura) para sobrescribir el comando Reset
                // y que no se ejecute si el usuario acerca el teléfono por error.
                listener?.requestNextCommand(CMD_READ_ENGINEERING)
                Toast.makeText(context, "Reset cancelado", Toast.LENGTH_SHORT).show()

            } else {
                // ACTIVAR: Si estaba Idle o Success, lo ponemos en espera
                updateResetButtonUI(STATE_PENDING)
                listener?.requestNextCommand(CMD_RESET_VOLUME)
            }
        }

        // PASO 2: ESCRITURA
        binding.btnStartCalibration.setOnClickListener {
            if (isTransactionLocked) return@setOnClickListener

            val volString = binding.editTextVolPatron.text.toString().replace(',', '.')
            val tempString = binding.editTextTempPatron.text.toString().replace(',', '.')
            val volValue = volString.toFloatOrNull()
            val tempValue = tempString.toFloatOrNull()

            if (volValue != null && tempValue != null) {
                isTransactionLocked = true
                waitingForTransferResult = true
                setButtonState(binding.btnStartCalibration, false)

                // 1. Aumentamos el buffer a 11 bytes (1 tipo + 4 vol + 4 temp + 2 checksum)
                val buffer = ByteBuffer.allocate(11).order(ByteOrder.LITTLE_ENDIAN)
                buffer.put(detectedCalibrationType)
                buffer.putFloat(volValue)   // Índices 1, 2, 3, 4
                buffer.putFloat(tempValue)  // Índices 5, 6, 7, 8

                // --- NUEVO: Cálculo del Checksum (Suma simple de los 8 bytes) ---
                val dataArray = buffer.array()
                var checksum = 0

                // Iteramos solo por los 8 bytes de Volumen y Temperatura
                for (i in 1..8) {
                    // El "and 0xFF" es crucial porque en Java/Kotlin los bytes son con signo (signed).
                    // Esto evita que un byte negativo reste valor a la suma, simulando el comportamiento de C.
                    checksum += (dataArray[i].toInt() and 0xFF)
                }

                // 2. Insertamos el checksum de 16-bits al final (índices 9 y 10)
                buffer.putShort(checksum.toShort())
                // -----------------------------------------------------------------

                Log.d(
                    TAG,
                    "Enviando Calibración 0x13. Type=$detectedCalibrationType | Checksum=$checksum"
                )

                sharedViewModel.setConfigDataToWrite(buffer.array())
                Toast.makeText(context, getString(R.string.toast_transferring), Toast.LENGTH_SHORT)
                    .show()
                listener?.requestNextCommand(CMD_CALIBRATE_FLOW)

            } else {
                Toast.makeText(
                    context,
                    getString(R.string.toast_invalid_values),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        binding.btnResetFlow.setOnClickListener { showStep1() }
        binding.btnFinishSuccess.setOnClickListener { prepareNextDeviceSameValues() }
    }

    private fun processManualEntry() {
        val manualId = selectedManualId
        if (manualId == null) {
            binding.inputLayoutManualFlow.error = getString(R.string.hint_select_flow_type)
            return
        }
        binding.inputLayoutManualFlow.error = null
        detectedCalibrationType = manualId.toByte()
        val typeName = dynamicManualOptions.find { it.second == manualId }?.first ?: "Manual"
        binding.textDetectedFlowType.text = "$typeName (Manual)"
        binding.editTextVolPatron.setText("")
        binding.editTextTempPatron.setText("")
        showStep2()
    }

    // --- OBSERVERS ---


    // --- OBSERVER ACTUALIZADO CON TIMER DE 3 SEGUNDOS ---
    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // 1. Observer de Datos (Lectura) - SIN CAMBIOS
                launch {
                    sharedViewModel.engineeringResponseData.collect { data ->
                        setButtonState(binding.btnReadMaster, true)
                        if (!isManualMode && data != null && data.size >= MIN_ENGINEERING_PAYLOAD_SIZE) {
                            if (binding.layoutStep1.visibility == View.VISIBLE) {
                                processReadData(data)
                            }
                        }
                    }
                }

                // 2. Observer de Estado (Escritura/Comandos) - CON CAMBIOS
                launch {
                    sharedViewModel.writeStatus.collect { state ->
                        if (state !is NfcState.Loading && state !is NfcState.Idle) {
                            setButtonState(binding.btnReadMaster, true)
                        }

                        when (state) {
                            is NfcState.Success -> {
                                // A. ÉXITO EN CALIBRACIÓN NORMAL
                                if (waitingForTransferResult) {
                                    waitingForTransferResult = false
                                    showSuccess()
                                    Toast.makeText(context, getString(R.string.msg_command_success), Toast.LENGTH_SHORT).show()
                                }

                                // B. ÉXITO EN RESET VOLUMEN (Lógica del Timer)
                                if (resetVolumeState == STATE_PENDING) {
                                    // 1. Ponemos el botón en VERDE
                                    updateResetButtonUI(STATE_SUCCESS)
                                    Toast.makeText(context, getString(R.string.msg_command_success), Toast.LENGTH_SHORT).show()

                                    // 2. Iniciamos una corutina para esperar 3 segundos
                                    viewLifecycleOwner.lifecycleScope.launch {
                                        delay(3000) // Espera 3000ms (3 seg)

                                        // Verificamos si sigue en estado SUCCESS (por si el usuario salió de la pantalla)
                                        if (resetVolumeState == STATE_SUCCESS) {
                                            updateResetButtonUI(STATE_IDLE) // Volver a ROJO
                                        }
                                    }
                                }
                            }
                            is NfcState.Error -> {
                                if (waitingForTransferResult) {
                                    isTransactionLocked = false
                                    waitingForTransferResult = false
                                    setButtonState(binding.btnStartCalibration, true)
                                    Toast.makeText(context, "Error: ${state.errorMessage}", Toast.LENGTH_LONG).show()
                                }

                                // Si falla el Reset, volvemos a Rojo inmediatamente
                                if (resetVolumeState == STATE_PENDING) {
                                    updateResetButtonUI(STATE_IDLE)
                                    Toast.makeText(context, "Error: ${state.errorMessage}", Toast.LENGTH_LONG).show()
                                }
                            }
                            is NfcState.Loading -> { }
                            is NfcState.Idle -> { }
                        }
                    }
                }
            }
        }
    }


    private fun processReadData(data: ByteArray) {
        try {
            val engineering = NfcDataParser.parseEngineeringData(data)
            val rawLastTrip = engineering.lastTripFlow.toFloat() / 100.0f
            val rawTemp = engineering.temperature.toFloat() / 10.0f
            val rawVolLiters = engineering.volumeLiters.toFloat() / 1000.0f

            val autoIndex = determineCalibrationIndex(rawLastTrip)
            detectedCalibrationType = autoIndex.toByte()

            val typeName = getCalibrationTypeName(autoIndex)
            binding.textDetectedFlowType.text = typeName
            val valueToCopy = if (autoIndex == IDX_TEMP) 0.0f else rawVolLiters

            binding.editTextVolPatron.setText(String.format(Locale.US, "%.3f", valueToCopy))
            binding.editTextTempPatron.setText(String.format(Locale.US, "%.2f", rawTemp))

            showStep2()
            val msg = getString(R.string.toast_pattern_read_format, typeName)
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()

        } catch (e: Exception) {
            Log.e(TAG, "Error procesando datos: ${e.message}")
            Toast.makeText(context, getString(R.string.toast_error_reading), Toast.LENGTH_SHORT).show()
        }
    }

    private fun determineCalibrationIndex(flowValue: Float): Int {
        return when {
            flowValue < 0.5f -> IDX_TEMP
            flowValue < 8.0f -> IDX_FLOW_Q1
            flowValue < 15.0f -> IDX_FLOW_Q2
            flowValue < 30.0f -> IDX_FLOW_035L
            flowValue < 100.0f -> IDX_FLOW_1L
            flowValue < 1500.0f -> IDX_FLOW_10L
            else -> IDX_FLOW_Q3
        }
    }

    private fun getCalibrationTypeName(index: Int): String {
        return when (index) {
            IDX_TEMP -> getString(R.string.cal_type_temp_only)
            IDX_FLOW_10L -> getString(R.string.cal_type_flow_10l)
            IDX_FLOW_1L -> getString(R.string.cal_type_flow_1l)
            IDX_FLOW_035L -> getString(R.string.cal_type_flow_035l)
            IDX_FLOW_Q2 -> getString(R.string.cal_type_flow_q2)
            IDX_FLOW_Q1 -> getString(R.string.cal_type_flow_q1)
            IDX_FLOW_Q3 -> getString(R.string.cal_type_flow_q3)
            else -> getString(R.string.cal_type_unknown)
        }
    }

    override fun onDetach() {
        super.onDetach()
        listener = null
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
