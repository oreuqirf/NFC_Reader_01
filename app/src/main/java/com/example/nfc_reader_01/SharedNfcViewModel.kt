package com.example.nfc_reader_01

import android.nfc.Tag
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class SharedNfcViewModel : ViewModel() {

    private val _isNfcEnabled = MutableLiveData<Boolean>()
    val isNfcEnabled: LiveData<Boolean> = _isNfcEnabled

    private val _nfcTag = MutableLiveData<Tag?>()
    val nfcTag: LiveData<Tag?> = _nfcTag

    private val _isNdefTag = MutableLiveData<Boolean>()
    val isNdefTag: LiveData<Boolean> = _isNdefTag

    private val _isNdefFormatable = MutableLiveData<Boolean>()
    val isNdefFormatable: LiveData<Boolean> = _isNdefFormatable

    private val _isEmptyNdefTag = MutableLiveData<Boolean>()
    val isEmptyNdefTag: LiveData<Boolean> = _isEmptyNdefTag

    private val _identityDataJson = MutableLiveData<String?>()
    val identityDataJson: LiveData<String?> = _identityDataJson

    private val _processDataJson = MutableLiveData<String?>()
    val processDataJson: LiveData<String?> = _processDataJson

    private val _configurationDataJson = MutableLiveData<String?>()
    val configurationDataJson: LiveData<String?> = _configurationDataJson

    private val _writeConfigRequest = MutableLiveData<String?>()
    val writeConfigRequest: LiveData<String?> = _writeConfigRequest

    fun setNfcStatus(status: Boolean) {
        _isNfcEnabled.value = status
    }

    fun setNfcTag(tag: Tag?) {
        _nfcTag.value = tag
    }

    fun setTagInfo(isNdef: Boolean, isNdefForm: Boolean, isEmpty: Boolean) {
        _isNdefTag.value = isNdef
        _isNdefFormatable.value = isNdefForm
        _isEmptyNdefTag.value = isEmpty
    }

    fun setNdefRecords(identityJson: String?, processJson: String?, configurationJson: String?) {
        _identityDataJson.value = identityJson
        _processDataJson.value = processJson
        _configurationDataJson.value = configurationJson
    }

    fun setWriteConfigRequest(json: String) {
        _writeConfigRequest.value = json
    }

    fun resetWriteRequest() {
        _writeConfigRequest.value = null
    }

    fun resetNfcData() {
        _nfcTag.value = null
        _isNdefTag.value = false
        _isNdefFormatable.value = false
        _isEmptyNdefTag.value = false
        _identityDataJson.value = null
        _processDataJson.value = null
        _configurationDataJson.value = null
    }
}
