package com.example.nfc_reader_01.ui.configuration

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.nfc_reader_01.SharedNfcViewModel
import com.example.nfc_reader_01.databinding.FragmentConfigurationBinding
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ConfigurationFragment : Fragment() {

    // Comandos de ejemplo para NFC
    private companion object {
        // Renombrado para claridad (el comando real será 0x05)
        const val COMMAND_READ_CONFIG = 0x04.toByte()
        const val COMMAND_WRITE_CONFIG = 0x05.toByte()
        const val COMMAND_FACTORY_RESET = 0x0A.toByte() // Comando genérico para reset
        const val FLOAT_COUNT = 11 // K_meter (1) + Temp Cal (4) + FC Errors (6)
        const val CONFIG_BYTE_SIZE = FLOAT_COUNT * 4 // 11 floats * 4 bytes/float = 44 bytes
    }

    private val sharedNfcViewModel: SharedNfcViewModel by activityViewModels()

    private var _binding: FragmentConfigurationBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Asumiendo que tu archivo XML es ahora fragment_configuration.xml
        _binding = FragmentConfigurationBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Inicializa la fecha de última configuración con un valor por defecto
        binding.textViewLastConfigurationDate.setText("No configurado")

        setupListeners()
        observeViewModel()
    }

    /**
     * Configura los listeners para los botones de acción (Leer, Guardar, Reset).
     */
    private fun setupListeners() {
        // --- 1. GUARDAR CONFIGURACIÓN (Escribir en TAG) ---
        binding.saveConfigButton.setOnClickListener {

            // 1. Obtener el ByteArray, que es nullable (ByteArray?).
            val configBytesNullable = serializeConfigDataToBytes()

            // 2. Usar un 'if' explícito para la validación y el control de flujo.
            if (configBytesNullable == null || configBytesNullable.size != CONFIG_BYTE_SIZE) {
                Toast.makeText(
                    requireContext(),
                    "Error: Asegúrese de que todos los ${FLOAT_COUNT} campos numéricos son válidos (requerido: $CONFIG_BYTE_SIZE bytes).",
                    Toast.LENGTH_LONG
                ).show()
                // Salir de la lambda del click listener
                return@setOnClickListener
            }

            // 3. Smart-cast explícito: Asignamos a una variable no nula después de la validación.
            val configBytes: ByteArray = configBytesNullable

            // 4. Establecer los datos y ENVIAR el comando de escritura
            sharedNfcViewModel.setConfigData(configBytes) // Pasa el valor no nulo validado
            // CORRECCIÓN CLAVE: Usar sendCommand en lugar de setCommandToSend
            sharedNfcViewModel.sendCommand(COMMAND_WRITE_CONFIG)

            // Actualizar la fecha y mostrar mensaje
            val timestamp = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date())
            binding.textViewLastConfigurationDate.setText(timestamp)

            Toast.makeText(
                requireContext(),
                "Comando ${COMMAND_WRITE_CONFIG.toHexString()} (Escribir) y $CONFIG_BYTE_SIZE bytes listos. ¡Ahora escanee el TAG!",
                Toast.LENGTH_LONG
            ).show()
        }

        // --- 2. LEER CONFIGURACIÓN (Leer del TAG) ---
        binding.readConfigButton.setOnClickListener {
            // Este es el punto que requiere que setConfigData acepte ByteArray?
            sharedNfcViewModel.setConfigData(null) // Limpiar datos de escritura previos
            // CORRECCIÓN CLAVE: Usar sendCommand en lugar de setCommandToSend
            sharedNfcViewModel.sendCommand(COMMAND_READ_CONFIG)

            Toast.makeText(
                requireContext(),
                "Comando ${COMMAND_READ_CONFIG.toHexString()} (Leer) listo. ¡Ahora escanee el TAG!",
                Toast.LENGTH_LONG
            ).show()
        }

        // --- 3. RESTABLECER CONFIGURACIÓN DE FÁBRICA ---
        binding.factoryResetButton.setOnClickListener {
            sharedNfcViewModel.setConfigData(null) // No se envían datos
            // CORRECCIÓN CLAVE: Usar sendCommand en lugar de setCommandToSend
            sharedNfcViewModel.sendCommand(COMMAND_FACTORY_RESET)

            Toast.makeText(
                requireContext(),
                "Comando ${COMMAND_FACTORY_RESET.toHexString()} (Reset de Fábrica) listo. ¡Ahora escanee el TAG!",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    /**
     * Serializa los 11 campos de texto flotantes del UI a un único ByteArray de 44 bytes.
     * @return ByteArray con la configuración o null si hay un error de formato.
     */
    private fun serializeConfigDataToBytes(): ByteArray? {
        // 1. Lista ordenada de todos los EditTexts que contienen valores flotantes
        val inputFields = listOf(
            binding.editTextKMeter,
            binding.editTextTempRawLow, binding.editTextTempRawHigh,
            binding.editTextTempCalLow, binding.editTextTempCalHigh,
            binding.editTextFcqQ1Error, binding.editTextFcqQ2Error,
            binding.editTextFcq035Error, binding.editTextFcq100Error,
            binding.editTextFcq10LmError, binding.editTextFcqQ3Error
        )

        val floats = mutableListOf<Float>()

        // 2. Intentar parsear todos los campos
        for (field in inputFields) {
            val text = field.text.toString().trim()
            val floatValue = text.toFloatOrNull()
            if (floatValue == null) {
                // Marcar el campo con error si no es un número válido
                field.error = "Valor inválido"
                return null
            }
            // Limpiar error si la validación es exitosa para este campo.
            field.error = null
            floats.add(floatValue)
        }

        // 3. Serializar los floats a bytes usando ByteBuffer
        val buffer = ByteBuffer.allocate(CONFIG_BYTE_SIZE)
        buffer.order(ByteOrder.BIG_ENDIAN) // Asumimos Big Endian. Cambiar a LITTLE_ENDIAN si es necesario

        floats.forEach { buffer.putFloat(it) }

        return buffer.array()
    }


    /**
     * Deserializa los datos de configuración recibidos del TAG y los vuelca al UI.
     * @param bytes El ByteArray recibido del TAG.
     */
    private fun deserializeConfigDataFromBytes(bytes: ByteArray) {
        if (bytes.size != CONFIG_BYTE_SIZE) {
            Toast.makeText(requireContext(), "Error: Tamaño de datos recibido incorrecto (${bytes.size} bytes).", Toast.LENGTH_LONG).show()
            return
        }

        val buffer = ByteBuffer.wrap(bytes)
        buffer.order(ByteOrder.BIG_ENDIAN) // Debe coincidir con el orden de escritura

        val inputFields = listOf(
            binding.editTextKMeter,
            binding.editTextTempRawLow, binding.editTextTempRawHigh,
            binding.editTextTempCalLow, binding.editTextTempCalHigh,
            binding.editTextFcqQ1Error, binding.editTextFcqQ2Error,
            binding.editTextFcq035Error, binding.editTextFcq100Error,
            binding.editTextFcq10LmError, binding.editTextFcqQ3Error
        )

        // Deserializar y actualizar los campos
        for (field in inputFields) {
            // Lee un float y lo convierte a String con 4 decimales
            val floatValue = try {
                buffer.getFloat()
            } catch (e: Exception) {
                // Manejar error de lectura si el buffer se queda sin datos
                Toast.makeText(requireContext(), "Error al deserializar datos: ${e.message}", Toast.LENGTH_LONG).show()
                return
            }
            field.setText(String.format(Locale.getDefault(), "%.4f", floatValue))
        }

        // Opcional: Actualizar la fecha de última lectura
        binding.textViewLastConfigurationDate.setText("Leído del TAG (${bytes.size} bytes)")
    }


    /**
     * Observa el estado del configData en el ViewModel.
     * Este Flow se actualiza cuando una lectura NFC exitosa devuelve un ByteArray.
     */
    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                sharedNfcViewModel.configData.collect { bytes ->
                    if (bytes != null && bytes.size == CONFIG_BYTE_SIZE) {
                        // Si se reciben bytes, se asume que es una respuesta de lectura exitosa
                        deserializeConfigDataFromBytes(bytes)
                        Toast.makeText(requireContext(), "Configuración leída y cargada exitosamente.", Toast.LENGTH_SHORT).show()
                    }
                    // Si bytes es null, no se hace nada (puede ser al inicio o después de un comando de escritura)
                }
            }
        }
    }

    // Helper para convertir Byte a String hexadecimal (para los Toast de comandos)
    private fun Byte.toHexString() = String.format("%02X", this)

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
