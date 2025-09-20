package com.example.nfc_reader_01.ui.dashboard

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
import com.example.nfc_reader_01.NdefRecordData
import com.example.nfc_reader_01.SharedNfcViewModel
import com.example.nfc_reader_01.databinding.FragmentDashboardBinding
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    private lateinit var sharedNfcViewModel: SharedNfcViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
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
        // Nuevo botón para actualizar el timestamp en memoria
        binding.timestampButton.setOnClickListener {
            // Obtener los datos actuales del ViewModel
            val currentRecords = sharedNfcViewModel.ndefRecords.value

            // Crear el nuevo timestamp
            val dateFormat = SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.getDefault())
            val newTimestamp = dateFormat.format(Date())
            val timestampBytes = newTimestamp.toByteArray(StandardCharsets.UTF_8)

            // Actualizar el registro de proceso
            val processData = currentRecords?.processData?.let {
                val buffer = ByteBuffer.wrap(it)
                val volumen = buffer.getInt()
                val caudal = buffer.getInt()
                val temperatura = buffer.getInt()
                val estado = buffer.getInt()

                ByteBuffer.allocate(4 + 4 + 4 + 4 + timestampBytes.size).apply {
                    putInt(volumen)
                    putInt(caudal)
                    putInt(temperatura)
                    putInt(estado)
                    put(timestampBytes)
                }.array()

            } ?: run {
                // Si no hay datos de proceso, crear un array de bytes por defecto con el nuevo timestamp
                ByteBuffer.allocate(4 + 4 + 4 + 4 + timestampBytes.size).apply {
                    putInt(0)
                    putInt(0)
                    putInt(0)
                    putInt(0)
                    put(timestampBytes)
                }.array()
            }

            // Crear una nueva instancia de NdefRecordData con el timestamp actualizado
            val updatedRecords = NdefRecordData(
                identityData = currentRecords?.identityData,
                processData = processData,
                configData = currentRecords?.configData
            )

            // Actualizar el ViewModel con los datos modificados
            sharedNfcViewModel.setNdefRecords(updatedRecords.identityData, updatedRecords.processData, updatedRecords.configData)

            // El observador de LiveData se encargará de actualizar la interfaz de usuario.
            Toast.makeText(context, "Fecha de última configuración actualizada en memoria. Presione 'Guardar Configuración' en la ventana de configuración para guardar.", Toast.LENGTH_LONG).show()
        }
    }

    private fun setupObservers() {
        sharedNfcViewModel.ndefRecords.observe(viewLifecycleOwner) { records ->
            // Lectura de los datos de Identidad
            val identityData = records.identityData
            if (identityData != null && identityData.size >= 18) {
                try {
                    val buffer = ByteBuffer.wrap(identityData)
                    val id = buffer.getInt()
                    val value = buffer.getInt()
                    val dateBytes = ByteArray(10)
                    buffer.get(dateBytes)
                    val lastConfigDate = String(dateBytes, StandardCharsets.UTF_8).trim()

                    binding.editTextSerialNumber.setText(id.toString())
                    binding.editTextFirmwareVersion.setText(value.toString())
                    binding.editTextLastConfigDate.setText(lastConfigDate)
                } catch (e: Exception) {
                    Log.e("DashboardFragment", "Error reading identity data: ${e.message}")
                    binding.editTextSerialNumber.setText("Error")
                    binding.editTextFirmwareVersion.setText("Error")
                    binding.editTextLastConfigDate.setText("Error")
                }
            } else {
                binding.editTextSerialNumber.setText("No hay datos")
                binding.editTextFirmwareVersion.setText("No hay datos")
                binding.editTextLastConfigDate.setText("No hay datos")
            }

            // Lectura de los datos de Proceso
            val processData = records.processData
            if (processData != null && processData.size >= 35) {
                try {
                    val buffer = ByteBuffer.wrap(processData)
                    val volumen = buffer.getInt()
                    val caudal = buffer.getInt()
                    val temperatura = buffer.getInt()
                    val estado = buffer.getInt()
                    val dateBytes = ByteArray(19)
                    buffer.get(dateBytes)
                    val timestamp = String(dateBytes, StandardCharsets.UTF_8).trim()

                    binding.editTextVolumen.setText(volumen.toString())
                    binding.editTextCaudal.setText(caudal.toString())
                    binding.editTextTemperatura.setText(temperatura.toString())
                    binding.editTextStatus.setText(estado.toString())
                    binding.editTextTimestamp.setText(timestamp)
                } catch (e: Exception) {
                    Log.e("DashboardFragment", "Error reading process data: ${e.message}")
                    binding.editTextVolumen.setText("Error")
                    binding.editTextCaudal.setText("Error")
                    binding.editTextTemperatura.setText("Error")
                    binding.editTextStatus.setText("Error")
                    binding.editTextTimestamp.setText("Error")
                }
            } else {
                binding.editTextVolumen.setText("No hay datos")
                binding.editTextCaudal.setText("No hay datos")
                binding.editTextTemperatura.setText("No hay datos")
                binding.editTextStatus.setText("No hay datos")
                binding.editTextTimestamp.setText("No hay datos")
            }
        }
    }
}

