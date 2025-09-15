package com.example.nfc_reader_01

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import android.content.Intent
import android.nfc.NdefMessage
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.util.Log
import com.google.gson.Gson
import java.nio.charset.Charset

class SharedNfcViewModel : ViewModel() {
    val nfcIntent = MutableLiveData<Intent>()
    val nfcDataString = MutableLiveData<String>()
    val dashboardJsonString = MutableLiveData<String>()
    val nfcTag = MutableLiveData<Tag>()

    val isTagDetected = MutableLiveData(false)
    val isNfcEnabled = MutableLiveData<Boolean>()
    val isEmptyNdefTag = MutableLiveData<Boolean>(false)

    fun processNfcIntent(intent: Intent?) {
        if (intent != null) {
            val tag = intent.getParcelableExtra<Tag>(NfcAdapter.EXTRA_TAG)
            if (tag != null) {
                this.nfcTag.value = tag
                this.isTagDetected.value = true
                Log.d("SharedNfcViewModel", "Etiqueta procesada en el ViewModel.")

                val ndef = Ndef.get(tag)
                if (ndef != null) {
                    try {
                        ndef.connect()
                        val ndefMessage = ndef.cachedNdefMessage
                        val records = ndefMessage.records

                        // Detección de etiqueta vacía
                        isEmptyNdefTag.value = ndefMessage.records.isEmpty()

                        val recordContents = records.mapIndexed { index, record ->
                            try {
                                val payload = record.payload
                                val text = parseNdefTextPayload(payload)
                                "Registro N°${index + 1}:\n$text"
                            } catch (e: Exception) {
                                "Registro N°${index + 1}:\n(Contenido no legible)"
                            }
                        }

                        val notificationContent = recordContents.joinToString(separator = "\n\n")
                        nfcDataString.value = notificationContent

                        if (records.isNotEmpty()) {
                            val firstRecordPayload = records[0].payload
                            val json = parseNdefTextPayload(firstRecordPayload)

                            dashboardJsonString.value = json
                            Log.d("SharedNfcViewModel", "Payload extraído y enviado al Dashboard.")

                        }
                    } catch (e: Exception) {
                        Log.e("SharedNfcViewModel", "Error al procesar la etiqueta: ${e.message}")
                        nfcDataString.value = "Error al leer los datos de la etiqueta: ${e.message}"
                        isEmptyNdefTag.value = false
                    } finally {
                        ndef.close()
                    }
                } else {
                    nfcDataString.value = "Etiqueta no compatible con NDEF. Tecnologías: ${tag.techList.joinToString()}"
                    isEmptyNdefTag.value = false
                }
            }
        }
    }

    private fun parseNdefTextPayload(payload: ByteArray): String {
        val statusByte = payload[0]
        val languageCodeLength = statusByte.toInt() and 0x3F

        val textBytes = payload.copyOfRange(1 + languageCodeLength, payload.size)

        val isUtf16 = (statusByte.toInt() and 0x80) != 0
        val charset = if (isUtf16) Charset.forName("UTF-16") else Charset.forName("UTF-8")

        return String(textBytes, charset).trim()
    }
}
