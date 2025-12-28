package com.example.nfc_reader_01

sealed class NfcState {
    object Idle : NfcState()
    object Loading : NfcState()
    object Success : NfcState()
    data class Error(val errorMessage: String) : NfcState()
}