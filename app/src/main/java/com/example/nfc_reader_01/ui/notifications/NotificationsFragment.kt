package com.example.nfc_reader_01.ui.notifications

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.nfc_reader_01.databinding.FragmentNotificationsBinding
import com.example.nfc_reader_01.utils.LogManager
import kotlinx.coroutines.launch

/**
 * A [Fragment] that displays the protocol logs.
 */
class NotificationsFragment : Fragment() {

    private var _binding: FragmentNotificationsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNotificationsBinding.inflate(inflater, container, false)
        val root: View = binding.root

        setupLogCollector()
        setupClearButton()

        return root
    }

    /**
     * Sets up the Kotlin Flow collector to receive logs.
     * This replaces the 'setupLogObserver' function that used LiveData.
     */
    private fun setupLogCollector() {
        // We use viewLifecycleOwner.lifecycleScope to ensure that the collection
        // stops automatically when the fragment's view is destroyed.
        viewLifecycleOwner.lifecycleScope.launch {
            // The fragment collects (consumes) the log flow.
            LogManager.protocolLog.collect { logs ->
                // logs is a String that already contains the complete formatted history.
                if (logs.isNotEmpty()) {
                    binding.textViewProtocolLogs.text = logs
                    // We call the scroll function to always show the most recent (at the beginning)
                    scrollToTop()
                } else {
                    binding.textViewProtocolLogs.text = "No events registered."
                }
            }
        }
    }

    /**
     * Sets up the listener for the clear logs button.
     */
    private fun setupClearButton() {
        binding.buttonClearLogs.setOnClickListener {
            // Launch the coroutine to call the suspend function of LogManager
            viewLifecycleOwner.lifecycleScope.launch {
                LogManager.clearLogs()
            }
        }
    }


    /**
     * Moves the scroll of the TextView to the beginning to see the most recent log.
     */
    private fun scrollToTop() {
        // Simply moves the cursor to the beginning of the text (index 0)
        binding.textViewProtocolLogs.scrollTo(0, 0)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
