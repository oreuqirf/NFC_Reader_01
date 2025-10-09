package com.example.nfc_reader_01

/**
 * Interface that allows Fragments to request actions from the MainActivity.
 * The MainActivity must implement this interface to handle NFC interactions.
 */
interface NfcInteractionListener {
    /**
     * Requests the MainActivity to navigate to the Dashboard fragment.
     * Used after a successful scan or an important action.
     */
    fun navigateToDashboard()

    /**
     * Requests the ViewModel to set a new command for the next read/write cycle
     * (e.g., 0x02 for reading process data).
     *
     * @param commandId The byte of the command to request.
     */
    fun requestNextCommand(commandId: Byte)

    /**
     * Requests the ViewModel to prepare and execute the configuration write message (0x05)
     * in the next TAG scan.
     */
    fun requestWriteConfig()
}
