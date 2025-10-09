package com.example.nfc_reader_01.ui.notifications

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

/**
 * ViewModel for the Notifications screen.
 */
class NotificationsViewModel : ViewModel() {

    /**
     * The text to be displayed on the notifications screen.
     */
    private val _text = MutableLiveData<String>().apply {
        value = "This is notifications Fragment"
    }
    val text: LiveData<String> = _text
}
