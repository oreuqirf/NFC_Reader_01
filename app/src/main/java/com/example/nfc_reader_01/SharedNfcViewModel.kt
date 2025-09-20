package com.example.nfc_reader_01

import android.nfc.NdefMessage
import android.nfc.Tag
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

// Data class para encapsular los datos de los registros.
data class NdefRecordData(
    val identityData: ByteArray?,
    val processData: ByteArray?,
    val configData: ByteArray?
)



class SharedNfcViewModel : ViewModel() {

    // Estado del adaptador NFC.
    private val _nfcStatus = MutableLiveData<Boolean>()
    val nfcStatus: LiveData<Boolean> = _nfcStatus

    // Estado de la etiqueta NFC detectada.
    private val _nfcTag = MutableLiveData<Tag?>()
    val nfcTag: LiveData<Tag?> = _nfcTag

    // Datos de los registros NDEF de la etiqueta.
    private val _ndefRecords = MutableLiveData<NdefRecordData>()
    val ndefRecords: LiveData<NdefRecordData> = _ndefRecords

    // LiveData para el estado de la escritura de la etiqueta.
    private val _writeStatus = MutableLiveData<String?>()
    val writeStatus: LiveData<String?> = _writeStatus

    // LiveData para la solicitud de escritura, que el MainActivity observa.
    private val _writeConfigRequest = MutableLiveData<ByteArray?>()
    val writeConfigRequest: LiveData<ByteArray?> = _writeConfigRequest

    // LiveData para la solicitud de escritura de un mensaje NDEF completo.
    private val _writeMessageRequest = MutableLiveData<NdefMessage?>()
    val writeMessageRequest: LiveData<NdefMessage?> = _writeMessageRequest

    // En SharedNfcViewModel.kt
    private val _formatNewTagRequest = MutableLiveData<Boolean?>()
    val formatNewTagRequest: LiveData<Boolean?> = _formatNewTagRequest

    // Estado de la etiqueta NDEF.
    private val _isNdef = MutableLiveData<Boolean>()
    val isNdef: LiveData<Boolean> = _isNdef

    // Estado de la etiqueta NDEF Formateable.
    private val _isNdefFormatable = MutableLiveData<Boolean>()
    val isNdefFormatable: LiveData<Boolean> = _isNdefFormatable

    // Estado de la etiqueta NDEF vacía.
    private val _isEmptyNdef = MutableLiveData<Boolean>()
    val isEmptyNdef: LiveData<Boolean> = _isEmptyNdef


    fun setFormatNewTagRequest(request: Boolean) {
        _formatNewTagRequest.value = request
    }

    fun resetFormatNewTagRequest() {
        _formatNewTagRequest.value = null
    }

    fun setNfcStatus(status: Boolean) {
        _nfcStatus.value = status
    }

    fun setNfcTag(tag: Tag?) {
        _nfcTag.value = tag
    }

    fun setNdefRecords(identityData: ByteArray?, processData: ByteArray?, configData: ByteArray?) {
        _ndefRecords.value = NdefRecordData(identityData, processData, configData)
    }

    fun setWriteStatus(status: String?) {
        _writeStatus.value = status
    }

    fun setWriteConfigRequest(data: ByteArray?) {
        _writeConfigRequest.value = data
    }

    fun resetWriteRequest() {
        _writeConfigRequest.value = null
    }

    fun setWriteMessageRequest(ndefMessage: NdefMessage) {
        _writeMessageRequest.value = ndefMessage
    }

    fun resetWriteMessageRequest() {
        _writeMessageRequest.value = null
    }

    fun setIsNdef(isNdef: Boolean) {
        _isNdef.value = isNdef
    }

    fun setIsNdefFormatable(isNdefFormatable: Boolean) {
        _isNdefFormatable.value = isNdefFormatable
    }

    fun setIsEmptyNdef(isEmptyNdef: Boolean) {
        _isEmptyNdef.value = isEmptyNdef
    }
}
