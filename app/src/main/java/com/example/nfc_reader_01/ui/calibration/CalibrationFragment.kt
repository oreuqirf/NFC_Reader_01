package com.example.nfc_reader_01.ui.calibration

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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

@OptIn(ExperimentalStdlibApi::class)
class CalibrationFragment : Fragment() {

    companion object {
        private const val TAG = "CalibrationFragment"
        private const val ENGINEERING_PAYLOAD_SIZE = 52
        private const val CMD_READ_ENGINEERING: Byte = 0x05

        private const val IDX_TEMP = 0
        private const val IDX_FLOW_10L = 1
        private const val IDX_FLOW_1L = 2
        private const val IDX_FLOW_035L = 3
        private const val IDX_FLOW_Q2 = 4
        private const val IDX_FLOW_Q1 = 5
        private const val IDX_FLOW_Q3 = 6
    }

    private var _binding: FragmentCalibrationBinding? = null
    private val binding get() = _binding!!

    private val sharedViewModel: SharedNfcViewModel by activityViewModels()
    private var listener: NfcInteractionListener? = null
    private var detectedCalibrationType: Byte = 0

    // CORRECCIÓN LÓGICA: Bandera para saber si estamos esperando el resultado de la transferencia
    private var waitingForTransferResult = false

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
        showStep1()
        setupListeners()
        setupObservers()
        setupLanguageButtons(view)
    }

    private fun setupLanguageButtons(view: View) {
        try {
            binding.btnLangEs.setOnClickListener { setAppLocale("es") }
            binding.btnLangEn.setOnClickListener { setAppLocale("en") }
            binding.btnLangZh.setOnClickListener { setAppLocale("zh-CN") }
        } catch (e: Exception) {
            Log.e(TAG, "Error configurando botones de idioma: ${e.message}")
        }
    }

    private fun setAppLocale(languageCode: String) {
        val appLocale: LocaleListCompat = LocaleListCompat.forLanguageTags(languageCode)
        AppCompatDelegate.setApplicationLocales(appLocale)
    }

    // --- MANEJO DE VISTAS (PASOS) ---

    private fun showStep1() {
        binding.layoutStep1.visibility = View.VISIBLE
        binding.layoutStep2.visibility = View.GONE
        binding.layoutSuccess.visibility = View.GONE

        binding.textDetectedFlowType.text = getString(R.string.text_placeholder_dash)
        binding.editTextVolPatron.setText("")
        binding.editTextTempPatron.setText("")

        setButtonState(binding.btnReadMaster, true)

        // CORRECCIÓN: Al volver al paso 1, no estamos esperando transferencia
        waitingForTransferResult = false
    }

    private fun showStep2() {
        binding.layoutStep1.visibility = View.GONE
        binding.layoutStep2.visibility = View.VISIBLE
        binding.layoutSuccess.visibility = View.GONE
        setButtonState(binding.btnStartCalibration, true)

        // Estamos en paso 2, pero aún no se ha pulsado el botón de transferir
        waitingForTransferResult = false
    }

    private fun showSuccess() {
        binding.layoutStep1.visibility = View.GONE
        binding.layoutStep2.visibility = View.GONE
        binding.layoutSuccess.visibility = View.VISIBLE
        // Proceso terminado
        waitingForTransferResult = false
    }

    private fun setButtonState(button: MaterialButton, isEnabled: Boolean) {
        button.isEnabled = isEnabled
        val primaryColor = ContextCompat.getColor(requireContext(), R.color.purple_500)
        val disabledColor = Color.GRAY
        if (isEnabled) {
            button.backgroundTintList = ColorStateList.valueOf(primaryColor)
        } else {
            button.backgroundTintList = ColorStateList.valueOf(disabledColor)
        }
    }

    // --- LISTENERS ---

    private fun setupListeners() {
        // PASO 1: LEER
        binding.btnReadMaster.setOnClickListener {
            setButtonState(binding.btnReadMaster, false)
            waitingForTransferResult = false // Aseguramos que no salte pasos
            Log.d(TAG, "Solicitando Lectura de Patrón (0x05)")
            listener?.requestNextCommand(CMD_READ_ENGINEERING)
        }

        // PASO 2: TRANSFERIR
        binding.btnStartCalibration.setOnClickListener {
            val volString = binding.editTextVolPatron.text.toString().replace(',', '.')
            val tempString = binding.editTextTempPatron.text.toString().replace(',', '.')

            val volValue = volString.toFloatOrNull()
            val tempValue = tempString.toFloatOrNull()

            if (volValue != null && tempValue != null) {
                setButtonState(binding.btnStartCalibration, false)

                // CORRECCIÓN: Activamos la bandera. Ahora sí aceptaremos un Success para ir al final.
                waitingForTransferResult = true

                val calibrationType = detectedCalibrationType
                val buffer = ByteBuffer.allocate(9).order(ByteOrder.LITTLE_ENDIAN)
                buffer.put(calibrationType)
                buffer.putFloat(volValue)
                buffer.putFloat(tempValue)

                val payloadBytes = buffer.array()

                sharedViewModel.setConfigDataToWrite(payloadBytes)
                Log.d(TAG, "Enviando Calibración. Tipo: $calibrationType")
                listener?.requestCalibrationWrite()

                Toast.makeText(context, getString(R.string.toast_transferring), Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, getString(R.string.toast_invalid_values), Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnResetFlow.setOnClickListener { showStep1() }
        binding.btnFinishSuccess.setOnClickListener { showStep2() }
    }

    // --- OBSERVERS ---

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {

                // 1. Observador de Datos (Lectura Patrón)
                launch {
                    sharedViewModel.engineeringResponseData.collect { data ->
                        setButtonState(binding.btnReadMaster, true)

                        if (data != null && data.isNotEmpty() && data.size >= ENGINEERING_PAYLOAD_SIZE) {
                            // Si estamos en paso 1, procesamos y pasamos al 2
                            if (binding.layoutStep1.visibility == View.VISIBLE) {
                                processReadData(data)
                            }
                        }
                    }
                }

                // 2. Observador de ESTADO (Controla la UI final)
                launch {
                    sharedViewModel.writeStatus.collect { state ->
                        // Reactivamos botones si terminó la carga
                        if (state !is NfcState.Loading && state !is NfcState.Idle) {
                            setButtonState(binding.btnReadMaster, true)
                            setButtonState(binding.btnStartCalibration, true)
                        }

                        when (state) {
                            is NfcState.Success -> {
                                // CORRECCIÓN CLAVE:
                                // Solo mostramos la pantalla de "Transferencia Exitosa" si realmente
                                // estábamos esperando el resultado de la transferencia (Paso 2).
                                // Si recibimos Success en el Paso 1 (Lectura), lo ignoramos aquí
                                // (se encarga processReadData).
                                if (waitingForTransferResult) {
                                    showSuccess()
                                }
                            }
                            is NfcState.Error -> {
                                waitingForTransferResult = false
                                Toast.makeText(context, state.errorMessage, Toast.LENGTH_LONG).show()
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
            val usefulBytes = data.sliceArray(0 until ENGINEERING_PAYLOAD_SIZE)
            val engineering = NfcDataParser.parseEngineeringData(usefulBytes)

            val rawLastTrip = engineering.lastTripFlow.toFloat() / 10.0f
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

    private fun determineCalibrationIndex(flowValue: Float): Int {
        return when {
            flowValue < 0.1f -> IDX_TEMP
            flowValue >= 4.0f && flowValue <= 8.0f -> IDX_FLOW_Q1
            flowValue > 8.0f && flowValue <= 15.0f -> IDX_FLOW_Q2
            flowValue >= 15.0f && flowValue <= 24.0f -> IDX_FLOW_035L
            flowValue >= 24.0f && flowValue <= 70.0f -> IDX_FLOW_1L
            flowValue >= 100.0f && flowValue <= 700.0f -> IDX_FLOW_10L
            flowValue >= 800.0f && flowValue <= 2800.0f -> IDX_FLOW_Q3
            else -> IDX_TEMP
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

