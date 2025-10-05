package com.example.nfc_reader_01.ui.dashboard

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.nfc_reader_01.SharedNfcViewModel
import com.example.nfc_reader_01.databinding.FragmentDashboardBinding
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Fragmento para mostrar los datos leídos de la etiqueta NFC (respuesta 0x81) en campos estructurados.
 */
class DashboardFragment : Fragment() {

    private val TAG = "DashboardFragment"
    private var _binding: FragmentDashboardBinding? = null

    // Esta propiedad solo es válida entre onCreateView y onDestroyView.
    private val binding get() = _binding!!

    // Inyecta el ViewModel compartido a nivel de actividad
    private val sharedViewModel: SharedNfcViewModel by activityViewModels()

    // Códigos de comando (Asumidos para la funcionalidad de los botones)
    private val CMD_READ_IDENTITY: Byte = 0x01
    private val CMD_READ_PROCESS: Byte = 0x02
    private val CMD_READ_CONFIG: Byte = 0x03
    private val CMD_READ_ENGINEERING: Byte = 0x04


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Asegúrate de que FragmentDashboardBinding se genera correctamente a partir de tu XML
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupListeners()
        setupObservers()
    }

    /**
     * Configura los listeners para los botones de solicitud de datos.
     */
    private fun setupListeners() {
        binding.requestIdentityButton.setOnClickListener {
            // Este comando solicitará los datos de identidad al TAG
            sharedViewModel.sendCommand(CMD_READ_IDENTITY)
            Log.d(TAG, "Solicitando Identidad (0x01)")
            sharedViewModel.setUiMessage("Comando 0x01 preparado para enviar.")
        }

        binding.requestProcessButton.setOnClickListener {
            // Este comando solicitará los datos de proceso
            sharedViewModel.sendCommand(CMD_READ_PROCESS)
            Log.d(TAG, "Solicitando Proceso (0x02)")
            sharedViewModel.setUiMessage("Comando 0x02 preparado para enviar.")
        }

        binding.requestConfigButton.setOnClickListener {
            // Este comando solicitará los datos de configuración
            sharedViewModel.sendCommand(CMD_READ_CONFIG)
            Log.d(TAG, "Solicitando Configuración (0x03)")
            sharedViewModel.setUiMessage("Comando 0x03 preparado para enviar.")
        }

        binding.requestEngineeringButton.setOnClickListener {
            // Este comando solicitará los datos de ingeniería
            sharedViewModel.sendCommand(CMD_READ_ENGINEERING)
            Log.d(TAG, "Solicitando Ingeniería (0x04)")
            sharedViewModel.setUiMessage("Comando 0x04 preparado para enviar.")
        }
    }

    /**
     * Configura los observadores para los flujos de datos del ViewModel.
     */
    private fun setupObservers() {
        // Observa los datos de identidad (payload de 0x81)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Ahora 'identityData' está correctamente resuelto
                sharedViewModel.identityData.collect { data ->
                    if (data != null && data.isNotEmpty()) {
                        parseAndDisplayData(data)
                        Log.i(TAG, "Datos recibidos y parseados: ${data.toHexString()}")
                        sharedViewModel.setUiMessage("Datos de ${data.size} bytes recibidos y mostrados.")
                    } else {
                        // clearAllFields() // Podrías querer limpiar o mantener los últimos datos
                        Log.d(TAG, "Datos de identidad limpiados o nulos. Manteniendo la última visualización.")
                    }
                }
            }
        }

        // Observa el mensaje general del ViewModel para feedback
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                sharedViewModel.uiMessage.collect { message ->
                    // Usaremos un TextView con id 'textMessage' si existe, o solo logearemos
                    try {
                        binding.textMessage.text = message
                    } catch (e: Exception) {
                        Log.d(TAG, "Estado NFC: $message")
                    }
                }
            }
        }
    }

    /**
     * Parsea el ByteArray recibido del TAG y actualiza todos los campos de texto del formulario.
     *
     * !!! ATENCIÓN: LOS OFFSETS Y TIPOS DE DATOS SON ASUNCIONES Y DEBEN AJUSTARSE A TU PROTOCOLO BINARIO !!!
     */
    private fun parseAndDisplayData(data: ByteArray) {
        // Usamos Little-Endian, común en microcontroladores, y lo envolvemos en un Buffer para facilitar la lectura.
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)

        if (data.size < 72) { // 72 bytes es una asunción del tamaño total de los campos
            Log.e(TAG, "Payload demasiado corto. Recibidos ${data.size} bytes. Se esperaba más para un parseo completo.")
            binding.editTextSerialNumber.setText("ERROR: Payload size ${data.size}")
            return
        }

        try {
            // --- BLOQUE DE IDENTIDAD (Offset 0) ---
            // Asegúrate de que estás leyendo desde el offset 0 del buffer
            buffer.position(0)

            val serialNumberBytes = ByteArray(4) // Asumimos 4 bytes para el Serial
            buffer.get(serialNumberBytes)
            binding.editTextSerialNumber.setText(serialNumberBytes.toHexString())

            val firmwareVersion = buffer.getShort().toInt() // Asumimos 2 bytes (Short)
            binding.editTextFirmwareVersion.setText("v${(firmwareVersion / 100).toFloat().format(2)}")

            val lastConfigDate = buffer.getInt().toLong() // Asumimos 4 bytes (Int/Timestamp)
            binding.editTextLastConfigDate.setText("TS: $lastConfigDate")

            // --- BLOQUE DE PROCESO (Offset 10) ---
            // Asumiendo que el buffer ya está en la posición 10 después de leer 4+2+4 bytes
            binding.editTextVolume.setText("${buffer.getFloat().format(3)} m³") // Asumimos Float (4 bytes)
            binding.editTextFlow.setText("${buffer.getFloat().format(2)} L/h") // Asumimos Float (4 bytes)
            binding.editTextTemperature.setText("${buffer.getFloat().format(1)} °C") // Asumimos Float (4 bytes)

            // Asumimos 1 byte para batería y 1 byte para estado
            binding.editTextBattery.setText("${buffer.get().toInt()}%")
            binding.editTextStatus.setText("0x${String.format("%02X", buffer.get())}")

            // --- BLOQUE DE CONFIGURACIÓN (Offset 24) ---
            binding.editTextDirectFlowPeriod.setText("${buffer.getShort().toInt()} min")
            binding.editTextReverseFlowPeriod.setText("${buffer.getShort().toInt()} min")
            binding.editTextNoFlowPeriod.setText("${buffer.getShort().toInt()} min")
            binding.editTextLeakagePeriod.setText("${buffer.getShort().toInt()} min")

            // --- BLOQUE DE INGENIERÍA (Offset 32) ---
            binding.editTextVolumeLiters.setText("${buffer.getFloat().format(2)} L")
            binding.editTextVolumeLitersUncal.setText("${buffer.getFloat().format(2)} L")
            binding.editTextTemperatureUncal.setText("${buffer.getFloat().format(1)} °C")
            binding.editTextFlowUncal.setText("${buffer.getFloat().format(2)} L/h")

            // TToF, DToF, StdDev, Time, ChipTemp, Lux - Asumimos 4 bytes Float c/u, Time 4 bytes Int
            binding.editTextTtof.setText(buffer.getFloat().format(4))
            binding.editTextDtof.setText(buffer.getFloat().format(4))
            binding.editTextStdDev.setText(buffer.getFloat().format(4))
            binding.editTextTime.setText("${buffer.getInt()} sec")
            binding.editTextChipTemperature.setText("${buffer.getFloat().format(1)} °C")
            binding.editTextLux.setText(buffer.getFloat().format(0))

        } catch (e: Exception) {
            Log.e(TAG, "Error CRÍTICO al parsear los datos. Revisar offsets y tamaño del payload: ${e.message}", e)
            binding.editTextSerialNumber.setText("ERROR: No se pudo parsear el payload. RAW: ${data.toHexString()}")
        }
    }

    /**
     * Limpia todos los campos de texto del formulario.
     */
    private fun clearAllFields() {
        binding.editTextSerialNumber.setText("")
        binding.editTextFirmwareVersion.setText("")
        binding.editTextLastConfigDate.setText("")
        binding.editTextVolume.setText("")
        binding.editTextFlow.setText("")
        binding.editTextTemperature.setText("")
        binding.editTextBattery.setText("")
        binding.editTextStatus.setText("")
        binding.editTextDirectFlowPeriod.setText("")
        binding.editTextReverseFlowPeriod.setText("")
        binding.editTextNoFlowPeriod.setText("")
        binding.editTextLeakagePeriod.setText("")
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
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

/** Función de utilidad para convertir ByteArray a String Hexadecimal */
fun ByteArray.toHexString() = joinToString(separator = " ") {
    String.format("%02X", it)
}

/** Función de utilidad para formatear un Float con precisión */
fun Float.format(digits: Int) = "%.${digits}f".format(this)
