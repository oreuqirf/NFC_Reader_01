package com.example.nfc_reader_01.ui.notifications

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.nfc_reader_01.R
import com.example.nfc_reader_01.SharedNfcViewModel
import com.example.nfc_reader_01.databinding.FragmentNotificationsBinding
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NotificationsFragment : Fragment() {

    private var _binding: FragmentNotificationsBinding? = null
    private val binding get() = _binding!!

    private lateinit var sharedNfcViewModel: SharedNfcViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNotificationsBinding.inflate(inflater, container, false)
        sharedNfcViewModel = ViewModelProvider(requireActivity()).get(SharedNfcViewModel::class.java)

        setupObservers()
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // Función de ayuda para convertir un array de bytes a una cadena hexadecimal
    private fun bytesToHex(bytes: ByteArray): String {
        val hexStringBuilder = StringBuilder()
        for (b in bytes) {
            hexStringBuilder.append(String.format("%02X ", b))
        }
        return hexStringBuilder.toString().trim()
    }

    // Función de ayuda para decodificar los datos de la configuración
    private fun decodeConfigData(bytes: ByteArray): String {
        val buffer = ByteBuffer.wrap(bytes)
        val stringBuilder = StringBuilder()

        try {
            // K_meter (Float - 4 bytes)
            val kMeter = buffer.getFloat()
            stringBuilder.append("K_meter: ${bytesToHex(bytes.copyOfRange(0, 4))} -> $kMeter\n")

            // temp_raw_low (Float - 4 bytes)
            val tempRawLow = buffer.getFloat()
            stringBuilder.append("temp_raw_low: ${bytesToHex(bytes.copyOfRange(4, 8))} -> $tempRawLow\n")

            // temp_raw_high (Float - 4 bytes)
            val tempRawHigh = buffer.getFloat()
            stringBuilder.append("temp_raw_high: ${bytesToHex(bytes.copyOfRange(8, 12))} -> $tempRawHigh\n")

            // temp_cal_low (Float - 4 bytes)
            val tempCalLow = buffer.getFloat()
            stringBuilder.append("temp_cal_low: ${bytesToHex(bytes.copyOfRange(12, 16))} -> $tempCalLow\n")

            // temp_cal_high (Float - 4 bytes)
            val tempCalHigh = buffer.getFloat()
            stringBuilder.append("temp_cal_high: ${bytesToHex(bytes.copyOfRange(16, 20))} -> $tempCalHigh\n")

            // fc_q_Q1 (Float - 4 bytes)
            val fcqQ1 = buffer.getFloat()
            stringBuilder.append("fc_q_Q1: ${bytesToHex(bytes.copyOfRange(20, 24))} -> $fcqQ1\n")

            // fc_q_Q2 (Float - 4 bytes)
            val fcqQ2 = buffer.getFloat()
            stringBuilder.append("fc_q_Q2: ${bytesToHex(bytes.copyOfRange(24, 28))} -> $fcqQ2\n")

            // fc_q_0_35 (Float - 4 bytes)
            val fcq035 = buffer.getFloat()
            stringBuilder.append("fc_q_0_35: ${bytesToHex(bytes.copyOfRange(28, 32))} -> $fcq035\n")

            // fc_q_1_00 (Float - 4 bytes)
            val fcq100 = buffer.getFloat()
            stringBuilder.append("fc_q_1_00: ${bytesToHex(bytes.copyOfRange(32, 36))} -> $fcq100\n")

            // fc_q_10_0 (Float - 4 bytes)
            val fcq10lm = buffer.getFloat()
            stringBuilder.append("fc_q_10_0: ${bytesToHex(bytes.copyOfRange(36, 40))} -> $fcq10lm\n")

            // fc_q_Q3 (Float - 4 bytes)
            val fcqQ3 = buffer.getFloat()
            stringBuilder.append("fc_q_Q3: ${bytesToHex(bytes.copyOfRange(40, 44))} -> $fcqQ3\n")

            return stringBuilder.toString()

        } catch (e: Exception) {
            Log.e("NotificationsFragment", "Error reading configuration data: ${e.message}")
            return "Error al leer los datos de configuración"
        }
    }


    private fun setupObservers() {
        sharedNfcViewModel.ndefRecords.observe(viewLifecycleOwner) { records ->
            // Manejar los datos de identidad
            val identityData = records.identityData
            if (identityData != null) {
                try {
                    val buffer = ByteBuffer.wrap(identityData)
                    val serialNumberBytes = ByteArray(4)
                    buffer.get(serialNumberBytes)
                    val serialNumber = ByteBuffer.wrap(serialNumberBytes).getInt()

                    val firmwareVersionBytes = ByteArray(4)
                    buffer.get(firmwareVersionBytes)
                    val firmwareVersion = ByteBuffer.wrap(firmwareVersionBytes).getInt()

                    val lastConfigDateBytes = ByteArray(10)
                    buffer.get(lastConfigDateBytes)
                    val lastConfigDate = String(lastConfigDateBytes, StandardCharsets.UTF_8).trim()

                    val identityText = "Registro N° 1 (Identidad):\n" +
                            "Número de Serie: ${bytesToHex(serialNumberBytes)} -> $serialNumber\n" +
                            "Versión de Firmware: ${bytesToHex(firmwareVersionBytes)} -> $firmwareVersion\n" +
                            "Última Configuración: ${bytesToHex(lastConfigDateBytes)} -> $lastConfigDate"
                    binding.textViewIdentityRecord.text = identityText
                } catch (e: Exception) {
                    Log.e("NotificationsFragment", "Error reading identity data: ${e.message}")
                    binding.textViewIdentityRecord.text = "Registro N° 1 (Identidad):\nError al leer los datos"
                }
            } else {
                binding.textViewIdentityRecord.text = "Registro N° 1 (Identidad):\nNo hay datos de identidad"
            }

            // Manejar los datos de proceso
            val processData = records.processData
            if (processData != null) {
                try {
                    val buffer = ByteBuffer.wrap(processData)
                    val volumenBytes = ByteArray(4)
                    buffer.get(volumenBytes)
                    val volumen = ByteBuffer.wrap(volumenBytes).getInt()

                    val caudalBytes = ByteArray(4)
                    buffer.get(caudalBytes)
                    val caudal = ByteBuffer.wrap(caudalBytes).getInt()

                    val temperaturaBytes = ByteArray(4)
                    buffer.get(temperaturaBytes)
                    val temperatura = ByteBuffer.wrap(temperaturaBytes).getInt()

                    val estadoBytes = ByteArray(4)
                    buffer.get(estadoBytes)
                    val estado = ByteBuffer.wrap(estadoBytes).getInt()

                    val dateBytes = ByteArray(19)
                    buffer.get(dateBytes)
                    val timestamp = String(dateBytes, StandardCharsets.UTF_8).trim()

                    val processText = "Registro N° 2 (Proceso):\n" +
                            "Volumen: ${bytesToHex(volumenBytes)} -> $volumen\n" +
                            "Caudal: ${bytesToHex(caudalBytes)} -> $caudal\n" +
                            "Temperatura: ${bytesToHex(temperaturaBytes)} -> $temperatura\n" +
                            "Estado: ${bytesToHex(estadoBytes)} -> $estado\n" +
                            "Timestamp: ${bytesToHex(dateBytes)} -> $timestamp"
                    binding.textViewProcessRecord.text = processText
                } catch (e: Exception) {
                    Log.e("NotificationsFragment", "Error reading process data: ${e.message}")
                    binding.textViewProcessRecord.text = "Registro N° 2 (Proceso):\nError al leer los datos"
                }
            } else {
                binding.textViewProcessRecord.text = "Registro N° 2 (Proceso):\nNo hay datos de proceso"
            }

            // Manejar los datos de configuración
            val configData = records.configData
            if (configData != null) {
                val configText = "Registro N° 3 (Configuración):\n" + decodeConfigData(configData)
                binding.textViewConfigurationRecord.text = configText
            } else {
                binding.textViewConfigurationRecord.text = "Registro N° 3 (Configuración):\nNo hay datos de configuración"
            }
        }
    }
}

