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

/**
 * Fragmento dedicado a la funcionalidad de Calibración.
 * Permite leer un equipo patrón usando el comando de Ingeniería (0x05)
 * y transferir ese valor a otros equipos (0x13).
 */
@OptIn(ExperimentalStdlibApi::class)
class CalibrationFragment : Fragment() {

    companion object {
        private const val TAG = "CalibrationFragment"
        // El payload de ingeniería es de 44 bytes (11 Ints)
        private const val ENGINEERING_PAYLOAD_SIZE = 44
        // Comando para leer datos de ingeniería del patrón
        private const val CMD_READ_ENGINEERING: Byte = 0x05
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
            "0 - Temperatura",        // Índice 0
            "1 - Caudal a 10 L/m",    // Índice 1 (Caudal)
            "2 - Caudal a 1 L/m",     // Índice 2 (Caudal)
            "3 - Caudal a 0,35 L/m",  // Índice 3 (Caudal)
            "4 - Caudal a Q2",        // Índice 4 (Caudal)
            "5 - Caudal a Q1",        // Índice 5 (Caudal)
            "6 - Caudal a Q3"         // Índice 6 (Caudal)
        )

        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, calibrationOptions)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerCalibrationType.adapter = adapter
    }

    private fun setupListeners() {
        // 1. Botón LEER PATRÓN (Ahora usa comando de Ingeniería 0x05)
        binding.btnReadMaster.setOnClickListener {
            Log.d(TAG, "Solicitando Lectura de Patrón (0x05 - Ingeniería)")
            listener?.requestNextCommand(CMD_READ_ENGINEERING)
        }

        // 2. Botón TRANSFERIR (ESCRIBIR)
        binding.btnStartCalibration.setOnClickListener {
            val inputString = binding.editTextTempPatron.text.toString()
            // Reemplazar coma por punto para asegurar conversión correcta
            val cleanInput = inputString.replace(',', '.')
            val floatValue = cleanInput.toFloatOrNull()

            if (floatValue != null) {
                // Obtener el tipo de calibración seleccionado
                val calibrationTypeByte = binding.spinnerCalibrationType.selectedItemPosition.toByte()

                // Construir payload de 5 bytes: [Tipo (1B)] + [Float (4B)]
                val buffer = ByteBuffer.allocate(5).order(ByteOrder.LITTLE_ENDIAN)
                buffer.put(calibrationTypeByte)
                buffer.putFloat(floatValue)
                val payloadBytes = buffer.array()

                // Guardar en ViewModel y solicitar escritura
                sharedViewModel.setConfigDataToWrite(payloadBytes)

                Log.d(TAG, "Solicitando Calibración (0x13). Tipo: $calibrationTypeByte, Valor: $floatValue")
                listener?.requestCalibrationWrite()

                Toast.makeText(context, "Valor listo. Acerque el equipo a calibrar.", Toast.LENGTH_LONG).show()
            } else {
                binding.inputLayoutTempPatron.error = "Ingrese un número válido primero"
            }
        }
    }

    private fun setupObservers() {
        // Observamos los datos de INGENIERÍA (Respuesta 0x85) del Equipo Patrón
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                sharedViewModel.engineeringResponseData.collect { data ->
                    if (data != null && data.isNotEmpty()) {

                        // Verificar tamaño y recortar padding
                        if (data.size >= ENGINEERING_PAYLOAD_SIZE) {
                            val usefulBytes = data.sliceArray(0 until ENGINEERING_PAYLOAD_SIZE)

                            try {
                                val engineering = NfcDataParser.parseEngineeringData(usefulBytes)

                                // --- LÓGICA DE SELECCIÓN AUTOMÁTICA (Desde Ingeniería) ---
                                val selectedPosition = binding.spinnerCalibrationType.selectedItemPosition
                                val valueToCopy: Float
                                val label: String

                                if (selectedPosition == 0) {
                                    // Opción 0: Temperatura
                                    // Usamos temperatureUncal de Ingeniería y dividimos por 10.0
                                    valueToCopy = engineering.temperatureUncal.toFloat() / 10.0f
                                    label = "Temperatura"
                                } else {
                                    // Opciones 1-6: Caudales
                                    // Aquí usamos el VOLUMEN (volumeLiters) y dividimos por 1000.0
                                    valueToCopy = engineering.volumeLiters.toFloat() / 1000.0f
                                    label = "Volumen (L)"
                                }
                                // ---------------------------------------

                                val formattedValue = String.format(Locale.US, "%.2f", valueToCopy)
                                binding.editTextTempPatron.setText(formattedValue)
                                binding.inputLayoutTempPatron.error = null

                                Toast.makeText(context, "$label Patrón Leído (Ing): $formattedValue", Toast.LENGTH_SHORT).show()

                            } catch (e: Exception) {
                                Log.e(TAG, "Error al parsear patrón ingeniería: ${e.message}")
                                Toast.makeText(context, "Error al leer datos de ingeniería.", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            // Si los datos están incompletos pero no vacíos
                            Log.e(TAG, "Error tamaño ingeniería: Recibidos ${data.size}, esperados $ENGINEERING_PAYLOAD_SIZE")
                        }
                    }
                }
            }
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

