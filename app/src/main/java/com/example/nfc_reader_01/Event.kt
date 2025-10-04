package com.example.nfc_reader_01


// Event.kt

/**
 * Usado como wrapper para datos que solo deberían ser consumidos una vez.
 */
open class Event<out T>(private val content: T) {

    var hasBeenHandled = false
        private set // Solo permite establecer este valor dentro de la clase

    /**
     * Devuelve el contenido y marca el evento como manejado.
     */
    fun getContentIfNotHandled(): T? {
        return if (hasBeenHandled) {
            null
        } else {
            hasBeenHandled = true
            content
        }
    }

    /**
     * Devuelve el contenido, incluso si ya ha sido manejado.
     */
    fun peekContent(): T = content
}
