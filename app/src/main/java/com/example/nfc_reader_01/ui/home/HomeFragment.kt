package com.example.nfc_reader_01.ui.home

import android.content.Intent
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.nfc_reader_01.SharedNfcViewModel
import com.example.nfc_reader_01.databinding.FragmentHomeBinding
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random
import org.json.JSONObject

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var sharedNfcViewModel: SharedNfcViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
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
        binding.openSettingsButton.setOnClickListener {
            val intent = Intent(Settings.ACTION_NFC_SETTINGS)
            startActivity(intent)
        }

        binding.writeButton.setOnClickListener {
            sharedNfcViewModel.nfcTag.value?.let { tag ->
                writeProcessRecord(tag)
            } ?: run {
                Toast.makeText(context, "Aproxime un TAG para escribir.", Toast.LENGTH_SHORT).show()
            }
        }

        binding.newTagButton.setOnClickListener {
            sharedNfcViewModel.nfcTag.value?.let { tag ->
                val newContent = binding.newTagInput.text.toString()
                writeNewRecord(tag, newContent)
            } ?: run {
                Toast.makeText(context, "Aproxime un TAG para escribir.", Toast.LENGTH_SHORT).show()
            }
        }

        // CÓDIGO NUEVO: Listener para el botón de restablecer a fábrica
        binding.factoryResetButton.setOnClickListener {
            sharedNfcViewModel.nfcTag.value?.let { tag ->
                writeFactoryResetRecord(tag)
            } ?: run {
                Toast.makeText(context, "Aproxime un TAG para restablecer a fábrica.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupObservers() {
        sharedNfcViewModel.isNfcEnabled.observe(viewLifecycleOwner) { isEnabled ->
            if (isEnabled) {
                binding.nfcStatusSection.visibility = View.GONE
                binding.mainContent.visibility = View.VISIBLE
            } else {
                binding.nfcStatusSection.visibility = View.VISIBLE
                binding.mainContent.visibility = View.GONE
            }
        }

        sharedNfcViewModel.nfcTag.observe(viewLifecycleOwner) { tag ->
            if (tag != null) {
                binding.statusIcon.setImageResource(com.example.nfc_reader_01.R.drawable.ic_nfc_connected_24)
                binding.statusMessage.text = "TAG detectado. Puede realizar acciones."
            } else {
                binding.statusIcon.setImageResource(com.example.nfc_reader_01.R.drawable.ic_nfc_scan_24)
                binding.statusMessage.text = "Aproxime una etiqueta NFC."
                binding.writeButton.visibility = View.GONE
                binding.newTagInput.visibility = View.GONE
                binding.newTagButton.visibility = View.GONE
                binding.factoryResetButton.visibility = View.GONE
            }
        }

        sharedNfcViewModel.isNdefTag.observe(viewLifecycleOwner) { isNdef ->
            if (isNdef == true) {
                binding.writeButton.visibility = View.VISIBLE
                binding.factoryResetButton.visibility = View.VISIBLE
            } else {
                binding.writeButton.visibility = View.GONE
                binding.factoryResetButton.visibility = View.GONE
            }
        }

        sharedNfcViewModel.isNdefFormatable.observe(viewLifecycleOwner) { isFormattable ->
            if (isFormattable == true) {
                binding.newTagInput.visibility = View.VISIBLE
                binding.newTagButton.visibility = View.VISIBLE
            } else {
                binding.newTagInput.visibility = View.GONE
                binding.newTagButton.visibility = View.GONE
            }
        }

        sharedNfcViewModel.writeConfigRequest.observe(viewLifecycleOwner) { json ->
            if (json != null) {
                sharedNfcViewModel.nfcTag.value?.let { tag ->
                    writeConfigRecord(tag, json)
                }
                sharedNfcViewModel.resetWriteRequest()
            }
        }
    }

    // Funciones de escritura en la etiqueta

    private fun writeNewRecord(tag: Tag, payload: String) {
        val message = NdefMessage(NdefRecord.createMime("application/json", payload.toByteArray(StandardCharsets.UTF_8)))
        writeNdefMessage(tag, message)
    }

    private fun writeConfigRecord(tag: Tag, jsonString: String) {
        val ndef = Ndef.get(tag)
        if (ndef == null) {
            Toast.makeText(context, "La etiqueta no soporta NDEF.", Toast.LENGTH_SHORT).show()
            return
        }
        val records = ndef.cachedNdefMessage.records.toMutableList()
        val newRecord = NdefRecord.createMime("application/json", jsonString.toByteArray(StandardCharsets.UTF_8))
        records[2] = newRecord
        val newMessage = NdefMessage(records.toTypedArray())
        writeNdefMessage(tag, newMessage)
    }

    private fun writeProcessRecord(tag: Tag) {
        val ndef = Ndef.get(tag)
        if (ndef == null || ndef.cachedNdefMessage.records.size < 3) {
            Toast.makeText(context, "La etiqueta debe tener 3 registros existentes para usar esta función.", Toast.LENGTH_SHORT).show()
            return
        }

        val oldNdefMessage = ndef.cachedNdefMessage
        val records = oldNdefMessage.records.toMutableList()

        val dateFormat = SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.getDefault())
        val timestamp = dateFormat.format(Date())

        val newProcessJson = """
            {
                "volumen": ${Random.nextInt(1000)},
                "caudal": ${Random.nextInt(100)},
                "temperatura": ${Random.nextInt(20, 40)},
                "status": "OK",
                "timestamp": "$timestamp"
            }
        """.trimIndent()

        val newRecord2 = NdefRecord.createMime("application/json", newProcessJson.toByteArray(StandardCharsets.UTF_8))
        records[1] = newRecord2

        val newMessage = NdefMessage(records.toTypedArray())

        writeNdefMessage(tag, newMessage)
    }

    // CÓDIGO NUEVO: Función para escribir el JSON de fábrica en el registro de configuración (tercer registro)
    private fun writeFactoryResetRecord(tag: Tag) {
        val ndef = Ndef.get(tag)
        if (ndef == null || ndef.cachedNdefMessage.records.size < 3) {
            Toast.makeText(context, "La etiqueta debe tener 3 registros existentes para usar esta función.", Toast.LENGTH_SHORT).show()
            return
        }

        val factoryJson = """
            {
                "K_meter":98.980,
                "temp_raw_low":0.00,
                "temp_raw_high":30.00,
                "temp_cal_low":0.00,
                "temp_cal_high":30.00,
                "fc_q_Q1":27.456,
                "fc_q_Q2":24.321,
                "fc_q_0_35":19.123,
                "fc_q_1_00":9.876,
                "fc_q_10_0":0.123,
                "fc_q_Q3":-2.345
            }
        """.trimIndent()

        val oldNdefMessage = ndef.cachedNdefMessage
        val records = oldNdefMessage.records.toMutableList()
        val newRecord = NdefRecord.createMime("application/json", factoryJson.toByteArray(StandardCharsets.UTF_8))
        records[2] = newRecord // Reemplaza el tercer registro (índice 2)
        val newMessage = NdefMessage(records.toTypedArray())

        writeNdefMessage(tag, newMessage)
    }

    // Función de ayuda genérica para escribir NdefMessage
    private fun writeNdefMessage(tag: Tag, message: NdefMessage) {
        val ndef = Ndef.get(tag)
        try {
            ndef.connect()
            if (!ndef.isWritable) {
                Toast.makeText(context, "La etiqueta es de solo lectura.", Toast.LENGTH_SHORT).show()
                return
            }

            if (ndef.maxSize < message.toByteArray().size) {
                Toast.makeText(context, "El mensaje es demasiado grande para la etiqueta.", Toast.LENGTH_SHORT).show()
                return
            }

            ndef.writeNdefMessage(message)
            Toast.makeText(context, "¡Escritura exitosa!", Toast.LENGTH_LONG).show()
            Log.d("HomeFragment", "Escritura exitosa: ${String(message.toByteArray(), StandardCharsets.UTF_8)}")
        } catch (e: Exception) {
            Toast.makeText(context, "Error al escribir: ${e.message}", Toast.LENGTH_LONG).show()
            Log.e("HomeFragment", "Error al escribir en la etiqueta", e)
        } finally {
            try {
                ndef.close()
            } catch (e: IOException) {
                Log.e("HomeFragment", "Error al cerrar la conexión", e)
            }
        }
    }
}
