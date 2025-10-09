package com.example.nfc_reader_01.ui.home

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

/**
 * ViewModel for the Home screen.
 */
class HomeViewModel : ViewModel() {

    /**
     * The text to be displayed on the home screen.
     */
    private val _text = MutableLiveData<String>().apply {
        value = "This is home Fragment"
    }
    val text: LiveData<String> = _text
}
