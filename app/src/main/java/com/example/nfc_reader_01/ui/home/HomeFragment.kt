package com.example.nfc_reader_01.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.nfc_reader_01.SharedNfcViewModel
import com.example.nfc_reader_01.databinding.FragmentHomeBinding

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var sharedNfcViewModel: SharedNfcViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        sharedNfcViewModel = ViewModelProvider(requireActivity()).get(SharedNfcViewModel::class.java)

        // No hay listeners de botones de escritura, solo de estado
        setupObservers()

        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setupObservers() {
        sharedNfcViewModel.nfcTag.observe(viewLifecycleOwner) { tag ->
            if (tag != null) {
                Toast.makeText(context, "¡Etiqueta NFC detectada!", Toast.LENGTH_SHORT).show()
                binding.statusIcon.setImageResource(com.example.nfc_reader_01.R.drawable.ic_nfc_connected_24)
                binding.statusMessage.text = "¡TAG detectado! Puede realizar acciones."
            } else {
                binding.statusIcon.setImageResource(com.example.nfc_reader_01.R.drawable.ic_nfc_scan_24)
                binding.statusMessage.text = "Aproxime una etiqueta NFC."
            }
        }

        sharedNfcViewModel.writeStatus.observe(viewLifecycleOwner) { status ->
            status?.let {
                Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
                sharedNfcViewModel.setWriteStatus(null)
            }
        }
    }
}
