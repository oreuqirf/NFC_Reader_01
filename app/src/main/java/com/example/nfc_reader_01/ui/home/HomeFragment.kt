package com.example.nfc_reader_01.ui.home

import android.content.Intent
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.nfc_reader_01.SharedNfcViewModel
import com.example.nfc_reader_01.databinding.FragmentHomeBinding
import com.google.gson.Gson
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var sharedNfcViewModel: SharedNfcViewModel
    private var lastScannedTag: Tag? = null
    private var dashboardData: String? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        sharedNfcViewModel = ViewModelProvider(requireActivity()).get(SharedNfcViewModel::class.java)

        // Observa el estado de habilitación de NFC
        sharedNfcViewModel.isNfcEnabled.observe(viewLifecycleOwner) { isEnabled ->
            if (isEnabled) {
                binding.nfcStatusMessage.visibility = View.GONE
                binding.openSettingsButton.visibility = View.GONE

                binding.textHome.visibility = View.VISIBLE
                binding.readButton.visibility = View.VISIBLE
                binding.writeButton.visibility = View.VISIBLE
                binding.updateButton.visibility = View.VISIBLE
                binding.newTagInput.visibility = View.VISIBLE
                binding.newTagButton.visibility = View.VISIBLE
            } else {
                binding.nfcStatusMessage.visibility = View.VISIBLE
                binding.openSettingsButton.visibility = View.VISIBLE

                binding.textHome.visibility = View.GONE
                binding.readButton.visibility = View.GONE
                binding.writeButton.visibility = View.GONE
                binding.updateButton.visibility = View.GONE
                binding.newTagInput.visibility = View.GONE
                binding.newTagButton.visibility = View.GONE
            }
        }

        sharedNfcViewModel.nfcTag.observe(viewLifecycleOwner) { tag ->
            lastScannedTag = tag
            updateButtonStates()
        }

        sharedNfcViewModel.dashboardJsonString.observe(viewLifecycleOwner) { jsonString ->
            dashboardData = jsonString
            updateButtonStates()
        }

        // Nuevo observador para la lógica de la etiqueta vacía
        sharedNfcViewModel.isEmptyNdefTag.observe(viewLifecycleOwner) { isEmpty ->
            if (isEmpty) {
                binding.factoryResetMessage.visibility = View.VISIBLE
                binding.factoryResetButton.visibility = View.VISIBLE
            } else {
                binding.factoryResetMessage.visibility = View.GONE
                binding.factoryResetButton.visibility = View.GONE
            }
        }

        binding.readButton.setOnClickListener {
            binding.textHome.text = "Por favor, aproxime una etiqueta NFC."
            Toast.makeText(context, "Aproxime la etiqueta al teléfono para leerla.", Toast.LENGTH_SHORT).show()
        }

        binding.openSettingsButton.setOnClickListener {
            val settingsIntent = Intent(Settings.ACTION_NFC_SETTINGS)
            startActivity(settingsIntent)
        }

        binding.writeButton.setOnClickListener {
            lastScannedTag?.let { tag ->
                writeNdefTag(tag)
            } ?: run {
                Toast.makeText(context, "No hay una etiqueta NFC válida para escribir.", Toast.LENGTH_SHORT).show()
            }
        }

        binding.updateButton.setOnClickListener {
            lastScannedTag?.let { tag ->
                dashboardData?.let { data ->
                    updateNdefRecord(tag, data)
                } ?: Toast.makeText(context, "No hay datos guardados en el Dashboard para actualizar.", Toast.LENGTH_SHORT).show()
            } ?: Toast.makeText(context, "No hay una etiqueta NFC válida para actualizar.", Toast.LENGTH_SHORT).show()
        }

        binding.newTagButton.setOnClickListener {
            val content = binding.newTagInput.text.toString()
            lastScannedTag?.let { tag ->
                writeNewNdefTag(tag, content)
            } ?: Toast.makeText(context, "No hay una etiqueta NFC válida para escribir.", Toast.LENGTH_SHORT).show()
        }

        binding.factoryResetButton.setOnClickListener {
            lastScannedTag?.let { tag ->
                writeFactoryConfigTag(tag)
            } ?: Toast.makeText(context, "No hay una etiqueta NFC válida para escribir.", Toast.LENGTH_SHORT).show()
        }

        binding.newTagInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) { }
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { }
            override fun afterTextChanged(s: Editable?) {
                updateButtonStates()
            }
        })

        binding.newTagInput.setOnEditorActionListener { _, actionId, _ ->
            updateButtonStates()
            false
        }

        updateButtonStates()
    }

    private fun updateButtonStates() {
        val hasTag = lastScannedTag != null
        val hasDashboardData = !dashboardData.isNullOrBlank()
        val hasNewTagContent = binding.newTagInput.text.isNotBlank()

        binding.readButton.isEnabled = true
        binding.writeButton.isEnabled = hasTag
        binding.updateButton.isEnabled = hasTag && hasDashboardData
        binding.newTagButton.isEnabled = hasTag && hasNewTagContent
    }

    private fun writeNdefTag(tag: Tag) {
        val ndef = Ndef.get(tag)
        if (ndef == null) {
            Toast.makeText(context, "La etiqueta no es compatible con NDEF.", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            ndef.connect()
            if (!ndef.isWritable) {
                Toast.makeText(context, "La etiqueta es de solo lectura.", Toast.LENGTH_SHORT).show()
                return
            }

            val oldNdefMessage = ndef.cachedNdefMessage
            val newRecordContent = "Valor aleatorio: ${Random.nextInt(1000)} | Tiempo: ${SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date())}"
            val newRecord = NdefRecord.createTextRecord("es", newRecordContent)

            val newRecords = oldNdefMessage.records.toMutableList()

            if (newRecords.size >= 2) {
                newRecords[1] = newRecord
            } else {
                Toast.makeText(context, "No se puede modificar, la etiqueta no tiene un segundo registro.", Toast.LENGTH_SHORT).show()
                return
            }

            val newNdefMessage = NdefMessage(newRecords.toTypedArray())

            if (ndef.maxSize < newNdefMessage.toByteArray().size) {
                Toast.makeText(context, "El mensaje es demasiado grande para la etiqueta.", Toast.LENGTH_SHORT).show()
                return
            }

            ndef.writeNdefMessage(newNdefMessage)
            Toast.makeText(context, "¡Escritura exitosa! NDEF n°2 modificado.", Toast.LENGTH_LONG).show()
            Log.d("HomeFragment", "Escritura exitosa: $newRecordContent")
        } catch (e: Exception) {
            Toast.makeText(context, "Error al escribir: ${e.message}", Toast.LENGTH_LONG).show()
            Log.e("HomeFragment", "Error al escribir en la etiqueta", e)
        } finally {
            try {
                ndef.close()
            } catch (e: Exception) {
                Log.e("HomeFragment", "Error al cerrar la conexión NFC", e)
            }
        }
    }

    private fun updateNdefRecord(tag: Tag, newJsonContent: String) {
        val ndef = Ndef.get(tag)
        if (ndef == null) {
            Toast.makeText(context, "La etiqueta no es compatible con NDEF.", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            ndef.connect()
            if (!ndef.isWritable) {
                Toast.makeText(context, "La etiqueta es de solo lectura.", Toast.LENGTH_SHORT).show()
                return
            }

            val oldNdefMessage = ndef.cachedNdefMessage

            val newRecord = NdefRecord.createTextRecord("es", newJsonContent)

            val newRecords = oldNdefMessage.records.toMutableList()

            if (newRecords.isNotEmpty()) {
                newRecords[0] = newRecord
            } else {
                Toast.makeText(context, "La etiqueta no tiene registros para actualizar.", Toast.LENGTH_SHORT).show()
                return
            }

            val newNdefMessage = NdefMessage(newRecords.toTypedArray())

            if (ndef.maxSize < newNdefMessage.toByteArray().size) {
                Toast.makeText(context, "El nuevo mensaje es demasiado grande para la etiqueta.", Toast.LENGTH_SHORT).show()
                return
            }

            ndef.writeNdefMessage(newNdefMessage)
            Toast.makeText(context, "¡Actualización exitosa! Registro NDEF n°1 actualizado.", Toast.LENGTH_LONG).show()
            Log.d("HomeFragment", "Registro NDEF n°1 actualizado con: $newJsonContent")

        } catch (e: Exception) {
            Toast.makeText(context, "Error al actualizar: ${e.message}", Toast.LENGTH_LONG).show()
            Log.e("HomeFragment", "Error al actualizar el registro", e)
        } finally {
            try {
                ndef.close()
            } catch (e: Exception) {
                Log.e("HomeFragment", "Error al cerrar la conexión NFC", e)
            }
        }
    }


    private fun writeNewNdefTag(tag: Tag, content: String) {
        val currentTimestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

        val textValue: String? = if (content.isBlank()) null else content
        val jsonMap = mapOf("texto" to textValue, "timestamp" to currentTimestamp)
        val jsonString = Gson().toJson(jsonMap)

        val newRecord = NdefRecord.createTextRecord("es", jsonString)
        val newMessage = NdefMessage(newRecord)

        Log.d("HomeFragment", "Preparando para escribir JSON: $jsonString")

        var ndef: Ndef? = Ndef.get(tag)
        if (ndef != null) {
            try {
                ndef.connect()
                if (!ndef.isWritable) {
                    Toast.makeText(context, "La etiqueta es de solo lectura.", Toast.LENGTH_SHORT).show()
                    return
                }

                if (ndef.maxSize < newMessage.toByteArray().size) {
                    Toast.makeText(context, "El mensaje es demasiado grande para la etiqueta.", Toast.LENGTH_SHORT).show()
                    return
                }

                ndef.writeNdefMessage(newMessage)
                Toast.makeText(context, "¡Escritura exitosa! Nueva etiqueta NDEF creada.", Toast.LENGTH_LONG).show()
                Log.d("HomeFragment", "Nueva etiqueta escrita con contenido: $jsonString")
            } catch (e: Exception) {
                Toast.makeText(context, "Error al escribir la nueva etiqueta: ${e.message}", Toast.LENGTH_LONG).show()
                Log.e("HomeFragment", "Error al escribir una nueva etiqueta", e)
            } finally {
                try {
                    ndef.close()
                } catch (e: Exception) {
                    Log.e("HomeFragment", "Error al cerrar la conexión NFC", e)
                }
            }
        } else {
            var ndefFormatable: NdefFormatable? = NdefFormatable.get(tag)
            if (ndefFormatable != null) {
                try {
                    ndefFormatable.connect()
                    ndefFormatable.format(newMessage)
                    Toast.makeText(context, "¡Formateo y escritura exitosa! Nueva etiqueta creada.", Toast.LENGTH_LONG).show()
                    Log.d("HomeFragment", "Etiqueta formateada y escrita con contenido: $jsonString")
                } catch (e: Exception) {
                    Toast.makeText(context, "Error al formatear y escribir la etiqueta: ${e.message}", Toast.LENGTH_LONG).show()
                    Log.e("HomeFragment", "Error al formatear y escribir la etiqueta", e)
                } finally {
                    try {
                        ndefFormatable.close()
                    } catch (e: Exception) {
                        Log.e("HomeFragment", "Error al cerrar la conexión de formateo NFC", e)
                    }
                }
            } else {
                Toast.makeText(context, "La etiqueta no es compatible con NDEF.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Nueva función para escribir la configuración de fábrica
    private fun writeFactoryConfigTag(tag: Tag) {
        val factoryConfigJson = """
            {
                "texto": "Configuracion de fabrica - N° Serial: 123456",
                "version": "1.0",
                "dispositivo": "sensor_01",
                "estado": "OK"
            }
        """.trimIndent()

        val newRecord = NdefRecord.createTextRecord("es", factoryConfigJson)
        val newMessage = NdefMessage(newRecord)

        Log.d("HomeFragment", "Preparando para escribir configuración de fábrica: $factoryConfigJson")

        var ndef: Ndef? = Ndef.get(tag)
        if (ndef != null) {
            try {
                ndef.connect()
                if (!ndef.isWritable) {
                    Toast.makeText(context, "La etiqueta es de solo lectura.", Toast.LENGTH_SHORT).show()
                    return
                }

                if (ndef.maxSize < newMessage.toByteArray().size) {
                    Toast.makeText(context, "El mensaje de configuración es demasiado grande para la etiqueta.", Toast.LENGTH_SHORT).show()
                    return
                }

                ndef.writeNdefMessage(newMessage)
                Toast.makeText(context, "¡Configuración de fábrica escrita!", Toast.LENGTH_LONG).show()
                Log.d("HomeFragment", "Configuración de fábrica escrita.")
            } catch (e: Exception) {
                Toast.makeText(context, "Error al escribir la configuración: ${e.message}", Toast.LENGTH_LONG).show()
                Log.e("HomeFragment", "Error al escribir la configuración de fábrica", e)
            } finally {
                try {
                    ndef.close()
                } catch (e: Exception) {
                    Log.e("HomeFragment", "Error al cerrar la conexión NFC", e)
                }
            }
        } else {
            var ndefFormatable: NdefFormatable? = NdefFormatable.get(tag)
            if (ndefFormatable != null) {
                try {
                    ndefFormatable.connect()
                    ndefFormatable.format(newMessage)
                    Toast.makeText(context, "¡Formateo y configuración de fábrica exitosa!", Toast.LENGTH_LONG).show()
                    Log.d("HomeFragment", "Etiqueta formateada y escrita con configuración de fábrica.")
                } catch (e: Exception) {
                    Toast.makeText(context, "Error al formatear y escribir la configuración: ${e.message}", Toast.LENGTH_LONG).show()
                    Log.e("HomeFragment", "Error al formatear y escribir la configuración de fábrica", e)
                } finally {
                    try {
                        ndefFormatable.close()
                    } catch (e: Exception) {
                        Log.e("HomeFragment", "Error al cerrar la conexión de formateo NFC", e)
                    }
                }
            } else {
                Toast.makeText(context, "La etiqueta no es compatible con NDEF.", Toast.LENGTH_SHORT).show()
            }
        }
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
