package com.example.nfc_reader_01.ui.home

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.nfc_reader_01.NfcInteractionListener
import com.example.nfc_reader_01.R
import com.example.nfc_reader_01.SharedNfcViewModel
import com.example.nfc_reader_01.databinding.FragmentHomeBinding
import kotlinx.coroutines.launch

/**
 * A [Fragment] that displays the main screen of the application.
 * It shows the current NFC status and navigates to the dashboard when a tag is detected.
 */
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    // Use by activityViewModels() to inject and get the shared instance
    private val sharedNfcViewModel: SharedNfcViewModel by activityViewModels()

    private var listener: NfcInteractionListener? = null // Reference to the Activity (Listener)

    override fun onAttach(context: Context) {
        super.onAttach(context)
        // 1. Make sure the Activity implements the interface
        if (context is NfcInteractionListener) {
            listener = context
        } else {
            // Log.wtf is used for critical configuration errors
            Log.wtf("HomeFragment", "$context must implement NfcInteractionListener")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        setupObservers()
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onDetach() {
        super.onDetach()
        listener = null // Clear the reference to avoid memory leaks
    }

    /**
     * Sets up the observers for the [SharedNfcViewModel].
     */
    private fun setupObservers() {
        // --- Observes nfcTagInfo (using collect) ---
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                sharedNfcViewModel.nfcTagInfo.collect { tagInfo ->
                    if (tagInfo != null) {
                        // 1. Notify the Activity to navigate
                        listener?.navigateToDashboard()

                        // 2. Update the UI
                        binding.statusIcon.setImageResource(R.drawable.ic_nfc_connected_24)
                        binding.statusMessage.text = "TAG detected! Navigating to information..."

                        // 3. Show a clear Toast
                        Toast.makeText(
                            context,
                            "NFC tag connected! Analyzing data.",
                            Toast.LENGTH_SHORT
                        ).show()

                    } else {
                        // This runs at the beginning or if the ViewModel resets the state
                        binding.statusIcon.setImageResource(R.drawable.ic_nfc_scan_24)
                        binding.statusMessage.text = "Approach an NFC tag to start reading."
                    }
                }
            }
        }

        // Observes writeStatus (SharedFlow).
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // 'status' is a String (non-nullable)
                sharedNfcViewModel.writeStatus.collect { status ->
                    // The ViewModel ensures that 'status' is a user-friendly message.
                    // We use a longer Toast to ensure the write status information is read.
                    Toast.makeText(context, status, Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
