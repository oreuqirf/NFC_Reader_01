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
import kotlinx.coroutines.launch
import kotlin.ExperimentalStdlibApi

/**
 * Fragmento para mostrar los datos leídos de la etiqueta NFC (respuesta 0x8X) en campos estructurados.
 */
@OptIn(ExperimentalStdlibApi::class) // <-- ANOTACIÓN APLICADA PARA USAR Float.fromBits()
class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null

    // Esta propiedad solo es válida entre onCreateView y onDestroyView.
    private val binding get() = _binding!!

    // Inyecta el ViewModel compartido a nivel de actividad
    private val sharedViewModel: SharedNfcViewModel by activityViewModels()

    // Códigos de comando definidos por el usuario
    private val CMD_READ_IDENTITY: Byte = 0x01
    private val CMD_READ_PROCESS: Byte = 0x02
    private val CMD_READ_CONFIG: Byte = 0x03
    // private val CMD_WRITE_CONFIG: Byte = 0x04 // Not used in this fragment
    private val CMD_READ_ENGINEERING: Byte = 0x05

    // --- TAMAÑOS ÚTILES ESPERADOS DEL PAYLOAD (asumiendo que el resto son bytes de relleno/padding) ---
    // El TAG envía 127 bytes, pero solo esta porción es la que contiene datos útiles.
    private val IDENTITY_PAYLOAD_SIZE = 12 // 12 bytes para ID, FW Version y Timestamp
    private val PROCESS_PAYLOAD_SIZE = 36 // 36 bytes para 8 campos de 4 bytes (Float/Int)
    // CONFIG_BYTE_SIZE está importado (asumido 96 bytes)
    private val ENGINEERING_PAYLOAD_SIZE = 44 // 44 bytes para 11 campos de 4 bytes (Float/Int)
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

    /**
     * Configura los listeners para los botones de solicitud de datos.
     */
    private fun setupListeners() {
        binding.requestIdentityButton.setOnClickListener {
            sharedViewModel.sendCommand(CMD_READ_IDENTITY)
            Log.d(TAG, "Solicitando Identidad (0x${CMD_READ_IDENTITY.toHexString()})")
            // Mensaje mejorado
            sharedViewModel.setUiMessage("Comando 0x${CMD_READ_IDENTITY.toHexString()} (Identidad) preparado. Acerque el TAG.")
        }

        binding.requestProcessButton.setOnClickListener {
            sharedViewModel.sendCommand(CMD_READ_PROCESS)
            Log.d(TAG, "Solicitando Proceso (0x${CMD_READ_PROCESS.toHexString()})")
            // Mensaje mejorado
            sharedViewModel.setUiMessage("Comando 0x${CMD_READ_PROCESS.toHexString()} (Proceso) preparado. Acerque el TAG.")
        }

        binding.requestConfigButton.setOnClickListener {
            sharedViewModel.sendCommand(CMD_READ_CONFIG)
            Log.d(TAG, "Solicitando Configuración (0x${CMD_READ_CONFIG.toHexString()})")
            // Mensaje mejorado
            sharedViewModel.setUiMessage("Comando 0x${CMD_READ_CONFIG.toHexString()} (Configuración) preparado. Acerque el TAG.")
        }

        binding.requestEngineeringButton.setOnClickListener {
            sharedViewModel.sendCommand(CMD_READ_ENGINEERING)
            Log.d(TAG, "Solicitando Ingeniería (0x${CMD_READ_ENGINEERING.toHexString()})")
            // Mensaje mejorado
            sharedViewModel.setUiMessage("Comando 0x${CMD_READ_ENGINEERING.toHexString()} (Ingeniería) preparado. Acerque el TAG.")
        }
    }

    /**
     * Configura los observadores para los flujos de datos del ViewModel, llamando
     * a la función de parseo adecuada para cada respuesta.
     */
    private fun setupObservers() {
        // Observa el mensaje general del ViewModel para feedback
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                sharedViewModel.uiMessage.collect { message ->
                    try {
                        // Aquí se muestra el mensaje en la UI del Dashboard (no en un Toast)
                        binding.editTextMessage.setText(message)
                    } catch (e: Exception) {
                        Log.d(TAG, "Estado NFC: $message")
                    }
                }
            }
        }

        // --- OBSERVADOR 0x81: Identidad (identityResponseData) ---
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                sharedViewModel.identityResponseData.collect { data ->
                    if (data != null && data.isNotEmpty()) {
                        Log.i(TAG, "Datos 0x81 (Identidad) recibidos: ${data.size} bytes. Hex: ${data.toHexString()}")

                        if (data.size >= IDENTITY_PAYLOAD_SIZE) {
                            // Extraer solo los bytes útiles, ignorando el padding al final
                            val usefulBytes = data.sliceArray(0 until IDENTITY_PAYLOAD_SIZE)
                            Log.d(TAG, "Procesando ${usefulBytes.size} bytes útiles para Identidad.")
                            parseIdentityData(usefulBytes)
                            // Mensaje mejorado: éxito
                            sharedViewModel.setUiMessage("Respuesta 0x81: Datos de Identidad cargados con éxito.")
                        } else {
                            Log.e(TAG, "ERROR: Datos de Identidad incompletos. Esperado: $IDENTITY_PAYLOAD_SIZE, Recibido: ${data.size}.")
                            // Mensaje mejorado: error
                            sharedViewModel.setUiMessage("ERROR 0x81: Respuesta incompleta. (${data.size}/${IDENTITY_PAYLOAD_SIZE} bytes).")
                        }
                    }
                }
            }
        }

        // --- OBSERVADOR 0x82: Proceso (processResponseData) ---
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                sharedViewModel.processResponseData.collect { data ->
                    if (data != null && data.isNotEmpty()) {
                        Log.i(TAG, "Datos 0x82 (Proceso) recibidos: ${data.size} bytes. Hex: ${data.toHexString()}")

                        if (data.size >= PROCESS_PAYLOAD_SIZE) {
                            // Extraer solo los bytes útiles, ignorando el padding al final
                            val usefulBytes = data.sliceArray(0 until PROCESS_PAYLOAD_SIZE)
                            Log.d(TAG, "Procesando ${usefulBytes.size} bytes útiles para Proceso.")
                            parseProcessData(usefulBytes)
                            // Mensaje mejorado: éxito
                            sharedViewModel.setUiMessage("Respuesta 0x82: Datos de Proceso actualizados.")
                        } else {
                            Log.e(TAG, "ERROR: Datos de Proceso incompletos. Esperado: $PROCESS_PAYLOAD_SIZE, Recibido: ${data.size}.")
                            // Mensaje mejorado: error
                            sharedViewModel.setUiMessage("ERROR 0x82: Respuesta incompleta. (${data.size}/${PROCESS_PAYLOAD_SIZE} bytes).")
                        }
                    }
                }
            }
        }

        // --- OBSERVADOR 0x83: Configuración (configurationResponseData) ---
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                sharedViewModel.configurationResponseData.collect { data ->
                    if (data != null && data.isNotEmpty()) {
                        Log.i(TAG, "Datos 0x83 (Configuración) recibidos: ${data.size} bytes. Hex: ${data.toHexString()}")

                        if (data.size >= CONFIG_BYTE_SIZE) {
                            // Extraer solo los bytes útiles, ignorando el padding al final
                            val usefulBytes = data.sliceArray(0 until CONFIG_BYTE_SIZE)
                            Log.d(TAG, "Procesando ${usefulBytes.size} bytes útiles para Configuración.")
                            parseConfigData(usefulBytes)
                            // Mensaje mejorado: éxito
                            sharedViewModel.setUiMessage("Respuesta 0x83: Datos de Configuración cargados correctamente.")
                            sharedViewModel.clearConfigurationResponseData()
                        } else {
                            Log.e(TAG, "ERROR: Datos de Configuración incompletos. Esperado: $CONFIG_BYTE_SIZE, Recibido: ${data.size}.")
                            // Mensaje mejorado: error
                            sharedViewModel.setUiMessage("ERROR 0x83: Respuesta incompleta. (${data.size}/${CONFIG_BYTE_SIZE} bytes).")
                            sharedViewModel.clearConfigurationResponseData()
                        }
                    }
                }
            }
        }

        // --- OBSERVADOR 0x85: Ingeniería (engineeringResponseData) ---
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                sharedViewModel.engineeringResponseData.collect { data ->
                    if (data != null && data.isNotEmpty()) {
                        Log.i(TAG, "Datos 0x85 (Ingeniería) recibidos: ${data.size} bytes. Hex: ${data.toHexString()}")

                        if (data.size >= ENGINEERING_PAYLOAD_SIZE) {
                            // Extraer solo los bytes útiles, ignorando el padding al final
                            val usefulBytes = data.sliceArray(0 until ENGINEERING_PAYLOAD_SIZE)
                            Log.d(TAG, "Procesando ${usefulBytes.size} bytes útiles para Ingeniería.")
                            parseEngineeringData(usefulBytes)
                            // Mensaje mejorado: éxito
                            sharedViewModel.setUiMessage("Respuesta 0x85: Datos de Ingeniería cargados.")
                        } else {
                            Log.e(TAG, "ERROR: Datos de Ingeniería incompletos. Esperado: $ENGINEERING_PAYLOAD_SIZE, Recibido: ${data.size}.")
                            // Mensaje mejorado: error
                            sharedViewModel.setUiMessage("ERROR 0x85: Respuesta incompleta. (${data.size}/${ENGINEERING_PAYLOAD_SIZE} bytes).")
                        }
                    }
                }
            }
        }

        // --- OBSERVADOR CORREGIDO: SharedFlow de Estado de Escritura (writeStatus) para Toast ---
        // Este observador se activa SOLAMENTE cuando el ViewModel emite un nuevo evento.
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                sharedViewModel.writeStatus.collect { status ->
                    // 'status' es un String (no nullable) y no requiere el chequeo '!= null'

                    Log.i(TAG, "Estado de Escritura (0x84 o similar) recibido: $status")

                    // 1. Mostrar el Toast con el mensaje de éxito/reset
                    // Toast.makeText(context, status, Toast.LENGTH_LONG).show()

                    // 2. Opcional: Actualizar el mensaje de la UI principal con el estado.
                    sharedViewModel.setUiMessage(status)

                    // NO SE REQUIERE setWriteStatus(null) porque es SharedFlow
                }
            }
        }
    }

    /**
     * Parsea SOLO los datos de IDENTIDAD (Respuesta 0x81).
     * Llama a la función de parseo centralizada en el objeto NfcDataParser.
     */
    private fun parseIdentityData(data: ByteArray) {
        try {
            // Llama a la función del objeto estático
            val identity = NfcDataParser.parseIdentityData(data)

            // Rellenar la UI
            binding.editTextSerialNumber.setText(identity.deviceId.toString())
            binding.editTextFirmwareVersion.setText(identity.firmwareVersion)

            // --- ACTUALIZACIÓN CORRECTA DE LA FECHA DE CONFIGURACIÓN (Solo en 0x81) ---
            binding.editTextLastConfigDate.setText(identity.lastConfigurationDate)

        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Error de tamaño de Payload (0x81): ${e.message}")
            binding.editTextSerialNumber.setText("ERROR: Tamaño: ${data.size} bytes. Falló parseo.")
        } catch (e: Exception) {
            Log.e(TAG, "Error CRÍTICO al parsear datos de Identidad: ${e.message}", e)
            binding.editTextSerialNumber.setText("ERROR: Parseo 0x81 fallido.")
        }

        // --- Limpieza de otros grupos de datos ---
        clearProcessValueFields()
        clearFlowPeriodFields()
        clearEngineeringFields()
    }


    /**
     * Parsea SOLO los datos de PROCESO (Respuesta 0x82).
     * Nota: Convierte los Ints (raw 4-byte fields) a Float para la visualización.
     */
    private fun parseProcessData(data: ByteArray) {
        try {
            val process = NfcDataParser.parseProcessData(data)

            // Conversión de Int (bits) a Float para campos de punto flotante
            val floatVolume = Float.fromBits(process.volume)
            val floatFlow = Float.fromBits(process.flowRate)
            val floatTemp = Float.fromBits(process.temperature)

            // Rellenar la UI
            binding.editTextVolume.setText("${floatVolume.format(3)} m³")
            binding.editTextFlow.setText("${floatFlow.format(2)} L/h")
            binding.editTextTemperature.setText("${floatTemp.format(1)} °C")

            // Campos Int puros (Batería, Status)
            binding.editTextBattery.setText("${process.battery}%")
            binding.editTextStatus.setText("0x${process.statusFlags.toHexString()}")

            // Periodos (Int puros)
            binding.editTextDirectFlowPeriod.setText("${process.directFlowPeriod} sec")
            binding.editTextReverseFlowPeriod.setText("${process.reverseFlowPeriod} sec")
            binding.editTextNoFlowPeriod.setText("${process.noFlowPeriod} sec")
            binding.editTextLeakagePeriod.setText("${process.leakageFlowPeriod} sec")

        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Error de tamaño de Payload (0x82): ${e.message}")
            binding.editTextVolume.setText("ERROR: Tamaño: ${data.size} bytes. Falló parseo.")
        } catch (e: Exception) {
            Log.e(TAG, "Error al parsear datos de Proceso: ${e.message}", e)
            binding.editTextVolume.setText("ERROR: Parseo 0x82 fallido.")
        }

        // --- Limpieza de otros grupos de datos ---
        clearIdentityFields()
        // Note: Flow periods are set above, so we don't clear them here.
        clearEngineeringFields()
    }

    /**
     * Parsea SOLO los datos de CONFIGURACIÓN (Respuesta 0x83).
     */
    private fun parseConfigData(data: ByteArray) {
        try {
            val config = NfcDataParser.parseConfigData(data)

            // Asumimos que la UI tiene campos para todos los 23 floats de configuración...
            // Por simplicidad, se usan solo placeholders, pero se limpian los campos de Proceso
            // que podrían solaparse.

        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Error de tamaño de Payload (0x83): ${e.message}")
            binding.editTextDirectFlowPeriod.setText("ERROR: Tamaño: ${data.size} bytes. Falló parseo.")
        } catch (e: Exception) {
            Log.e(TAG, "Error al parsear datos de Configuración: ${e.message}", e)
            binding.editTextDirectFlowPeriod.setText("ERROR: Parseo 0x83 fallido.")
        }

        // Se limpia la fecha de configuración, ya que 0x83 no debe actualizarla y el resto se limpia.
        clearIdentityFields(keepLastConfigDate = false)
        clearProcessValueFields()
        clearFlowPeriodFields() // Limpia explícitamente los campos de Periodo
        clearEngineeringFields()
    }


    /**
     * Parsea SOLO los datos de INGENIERÍA (Respuesta 0x85).
     * Nota: Convierte los Ints (raw 4-byte fields) a Float para la visualización.
     */
    private fun parseEngineeringData(data: ByteArray) {
        try {
            val engineering = NfcDataParser.parseEngineeringData(data)

            // Conversión de Int (bits) a Float para campos de punto flotante
            val floatVolL = Float.fromBits(engineering.volumeLiters)
            val floatVolLU = Float.fromBits(engineering.volumeLitersUncal)
            val floatTempU = Float.fromBits(engineering.temperatureUncal)
            val floatFlowU = Float.fromBits(engineering.flowUncal)
            val floatTtof = Float.fromBits(engineering.ttof)
            val floatDtof = Float.fromBits(engineering.dtof)
            val floatStdDev = Float.fromBits(engineering.stdDev)
            // engineering.time ya es Int y se usa directamente
            val floatChipTemp = Float.fromBits(engineering.chipTemperature)
            val floatLux = Float.fromBits(engineering.lux)

            // Rellenar la UI
            binding.editTextVolumeLiters.setText("${floatVolL.format(2)} L")
            binding.editTextVolumeLitersUncal.setText("${floatVolLU.format(2)} L")
            binding.editTextTemperatureUncal.setText("${floatTempU.format(1)} °C")
            binding.editTextFlowUncal.setText("${floatFlowU.format(2)} L/h")
            binding.editTextTtof.setText(floatTtof.format(4))
            binding.editTextDtof.setText(floatDtof.format(4))
            binding.editTextStdDev.setText(floatStdDev.format(4))
            binding.editTextTime.setText("${engineering.time} sec") // Este es un Int puro
            binding.editTextChipTemperature.setText("${floatChipTemp.format(1)} °C")
            binding.editTextLux.setText("${floatLux.format(0)} mV")
            binding.editTextRakFrameCounter.setText("${engineering.rakFrameCounter}") // Este es un Int puro

        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Error de tamaño de Payload (0x85): ${e.message}")
            binding.editTextVolumeLiters.setText("ERROR: Tamaño: ${data.size} bytes. Falló parseo.")
        } catch (e: Exception) {
            Log.e(TAG, "Error al parsear datos de Ingeniería: ${e.message}", e)
            binding.editTextVolumeLiters.setText("ERROR: Parseo 0x85 fallido.")
        }

        // --- Limpieza de otros grupos de datos ---
        clearIdentityFields()
        clearProcessValueFields()
        clearFlowPeriodFields()
    }

    // --- Funciones auxiliares para limpiar campos (Refactorizadas) ---

    /** Limpia los campos de Identidad (ID, FW, y opcionalmente Fecha) */
    private fun clearIdentityFields(keepLastConfigDate: Boolean = false) {
        binding.editTextSerialNumber.setText("")
        binding.editTextFirmwareVersion.setText("")
        if (!keepLastConfigDate) {
            binding.editTextLastConfigDate.setText("")
        }
    }

    /** Limpia los campos de valores de Proceso (Volume, Flow, Temp, Battery, Status) */
    private fun clearProcessValueFields() {
        binding.editTextVolume.setText("")
        binding.editTextFlow.setText("")
        binding.editTextTemperature.setText("")
        binding.editTextBattery.setText("")
        binding.editTextStatus.setText("")
    }

    /** Limpia los campos de Periodo de Flujo (Compartidos/Relevantes para Proceso y Config) */
    private fun clearFlowPeriodFields() {
        binding.editTextDirectFlowPeriod.setText("")
        binding.editTextReverseFlowPeriod.setText("")
        binding.editTextNoFlowPeriod.setText("")
        binding.editTextLeakagePeriod.setText("")
    }

    /** Limpia los campos de Ingeniería */
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
        // Limpiamos los placeholders de configuración avanzada si existen
        // binding.editTextConfigKMeter.setText("")
        // binding.editTextConfigHighTempCorrected.setText("")
        // binding.editTextConfigFlowQ1.setText("")
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val TAG = "DashboardFragment"
    }
}

/** Función de utilidad para convertir ByteArray a String Hexadecimal */
fun ByteArray.toHexString() = joinToString(separator = " ") {
    String.format("%02X", it)
}

/** Función de utilidad para convertir un Int a String Hexadecimal (ej. para Status Flags) */
fun Int.toHexString() = String.format("%08X", this) // 4 bytes = 8 dígitos

/** Función de utilidad para formatear un Float con precisión */
fun Float.format(digits: Int) = "%.${digits}f".format(this)
