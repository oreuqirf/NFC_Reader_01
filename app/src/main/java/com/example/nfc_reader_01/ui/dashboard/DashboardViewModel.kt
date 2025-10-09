package com.example.nfc_reader_01.ui.dashboard

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

/**
 * ViewModel for the Dashboard screen.
 */
class DashboardViewModel : ViewModel() {

    /**
     * The text to be displayed on the dashboard.
     */
    private val _text = MutableLiveData<String>().apply {
        value = "This is dashboard Fragment"
    }
    val text: LiveData<String> = _text
}
