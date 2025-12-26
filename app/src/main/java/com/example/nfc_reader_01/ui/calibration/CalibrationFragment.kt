package com.example.nfc_reader_01.ui.calibration

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.nfc_reader_01.NfcInteractionListener
import com.example.nfc_reader_01.SharedNfcViewModel
import com.example.nfc_reader_01.data.NfcDataParser
import com.example.nfc_reader_01.databinding.FragmentCalibrationBinding
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import kotlin.ExperimentalStdlibApi
import kotlin.math.abs

/**
 * Fragmento dedicado a la funcionalidad de Calibración.
 * Permite leer un equipo patrón usando el comando de Ingeniería (0x05)
 * y transferir ese valor a otros equipos (0x13).
 * Incluye selección automática del punto de calibración basada en 'lastTripFlow'.
 */
@OptIn(ExperimentalStdlibApi::class)
class CalibrationFragment : Fragment() {

    companion object {
        private const val TAG = "CalibrationFragment"
        // El payload de ingeniería es de 48 bytes (12 Ints)
        private const val ENGINEERING_PAYLOAD_SIZE = 48
        private const val CMD_READ_ENGINEERING: Byte = 0x05

        // Índices del Spinner (Deben coincidir con el orden en 'setupSpinner')
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

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is NfcInteractionListener) {
            listener = context
        } else {
            Log.e(TAG, "$context debe implementar NfcInteractionListener")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCalibrationBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupSpinner()
        setupListeners()
        setupObservers()
    }

    private fun setupSpinner() {
        val calibrationOptions = listOf(
            "0 - Temperatura",        // IDX_TEMP
            "1 - Caudal a 10 L/m (600 L/h)",    // IDX_FLOW_10L
            "2 - Caudal a 1 L/m (60 L/h)",     // IDX_FLOW_1L
            "3 - Caudal a 0,35 L/m (21 L/h)",  // IDX_FLOW_035L
            "4 - Caudal Q2 (10 L/h)",        // IDX_FLOW_Q2
            "5 - Caudal Q1 (6 L/h)",        // IDX_FLOW_Q1
            "6 - Caudal Q3 (2500 L/h)"         // IDX_FLOW_Q3
        )

        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, calibrationOptions)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerCalibrationType.adapter = adapter
    }


    private fun setupListeners() {
        // 1. Botón LEER PATRÓN (Usa comando de Ingeniería 0x05 a través de requestReadMaster)
        binding.btnReadMaster.setOnClickListener {
            Log.d(TAG, "Solicitando Lectura de Patrón (0x05)")
            listener?.requestReadMaster()
        }

        // 2. Botón TRANSFERIR (ESCRIBIR 0x13)
        binding.btnStartCalibration.setOnClickListener {
            val volString = binding.editTextVolPatron.text.toString().replace(',', '.')
            val tempString = binding.editTextTempPatron.text.toString().replace(',', '.')

            val volValue = volString.toFloatOrNull()
            val tempValue = tempString.toFloatOrNull()

            if (volValue != null && tempValue != null) {
                // Obtener el tipo de calibración seleccionado (Byte 0)
                val calibrationTypeByte = binding.spinnerCalibrationType.selectedItemPosition.toByte()

                // Construir payload de 9 bytes:
                // [Tipo (1B)] + [Volumen Float (4B)] + [Temperatura Float (4B)]
                val buffer = ByteBuffer.allocate(9).order(ByteOrder.LITTLE_ENDIAN)

                buffer.put(calibrationTypeByte)
                buffer.putFloat(volValue)  // Bytes 1-4
                buffer.putFloat(tempValue) // Bytes 5-8

                val payloadBytes = buffer.array()

                // Guardar en ViewModel
                sharedViewModel.setConfigDataToWrite(payloadBytes)

                // Solicitar escritura
                Log.d(TAG, "Solicitando Calibración (0x13). Tipo: $calibrationTypeByte, Vol: $volValue, Temp: $tempValue")
                listener?.requestCalibrationWrite()

                Toast.makeText(context, "Listo para transferir. Acerque el equipo a calibrar.", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(context, "Error: Ingrese valores numéricos válidos en ambos campos.", Toast.LENGTH_SHORT).show()
                if (volValue == null) binding.inputLayoutVolPatron.error = "Inválido"
                if (tempValue == null) binding.inputLayoutTempPatron.error = "Inválido"
            }
        }
    }


    private fun setupObservers() {
        // Observamos los datos de INGENIERÍA (Respuesta 0x85) del Equipo Patrón
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                sharedViewModel.engineeringResponseData.collect { data ->
                    if (data != null && data.isNotEmpty()) {

                        // Verificar tamaño (48 bytes = 12 ints)
                        if (data.size >= ENGINEERING_PAYLOAD_SIZE) {
                            val usefulBytes = data.sliceArray(0 until ENGINEERING_PAYLOAD_SIZE)

                            try {
                                val engineering = NfcDataParser.parseEngineeringData(usefulBytes)

                                // --- EXTRACCIÓN DE DATOS DEL PATRÓN ---

                                // 1. Volumen (Last Trip Flow): Escalado / 1000.0f (Litros)
                                // Convertimos bits a float (o int a float directo si es valor numérico)
                                // Asumiendo que es un valor numérico entero escalado:
                                val rawLastTrip = engineering.lastTripFlow.toFloat() / 10.0f

                                val rawLastPartialVolume = engineering.volumeLiters.toFloat() / 1000.0f

                                // 2. Temperatura (Temp Uncal): Escalado / 10.0f (°C)
                                val rawTemp = engineering.temperatureUncal.toFloat() / 10.0f

                                // --- SELECCIÓN AUTOMÁTICA SPINNER ---
                                // Basada en el caudal/volumen leído para identificar el punto de calibración
                                val autoIndex = determineCalibrationIndex(rawLastTrip)
                                binding.spinnerCalibrationType.setSelection(autoIndex)

                                // --- LLENAR CAMPOS DE TEXTO ---
                                val formattedVol = String.format(Locale.US, "%.3f", rawLastPartialVolume)
                                val formattedTemp = String.format(Locale.US, "%.2f", rawTemp)

                                binding.editTextVolPatron.setText(formattedVol)
                                binding.editTextTempPatron.setText(formattedTemp)

                                // Limpiar errores
                                binding.inputLayoutVolPatron.error = null
                                binding.inputLayoutTempPatron.error = null

                                Toast.makeText(context, "Patrón Leído:\nVol: $formattedVol L\nTemp: $formattedTemp °C", Toast.LENGTH_SHORT).show()

                            } catch (e: Exception) {
                                Log.e(TAG, "Error al parsear patrón ingeniería: ${e.message}")
                                Toast.makeText(context, "Error al leer datos del patrón.", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Log.e(TAG, "Error tamaño ingeniería: Recibidos ${data.size}, esperados $ENGINEERING_PAYLOAD_SIZE")
                        }
                    }
                }
            }
        }
    }

    /**
     * Determina el índice del Spinner basándose en rangos explícitos (Min - Max).
     * Los valores de caudal están en L/h.
     */
    private fun determineCalibrationIndex(flowValue: Float): Int {
        return when {
            // Rango para Temperatura (valores cercanos a 0 o negativos)
            flowValue < 0.1f -> IDX_TEMP

            // Q1 = 6 L/h (Rango: 4.0 - 8.0)
            flowValue >= 4.0f && flowValue <= 8.0f -> IDX_FLOW_Q1

            // Q2 = 10 L/h (Rango: 8.0 - 15.0)
            flowValue > 8.0f && flowValue <= 15.0f -> IDX_FLOW_Q2

            // 0.35 L/m = 21 L/h (Rango: 18.0 - 24.0)
            flowValue >= 15.0f && flowValue <= 24.0f -> IDX_FLOW_035L

            // 1 L/m = 60 L/h (Rango: 50.0 - 70.0)
            flowValue >= 24.0f && flowValue <= 70.0f -> IDX_FLOW_1L

            // 10 L/m = 600 L/h (Rango: 550.0 - 650.0)
            flowValue >= 100.0f && flowValue <= 700.0f -> IDX_FLOW_10L

            // Q3 = 2500 L/h (Rango: 2400.0 - 2600.0)
            flowValue >= 800.0f && flowValue <= 2800.0f -> IDX_FLOW_Q3

            // Si no cae en ningún rango conocido, por defecto Temperatura
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
