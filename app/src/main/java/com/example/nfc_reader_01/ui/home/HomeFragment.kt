package com.example.nfc_reader_01.ui.home

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels // Importación necesaria para by activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.nfc_reader_01.NfcInteractionListener
import com.example.nfc_reader_01.SharedNfcViewModel
import com.example.nfc_reader_01.databinding.FragmentHomeBinding
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    // Usar by activityViewModels() para inyectar y obtener la instancia compartida
    private val sharedNfcViewModel: SharedNfcViewModel by activityViewModels()

    private var listener: NfcInteractionListener? = null // Referencia a la Activity (Listener)

    override fun onAttach(context: Context) {
        super.onAttach(context)
        // 1. Asegurarse de que la Activity implemente la interfaz
        if (context is NfcInteractionListener) {
            listener = context
        } else {
            Log.e("HomeFragment", "$context debe implementar NfcInteractionListener")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        // La inyección se hace en la declaración de la propiedad, no aquí.

        setupObservers()

        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onDetach() {
        super.onDetach()
        listener = null // Limpiar la referencia para evitar pérdidas de memoria
    }

    private fun setupObservers() {
        // Corregido: Usar .collect para StateFlow y envolver en coroutines
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // 1. Observa nfcTag (StateFlow)
                sharedNfcViewModel.nfcTag.collect { tag ->
                    if (tag != null) {
                        // Notificar a la Activity para navegar
                        listener?.navigateToDashboard()

                        // Actualizar la UI
                        binding.statusIcon.setImageResource(com.example.nfc_reader_01.R.drawable.ic_nfc_connected_24)
                        binding.statusMessage.text = "¡TAG detectado! Navegando automáticamente..."
                    } else {
                        binding.statusIcon.setImageResource(com.example.nfc_reader_01.R.drawable.ic_nfc_scan_24)
                        binding.statusMessage.text = "Aproxime una etiqueta NFC."
                    }
                }
            }
        }

        // 2. Observa writeStatus (asumiendo que también es un StateFlow<String?>)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                sharedNfcViewModel.writeStatus.collect { status ->
                    status?.let {
                        Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
                        // Nota: La función setWriteStatus() debe estar implementada en el ViewModel
                        // para limpiar el estado después de mostrar el Toast.
                        sharedNfcViewModel.setWriteStatus(null)
                    }
                }
            }
        }
    }
}
