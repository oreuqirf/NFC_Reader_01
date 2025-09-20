package com.example.nfc_reader_01.ui.configuration

import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.nfc_reader_01.SharedNfcViewModel
import com.example.nfc_reader_01.databinding.FragmentConfigurationBinding
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

class ConfigurationFragment : Fragment() {

    private var _binding: FragmentConfigurationBinding? = null
    private val binding get() = _binding!!

    private lateinit var sharedNfcViewModel: SharedNfcViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentConfigurationBinding.inflate(inflater, container, false)
        sharedNfcViewModel = ViewModelProvider(requireActivity()).get(SharedNfcViewModel::class.java)

        setupListeners()
        setupObservers()

        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }


    private fun setupListeners() {
        binding.saveConfigButton.setOnClickListener {
            // 1. Obtener los registros actuales del ViewModel
            val currentRecords = sharedNfcViewModel.ndefRecords.value

            // 2. Obtener los datos de configuración de los EditText
            val kMeter = binding.editTextKMeter.text.toString().toFloatOrNull() ?: 0.0f
            val tempRawLow = binding.editTextTempRawLow.text.toString().toFloatOrNull() ?: 0.0f
            val tempRawHigh = binding.editTextTempRawHigh.text.toString().toFloatOrNull() ?: 0.0f
            val tempCalLow = binding.editTextTempCalLow.text.toString().toFloatOrNull() ?: 0.0f
            val tempCalHigh = binding.editTextTempCalHigh.text.toString().toFloatOrNull() ?: 0.0f
            val fcqQ1 = binding.editTextFcqQ1.text.toString().toFloatOrNull() ?: 0.0f
            val fcqQ2 = binding.editTextFcqQ2.text.toString().toFloatOrNull() ?: 0.0f
            val fcq035 = binding.editTextFcq035.text.toString().toFloatOrNull() ?: 0.0f
            val fcq100 = binding.editTextFcq100.text.toString().toFloatOrNull() ?: 0.0f
            val fcq10lm = binding.editTextFcq10Lm.text.toString().toFloatOrNull() ?: 0.0f
            val fcqQ3 = binding.editTextFcqQ3.text.toString().toFloatOrNull() ?: 0.0f

            // 3. Crear el nuevo registro de configuración con el tipo MIME correcto
            val configData = ByteBuffer.allocate(44).apply {
                putFloat(kMeter)
                putFloat(tempRawLow)
                putFloat(tempRawHigh)
                putFloat(tempCalLow)
                putFloat(tempCalHigh)
                putFloat(fcqQ1)
                putFloat(fcqQ2)
                putFloat(fcq035)
                putFloat(fcq100)
                putFloat(fcq10lm)
                putFloat(fcqQ3)
            }.array()
            // Cambio del tipo MIME para que coincida con lo que MainActivity espera
            val configRecord = NdefRecord(NdefRecord.TNF_MIME_MEDIA, "application/vnd.my_app.binary_config".toByteArray(), ByteArray(0), configData)

            // 4. Combinar todos los registros en un solo NdefMessage
            val recordsList = mutableListOf<NdefRecord>()
            currentRecords?.identityData?.let {
                // Cambio del tipo MIME para que coincida con lo que MainActivity espera
                recordsList.add(NdefRecord(NdefRecord.TNF_MIME_MEDIA, "application/vnd.my_app.binary_identity".toByteArray(), ByteArray(0), it))
            }
            currentRecords?.processData?.let {
                // Cambio del tipo MIME para que coincida con lo que MainActivity espera
                recordsList.add(NdefRecord(NdefRecord.TNF_MIME_MEDIA, "application/vnd.my_app.binary_process".toByteArray(), ByteArray(0), it))
            }

            // Reemplazar el registro de configuración si existe, o agregarlo si no
            val existingConfigIndex = recordsList.indexOfFirst { String(it.type) == "application/vnd.my_app.binary_config" }
            if (existingConfigIndex != -1) {
                recordsList[existingConfigIndex] = configRecord
            } else {
                recordsList.add(configRecord)
            }

            if (recordsList.isEmpty()) {
                Toast.makeText(context, "No se puede guardar, no hay registros para escribir.", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            val combinedMessage = NdefMessage(recordsList.toTypedArray())

            // 5. Enviar el mensaje completo para la escritura
            sharedNfcViewModel.setWriteMessageRequest(combinedMessage)
            Toast.makeText(context, "Solicitud de escritura enviada. Aproxime el TAG para guardar todos los cambios.", Toast.LENGTH_LONG).show()
        }

        binding.factoryResetButton.setOnClickListener {
            // 1. Crear un nuevo registro de configuración con valores por defecto (todos 0.0f)
            val defaultConfigData = ByteBuffer.allocate(44).apply {
                putFloat(0.0f) // kMeter
                putFloat(0.0f) // tempRawLow
                putFloat(0.0f) // tempRawHigh
                putFloat(0.0f) // tempCalLow
                putFloat(0.0f) // tempCalHigh
                putFloat(0.0f) // fcqQ1
                putFloat(0.0f) // fcqQ2
                putFloat(0.0f) // fcq035
                putFloat(0.0f) // fcq100
                putFloat(0.0f) // fcq10lm
                putFloat(0.0f) // fcqQ3
            }.array()

            // 2. Obtener los registros actuales del ViewModel
            val currentRecords = sharedNfcViewModel.ndefRecords.value

            // 3. Actualizar el ViewModel con los nuevos datos de configuración.
            // La UI se actualizará automáticamente a través del observador.
            sharedNfcViewModel.setNdefRecords(
                currentRecords?.identityData,
                currentRecords?.processData,
                defaultConfigData
            )
            Toast.makeText(context, "Valores de fábrica restaurados en memoria. Presione 'Guardar Configuración' para transferir los cambios al TAG.", Toast.LENGTH_LONG).show()
        }

        binding.FormatButton.setOnClickListener {
            sharedNfcViewModel.setFormatNewTagRequest(true)
            Toast.makeText(requireContext(), "Acerca el TAG para formatear", Toast.LENGTH_SHORT).show()
        }

    }

    private fun setupObservers() {
        sharedNfcViewModel.ndefRecords.observe(viewLifecycleOwner) { records ->
            val configData = records.configData
            if (configData != null) {
                try {
                    val buffer = ByteBuffer.wrap(configData)
                    binding.editTextKMeter.setText(buffer.getFloat().toString())
                    binding.editTextTempRawLow.setText(buffer.getFloat().toString())
                    binding.editTextTempRawHigh.setText(buffer.getFloat().toString())
                    binding.editTextTempCalLow.setText(buffer.getFloat().toString())
                    binding.editTextTempCalHigh.setText(buffer.getFloat().toString())
                    binding.editTextFcqQ1.setText(buffer.getFloat().toString())
                    binding.editTextFcqQ2.setText(buffer.getFloat().toString())
                    binding.editTextFcq035.setText(buffer.getFloat().toString())
                    binding.editTextFcq100.setText(buffer.getFloat().toString())
                    binding.editTextFcq10Lm.setText(buffer.getFloat().toString())
                    binding.editTextFcqQ3.setText(buffer.getFloat().toString())
                } catch (e: Exception) {
                    Log.e("ConfigurationFragment", "Error reading config data", e)
                    // Limpiar todos los campos si hay un error
                    binding.editTextKMeter.setText("Error")
                    binding.editTextTempRawLow.setText("Error")
                    binding.editTextTempRawHigh.setText("Error")
                    binding.editTextTempCalLow.setText("Error")
                    binding.editTextTempCalHigh.setText("Error")
                    binding.editTextFcqQ1.setText("Error")
                    binding.editTextFcqQ2.setText("Error")
                    binding.editTextFcq035.setText("Error")
                    binding.editTextFcq100.setText("Error")
                    binding.editTextFcq10Lm.setText("Error")
                    binding.editTextFcqQ3.setText("Error")
                }
            } else {
                // Limpiar los campos si no hay datos
                binding.editTextKMeter.setText("No hay datos")
                binding.editTextTempRawLow.setText("No hay datos")
                binding.editTextTempRawHigh.setText("No hay datos")
                binding.editTextTempCalLow.setText("No hay datos")
                binding.editTextTempCalHigh.setText("No hay datos")
                binding.editTextFcqQ1.setText("No hay datos")
                binding.editTextFcqQ2.setText("No hay datos")
                binding.editTextFcq035.setText("No hay datos")
                binding.editTextFcq100.setText("No hay datos")
                binding.editTextFcq10Lm.setText("No hay datos")
                binding.editTextFcqQ3.setText("No hay datos")
            }
        }
    }
}
