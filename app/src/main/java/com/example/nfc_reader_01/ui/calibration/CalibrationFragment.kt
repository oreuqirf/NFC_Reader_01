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
        private const val IDX_FLOW_Q1 = 1
        private const val IDX_FLOW_Q2 = 2
        private const val IDX_FLOW_P3 = 3
        private const val IDX_FLOW_P4 = 4
        private const val IDX_FLOW_P5 = 5
        private const val IDX_FLOW_P6 = 6
        private const val IDX_FLOW_P7 = 7
        private const val IDX_FLOW_P8 = 8
        private const val IDX_FLOW_10L = 9
        private const val IDX_FLOW_P10 = 10
        private const val IDX_FLOW_Q3 = 11
        private const val IDX_FLOW_Q4 = 12

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

    // --- VARIABLES PARA MODO SECUENCIA (BANCO) ---
    private var isSequenceMode = false
    private val sequenceOrder = listOf(IDX_FLOW_10L, IDX_FLOW_Q3, IDX_FLOW_Q2, IDX_FLOW_Q1)
    private var currentSequenceStageIndex = 0 // 0 a 3 (apunta a la lista de arriba)
    private var currentDeviceInBatch = 1      // 1 a 6

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

        // --- NUEVO: Iniciamos por defecto en Modo Banco ---
        setSequenceMode(true)

        setupListeners()
        setupObservers()
        setupLanguageButtons()
    }


    // ... (setupDropdownAdapter, setupLanguageButtons, setAppLocale se mantienen IGUAL) ...

    private fun setupDropdownAdapter() {
        val orderedIds = listOf(
            IDX_TEMP, IDX_FLOW_Q1, IDX_FLOW_Q2, IDX_FLOW_P3, IDX_FLOW_P4,  IDX_FLOW_P5, IDX_FLOW_P6,  IDX_FLOW_P7,  IDX_FLOW_P8,  IDX_FLOW_10L, IDX_FLOW_P10,IDX_FLOW_Q3, IDX_FLOW_Q4)
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

            // --- ACTUALIZADO: Texto dinámico ---
            binding.btnReadMaster.text = getString(R.string.btn_transfer)
            binding.btnReadMaster.icon = null

            // 1. Pintamos el botón Manual de morado
            binding.btnModeManual.backgroundTintList = ColorStateList.valueOf(activeColor)
            binding.btnModeManual.setTextColor(Color.WHITE)

            // 2. ¡NUEVO! Despintamos el botón Banco (Batch) a gris/transparente
            binding.btnModeSequence.backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
            binding.btnModeSequence.setTextColor(inactiveColor)

            // 3. (Mantenemos apagado el Auto por las dudas)
            binding.btnModeAuto.backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
            binding.btnModeAuto.setTextColor(inactiveColor)

            binding.textDetectedFlowType.text = getString(R.string.text_manual_mode)
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

        // NUEVO: Mostrar el botón de salto SOLO si estamos en Modo Secuencia
        if (isSequenceMode) {
            binding.btnSkipNextFlow.visibility = View.VISIBLE
        } else {
            binding.btnSkipNextFlow.visibility = View.GONE
        }
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
        Toast.makeText(context, getString(R.string.toast_ready_next_device), Toast.LENGTH_SHORT).show()
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

        // --- ACTUALIZADO: SELECTORES DE MODO ---
        binding.btnModeAuto.setOnClickListener {
            setSequenceMode(false) // Apaga secuencia
            setManualMode(false)   // Apaga manual
        }

        binding.btnModeManual.setOnClickListener {
            setSequenceMode(false) // Apaga secuencia
            setManualMode(true)    // Enciende manual
        }

        binding.btnModeSequence.setOnClickListener {
            setManualMode(false)   // Apaga manual
            setSequenceMode(true)  // Enciende secuencia (Banco)
        }

        // NOTA: Si agregas un tercer botón para el Modo Secuencia en tu XML (ej. btnModeSequence),
        // descomenta esta línea:
        // binding.btnModeSequence.setOnClickListener { setSequenceMode(true) }

        // PASO 1
        binding.btnReadMaster.setOnClickListener {
            // --- ACTUALIZADO: ORDEN DE PRIORIDAD DE MODOS ---
            if (isSequenceMode) {
                processSequenceEntry()
            } else if (isManualMode) {
                processManualEntry()
            } else {
                setButtonState(binding.btnReadMaster, false)
                waitingForTransferResult = false
                listener?.requestNextCommand(CMD_READ_ENGINEERING)
            }
        }

        // --- LÓGICA TOGGLE PARA BOTÓN RESET (SIN CAMBIOS) ---
        binding.btnResetVolume.setOnClickListener {
            if (resetVolumeState == STATE_PENDING) {
                // CANCELAR: Si ya estaba pendiente y lo tocan, cancelamos.
                updateResetButtonUI(STATE_IDLE)

                // Enviamos un comando "seguro" (Lectura) para sobrescribir el comando Reset
                // y que no se ejecute si el usuario acerca el teléfono por error.
                listener?.requestNextCommand(CMD_READ_ENGINEERING)
                Toast.makeText(context, getString(R.string.toast_reset_canceled), Toast.LENGTH_SHORT).show()

            } else {
                // ACTIVAR: Si estaba Idle o Success, lo ponemos en espera
                updateResetButtonUI(STATE_PENDING)
                listener?.requestNextCommand(CMD_RESET_VOLUME)
            }
        }

        // PASO 2: ESCRITURA
        binding.btnStartCalibration.setOnClickListener {
            if (isTransactionLocked) return@setOnClickListener

            // 1. Obtenemos el texto y limpiamos letras o espacios
            val rawVol = binding.editTextVolPatron.text.toString().replace(',', '.')
            val rawTemp = binding.editTextTempPatron.text.toString().replace(',', '.')

            val volString = rawVol.replace(Regex("[^0-9.-]"), "")
            val tempString = rawTemp.replace(Regex("[^0-9.-]"), "")

            // 2. Validamos que al menos el Volumen esté completo
            if (volString.isEmpty()) {
                Toast.makeText(context, getString(R.string.toast_missing_volume), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val volValue = volString.toFloatOrNull()

            // LÓGICA INTELIGENTE: Si la temperatura está vacía, usamos 20.0f por defecto
            val tempValue = if (tempString.isEmpty()) 20.0f else tempString.toFloatOrNull()

            // 3. Si la conversión numérica es exitosa, procedemos
            if (volValue != null && tempValue != null) {
                isTransactionLocked = true
                waitingForTransferResult = true
                setButtonState(binding.btnStartCalibration, false)

                // Buffer de 11 bytes (1 tipo + 4 vol + 4 temp + 2 checksum)
                val buffer = ByteBuffer.allocate(11).order(ByteOrder.LITTLE_ENDIAN)
                buffer.put(detectedCalibrationType)
                buffer.putFloat(volValue)   // Índices 1, 2, 3, 4
                buffer.putFloat(tempValue)  // Índices 5, 6, 7, 8

                // Cálculo del Checksum (Suma simple de los 8 bytes de vol y temp)
                val dataArray = buffer.array()
                var checksum = 0

                for (i in 1..8) {
                    checksum += (dataArray[i].toInt() and 0xFF)
                }

                buffer.putShort(checksum.toShort())

                Log.d(TAG, "Enviando Calibración 0x13. Type=$detectedCalibrationType | Checksum=$checksum (Vol=$volValue, Temp=$tempValue)")

                sharedViewModel.setConfigDataToWrite(buffer.array())
                Toast.makeText(context, getString(R.string.toast_transferring), Toast.LENGTH_SHORT).show()
                listener?.requestNextCommand(CMD_CALIBRATE_FLOW)

            } else {
                Toast.makeText(context, getString(R.string.toast_invalid_values), Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnResetFlow.setOnClickListener { showStep1() }
        binding.btnFinishSuccess.setOnClickListener { prepareNextDeviceSameValues() }

        // NUEVO: Listener para el botón de Saltar Caudal
        binding.btnSkipNextFlow.setOnClickListener {
            skipToNextSequenceStage()
        }

        // ...

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
        // --- ACTUALIZADO: Texto dinámico ---
        binding.textDetectedFlowType.text = getString(R.string.mode_manual_suffix, typeName)

        binding.btnStartCalibration.text = getString(R.string.btn_transfer)

        binding.editTextVolPatron.setText("")
        binding.editTextTempPatron.setText("")
        showStep2()
    }

    // --- OBSERVER ACTUALIZADO CON TIMER Y MODO SECUENCIA ---
    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // 1. Observer de Datos (Lectura) - SIN CAMBIOS
                launch {
                    sharedViewModel.engineeringResponseData.collect { data ->
                        setButtonState(binding.btnReadMaster, true)
                        if (!isManualMode && !isSequenceMode && data != null && data.size >= MIN_ENGINEERING_PAYLOAD_SIZE) {
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
                                // A. ÉXITO EN CALIBRACIÓN NORMAL O SECUENCIA
                                if (waitingForTransferResult) {
                                    waitingForTransferResult = false
                                    Toast.makeText(context, getString(R.string.msg_command_success), Toast.LENGTH_SHORT).show()

                                    if (isSequenceMode) {
                                        // Si estamos en modo banco, la app auto-avanza al siguiente equipo
                                        advanceSequenceBatch()
                                    } else {
                                        // Comportamiento original para Auto o Manual
                                        showSuccess()
                                    }
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
                                    Toast.makeText(context, getString(R.string.toast_error_prefix, state.errorMessage), Toast.LENGTH_LONG).show()
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
            // flowValue < 0.5f -> IDX_TEMP
            // flowValue < 8.0f -> IDX_FLOW_Q1
            // flowValue < 15.0f -> IDX_FLOW_Q2
            // flowValue < 30.0f -> IDX_FLOW_P3
            // flowValue < 100.0f -> IDX_FLOW_P4
            // flowValue < 1500.0f -> IDX_FLOW_10L
            // else -> IDX_FLOW_Q3
            // flowValue < 0.5f -> IDX_TEMP
            // flowValue < 13.0f -> IDX_FLOW_Q1
            // flowValue < 24.0f -> IDX_FLOW_Q2
            // flowValue < 48.0f -> IDX_FLOW_P3
            // flowValue < 160.0f -> IDX_FLOW_P4
            // flowValue < 1300.0f -> IDX_FLOW_10L
            // flowValue < 3000.0f -> IDX_FLOW_P6
            // flowValue < 4300.0f -> IDX_FLOW_Q3

            flowValue < 0.5f -> IDX_TEMP

            flowValue < 13.0f -> IDX_FLOW_Q1      // Intacto (< 13.0)
            flowValue < 24.0f -> IDX_FLOW_Q2      // Intacto (< 24.0)

            // --- Los 6 puntos intermedios entre Q2 (24) y 10L (600) ---
            flowValue < 40.0f -> IDX_FLOW_P3
            flowValue < 70.0f -> IDX_FLOW_P4
            flowValue < 120.0f -> IDX_FLOW_P5     // Nuevo punto
            flowValue < 200.0f -> IDX_FLOW_P6     // Nuevo punto
            flowValue < 350.0f -> IDX_FLOW_P7     // Nuevo punto
            flowValue < 500.0f -> IDX_FLOW_P8     // Nuevo punto

            // --- Puntos superiores ---
            flowValue < 1300.0f -> IDX_FLOW_10L   // (600 L/h nominales)
            flowValue < 2000.0f -> IDX_FLOW_P10   // Ajustar según corresponda
            flowValue < 4300.0f -> IDX_FLOW_Q3    // (4000 L/h nominales)
            else -> IDX_FLOW_Q4                   // (5000 L/h nominales)
        }
    }


    private fun getCalibrationTypeName(index: Int): String {
        return when (index) {
            IDX_TEMP -> getString(R.string.cal_type_temp_only)
            IDX_FLOW_Q1 -> getString(R.string.cal_type_flow_q1)
            IDX_FLOW_Q2 -> getString(R.string.cal_type_flow_q2)
            IDX_FLOW_P3 -> getString(R.string.cal_type_flow_p3)
            IDX_FLOW_P4 -> getString(R.string.cal_type_flow_p4)
            IDX_FLOW_P5 -> getString(R.string.cal_type_flow_p5)
            IDX_FLOW_P6 -> getString(R.string.cal_type_flow_p6)
            IDX_FLOW_P7 -> getString(R.string.cal_type_flow_p7)
            IDX_FLOW_P8 -> getString(R.string.cal_type_flow_p8)
            IDX_FLOW_10L -> getString(R.string.cal_type_flow_10l)
            IDX_FLOW_P10 -> getString(R.string.cal_type_flow_p10)
            IDX_FLOW_Q3 -> getString(R.string.cal_type_flow_q3)
            IDX_FLOW_Q4 -> getString(R.string.cal_type_flow_q4)
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

    private fun setSequenceMode(enable: Boolean) {
        isSequenceMode = enable
        val activeColor = ContextCompat.getColor(requireContext(), R.color.purple_500)
        val inactiveColor = Color.GRAY

        showStep1()

        if (isSequenceMode) {
            isManualMode = false
            currentSequenceStageIndex = 0
            currentDeviceInBatch = 1

            binding.inputLayoutManualFlow.visibility = View.GONE

            // --- ACTUALIZADO: Texto dinámico ---
            val firstFlow = getCalibrationTypeName(sequenceOrder[0])
            binding.btnReadMaster.text = getString(R.string.btn_start_batch, firstFlow)
            binding.btnReadMaster.icon = null

            binding.editTextTempPatron.setText("20.0")
            binding.inputLayoutTempPatron.isEnabled = false

            binding.btnModeSequence.backgroundTintList = ColorStateList.valueOf(activeColor)
            binding.btnModeSequence.setTextColor(Color.WHITE)
            binding.btnModeManual.backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
            binding.btnModeManual.setTextColor(inactiveColor)
        } else {
            binding.inputLayoutTempPatron.isEnabled = true
            binding.editTextTempPatron.setText("")
        }
    }

    private fun advanceSequenceBatch() {
        currentDeviceInBatch++

        if (currentDeviceInBatch > 6) {
            // Si pasamos los 6 equipos, salta automáticamente
            skipToNextSequenceStage()
        } else {
            val typeName = getCalibrationTypeName(sequenceOrder[currentSequenceStageIndex])
            binding.textDetectedFlowType.text = getString(R.string.text_device_step, typeName, currentDeviceInBatch)
            binding.btnStartCalibration.text = getString(R.string.btn_transfer_device, currentDeviceInBatch)

            prepareNextDeviceSameValues()
        }
    }

    private fun skipToNextSequenceStage() {
        currentDeviceInBatch = 1
        currentSequenceStageIndex++

        if (currentSequenceStageIndex >= sequenceOrder.size) {
            // Terminó el lote completo
            Toast.makeText(context, getString(R.string.toast_batch_complete), Toast.LENGTH_LONG).show()
            currentSequenceStageIndex = 0

            // LÓGICA NUEVA: El botón solo avisa "LOTE COMPLETO"
            binding.btnReadMaster.text = getString(R.string.btn_batch_complete)

            showStep1()
        } else {
            // Simplemente pasa al siguiente caudal
            val nextFlow = getCalibrationTypeName(sequenceOrder[currentSequenceStageIndex])
            Toast.makeText(context, getString(R.string.toast_change_valve, nextFlow), Toast.LENGTH_LONG).show()
            binding.btnReadMaster.text = getString(R.string.btn_start_batch, nextFlow)
            showStep1()
        }
    }


    private fun processSequenceEntry() {
        detectedCalibrationType = sequenceOrder[currentSequenceStageIndex].toByte()
        val typeName = getCalibrationTypeName(sequenceOrder[currentSequenceStageIndex])

        // --- ACTUALIZADO: Textos dinámicos ---
        binding.textDetectedFlowType.text = getString(R.string.text_device_step, typeName, currentDeviceInBatch)
        binding.btnStartCalibration.text = getString(R.string.btn_transfer_device, currentDeviceInBatch)

        if (currentDeviceInBatch == 1) {
            binding.editTextVolPatron.setText("")
        }

        showStep2()
    }

}
