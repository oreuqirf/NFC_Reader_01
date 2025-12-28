package com.example.nfc_reader_01.data

import android.nfc.NdefMessage
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Clase de datos que encapsula toda la información relevante de un TAG NFC detectado.
 * Esto simplifica la gestión del estado en el ViewModel.
 *
 * @param tagIdHex El ID del TAG en formato hexadecimal (String) para visualización.
 * @param techType La tecnología principal detectada (e.g., "Ndef", "NfcA").
 * @param ndefMessage El mensaje NDEF leído (puede ser null).
 */
data class NfcTagInfo(
    val tagIdHex: String,
    val techType: String,
    val ndefMessage: NdefMessage? = null
)

