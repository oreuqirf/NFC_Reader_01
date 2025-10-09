package com.example.nfc_reader_01

/**
 * A wrapper for data that should only be consumed once.
 * This is useful for events like navigation or showing a Snackbar, where you want to prevent the
 * action from being repeated on configuration changes.
 *
 * @param T The type of the content.
 * @property content The actual data being held by the event.
 */
open class Event<out T>(private val content: T) {

    var hasBeenHandled = false
        private set // Allow external read but not write

    /**
     * Returns the content and prevents its use again.
     * If the content has already been handled, it returns null.
     *
     * @return The content if it hasn't been handled, otherwise null.
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
     * Returns the content, even if it's already been handled.
     * This is useful for peeking at the content without consuming the event.
     *
     * @return The content.
     */
    fun peekContent(): T = content
}
