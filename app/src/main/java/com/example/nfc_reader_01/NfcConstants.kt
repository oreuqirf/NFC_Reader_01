package com.example.nfc_reader_01

object NfcConstants {
    // --- TAMAÑOS DE PAYLOAD ---
    const val CONFIG_BYTE_SIZE = 80

    // --- COMANDOS DE PROTOCOLO ---
    const val CMD_READ_IDENTITY: Byte = 0x01
    const val CMD_READ_PROCESS: Byte = 0x02
    const val CMD_READ_CONFIG: Byte = 0x03
    const val CMD_WRITE_CONFIG: Byte = 0x04
    const val CMD_READ_ENGINEERING: Byte = 0x05
    const val CMD_FACTORY_RESET: Byte = 0x0A
    const val CMD_GET_SYSTEM_INFO: Byte = 0x2B.toByte()

    // --- COMANDOS ESPECIALES DE CONTROL ---
    const val CMD_SHUTDOWN: Byte = 0x10.toByte()
    const val CMD_RESET_VOLUME: Byte = 0x11.toByte()
    const val CMD_NORMAL_MODE: Byte = 0x12.toByte()
    const val CMD_CALIBRATE_FLOW: Byte = 0x13.toByte()
    const val CMD_SET_VOLUME: Byte = 0x14.toByte()
    const val CMD_ENTER_ENGINEERING_MODE: Byte = 0x15.toByte()
    const val CMD_CLEAR_FLAGS: Byte = 0x16.toByte()

    // --- TIPOS MIME ---
    const val MIME_COMMAND_TYPE = "application/x-cmd"
    const val MIME_RESPONSE_TYPE = "application/x-data"
}