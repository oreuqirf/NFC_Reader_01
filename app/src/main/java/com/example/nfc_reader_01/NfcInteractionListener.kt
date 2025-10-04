package com.example.nfc_reader_01

/**
 * Interfaz que permite a los Fragmentos solicitar acciones a la MainActivity.
 * La MainActivity debe implementar esta interfaz para manejar las interacciones NFC.
 */
interface NfcInteractionListener {
    /**
     * Solicita a la MainActivity que navegue al fragmento Dashboard.
     * Utilizado después de un escaneo exitoso o una acción de importancia.
     */
    fun navigateToDashboard()

    /**
     * Solicita al ViewModel que establezca un nuevo comando para el próximo ciclo
     * de lectura/escritura (ej. 0x02 para leer datos de proceso).
     *
     * @param commandId El byte del comando a solicitar.
     */
    fun requestNextCommand(commandId: Byte)

    /**
     * Solicita al ViewModel que prepare y ejecute el mensaje de escritura de configuración (0x04)
     * en el próximo escaneo del TAG.
     */
    fun requestWriteConfig()
}
