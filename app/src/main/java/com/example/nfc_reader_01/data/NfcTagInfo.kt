package com.example.nfc_reader_01.data

import android.nfc.NdefMessage

/**
 * Data class that encapsulates all relevant information of a detected NFC TAG.
 * This simplifies state management in the ViewModel.
 *
 * @property tagIdHex The ID of the TAG in hexadecimal format (String) for display.
 * @property techType The main detected technology (e.g., "Ndef", "NfcA").
 * @property ndefMessage The read NDEF message (can be null).
 */
data class NfcTagInfo(
    val tagIdHex: String,
    val techType: String,
    val ndefMessage: NdefMessage? = null
)
