package com.example.nfc_reader_01

/**
 * Interfaz para comunicar eventos desde los Fragmentos hacia la Activity principal (MainActivity),
 * quien gestiona la lógica de NFC.
 */
interface NfcInteractionListener {

    /**
     * Navega al fragmento Dashboard para mostrar el progreso o resultados.
     */
    fun navigateToDashboard()

    /**
     * Solicita a la Activity que prepare el siguiente comando NFC simple (lectura, reset, o comandos de control sin payload).
     * @param commandId El byte del comando a ejecutar (ej: 0x01, 0x02, 0x0A, 0x10, 0x11, etc.)
     */
    fun requestNextCommand(commandId: Byte)

    /**
     * Solicita a la Activity que prepare la escritura de la configuración completa (96 bytes).
     * Los datos deben haber sido seteados previamente en el ViewModel.
     * Comando asociado: 0x04.
     */
    fun requestWriteConfig()

    /**
     * Solicita a la Activity que prepare el seteo del volumen (4 bytes).
     * Los datos (el nuevo volumen Float) deben haber sido seteados previamente en el ViewModel.
     * Comando asociado: 0x14.
     */
    fun requestSetVolume()

    /**
     * Solicita a la Activity que inicie el proceso de lectura del equipo patrón.
     * Utiliza el comando de lectura de ingeniería (0x05) o proceso (0x02) según la configuración.
     */
    fun requestReadMaster()

    /**
     * Solicita a la Activity que prepare la escritura del valor de calibración (5 bytes: 1 tipo + 4 float).
     * Los datos deben haber sido seteados previamente en el ViewModel.
     * Comando asociado: 0x13.
     */
    fun requestCalibrationWrite()

    /**
     * Solicita a la Activity que envíe el comando para ingresar al Modo Ingeniería.
     * Comando asociado: 0x15.
     */
    fun requestEnterEngineeringMode()
}

