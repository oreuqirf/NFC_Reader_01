package com.example.nfc_reader_01.ui.notifications

import android.content.Intent
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import com.example.nfc_reader_01.R
import com.example.nfc_reader_01.SharedNfcViewModel
import com.example.nfc_reader_01.databinding.FragmentNotificationsBinding
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import java.io.IOException
import java.nio.charset.Charset
import java.text.SimpleDateFormat
import java.util.*
import kotlin.random.Random
import android.graphics.Color
import android.util.Log

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
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        sharedNfcViewModel = ViewModelProvider(requireActivity()).get(SharedNfcViewModel::class.java)

        val nfcIntent: Intent? = arguments?.getParcelable("nfc_intent")
        if (nfcIntent != null) {
            handleNfcIntent(nfcIntent)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun handleNfcIntent(intent: Intent?) {
        if (intent == null) return

        binding.nfcStatusTextView.text = "Acción: Leyendo etiqueta NFC...\n"
        binding.nfcDataTextView.text = "Contenido de la etiqueta: \n\n"

        val tag = intent.getParcelableExtra<Tag>(NfcAdapter.EXTRA_TAG)
        val nfcDataContainer = view?.findViewById<LinearLayout>(R.id.nfc_container)

        if (nfcDataContainer != null) {
            val childCount = nfcDataContainer.childCount
            if (childCount > 3) {
                for (i in childCount - 1 downTo 3) {
                    nfcDataContainer.removeViewAt(i)
                }
            }
        }

        if (tag != null) {
            val info = StringBuilder()
            info.append("ID de la Etiqueta (Hex): ${bytesToHexString(tag.id)}\n")
            info.append("Tecnologías soportadas:\n")
            tag.techList.forEach { tech ->
                info.append("- $tech\n")
            }

            val ndef = Ndef.get(tag)
            if (ndef != null) {
                info.append("Tipo de NDEF: ${ndef.type}\n")
                info.append("Tamaño máximo: ${ndef.maxSize} bytes\n")
                info.append("Grabable: ${ndef.isWritable}\n")

            } else {
                val ndefFormatable = NdefFormatable.get(tag)
                if (ndefFormatable != null) {
                    info.append("Formateable a NDEF: Sí\n")
                }
            }
            binding.nfcCharacteristicsTextView.text = info.toString()
        } else {
            binding.nfcCharacteristicsTextView.text = "No se pudo obtener la información de la etiqueta."
        }

        val rawMessages = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)
        var jsonContent: String? = null

        if (rawMessages != null) {
            val messages = rawMessages.mapNotNull { it as? NdefMessage }
            if (messages.isNotEmpty()) {
                var recordCounter = 1
                for (msg in messages) {
                    for (record in msg.records) {
                        val recordInfo = StringBuilder()
                        recordInfo.append("Registro NDEF #${recordCounter}\n")
                        recordInfo.append("----------------------------\n")

                        if (record.tnf == NdefRecord.TNF_WELL_KNOWN.toShort() && record.type.contentEquals(NdefRecord.RTD_TEXT)) {
                            val payload = record.payload
                            val textEncoding = if ((payload[0].toInt() and 128) == 0) "UTF-8" else "UTF-16"
                            val languageCodeLength = payload[0].toInt() and 0x3F
                            val text = String(
                                payload,
                                languageCodeLength + 1,
                                payload.size - languageCodeLength - 1,
                                Charset.forName(textEncoding)
                            )
                            recordInfo.append("Tipo de registro: Texto (RTD_TEXT)\n")
                            recordInfo.append("Contenido: $text\n")

                            if (recordCounter == 1) {
                                jsonContent = text
                            }
                        } else {
                            recordInfo.append("Tipo de registro: Desconocido\n")
                            recordInfo.append("Tipo de Tnf: ${record.tnf}\n")
                            recordInfo.append("Tipo de payload: ${String(record.type)}\n")
                            recordInfo.append("Contenido en bruto: ${bytesToHexString(record.payload)}\n")
                        }

                        val newTextView = TextView(context).apply {
                            layoutParams = LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT
                            ).also {
                                it.setMargins(0, 16, 0, 0)
                            }
                            textSize = 16f
                            setBackgroundColor(Color.parseColor("#E0E0E0"))
                            setPadding(8, 8, 8, 8)
                            text = recordInfo.toString()
                        }
                        nfcDataContainer?.addView(newTextView)
                        recordCounter++
                    }
                }
            } else {
                binding.nfcDataTextView.text = "La etiqueta no contiene mensajes NDEF válidos."
            }
        } else {
            binding.nfcDataTextView.text = "No se encontró un mensaje NDEF válido."
        }

        // Guarda el JSON completo como string en el ViewModel
        sharedNfcViewModel.nfcDataString.value = jsonContent
        if (jsonContent != null) {
            Toast.makeText(context, "Datos NFC guardados", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "No se encontraron datos JSON", Toast.LENGTH_SHORT).show()
        }

        // Navega al DashboardFragment para mostrar los datos
        findNavController().navigate(R.id.navigation_dashboard)
    }

    private fun bytesToHexString(src: ByteArray?): String {
        val stringBuilder = StringBuilder("0x")
        if (src == null || src.isEmpty()) {
            return "null"
        }
        val hexChars = CharArray(src.size * 2)
        for (j in src.indices) {
            val v = src[j].toInt() and 0xFF
            hexChars[j * 2] = hexArray[v ushr 4]
            hexChars[j * 2 + 1] = hexArray[v and 0x0F]
        }
        stringBuilder.append(hexChars)
        return stringBuilder.toString()
    }

    companion object {
        private val hexArray = "0123456789ABCDEF".toCharArray()
    }
}
