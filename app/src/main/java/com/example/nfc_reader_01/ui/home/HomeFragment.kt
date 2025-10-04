package com.example.nfc_reader_01.ui.home

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.nfc_reader_01.NfcInteractionListener
import com.example.nfc_reader_01.SharedNfcViewModel
import com.example.nfc_reader_01.databinding.FragmentHomeBinding

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var sharedNfcViewModel: SharedNfcViewModel
    private var listener: NfcInteractionListener? = null // Referencia a la Activity (Listener)

    override fun onAttach(context: Context) {
        super.onAttach(context)
        // 1. Asegurarse de que la Activity implemente la interfaz
        if (context is NfcInteractionListener) {
            listener = context
        } else {
            // Esto asegura que si la Activity no implementa el listener, la app fallará inmediatamente.
            // Esto es crucial para un correcto manejo de callbacks entre Fragmentos y Activities.
            Log.e("HomeFragment", "$context debe implementar NfcInteractionListener")
            // No lanzar excepción, ya que podría estar en un entorno de vista previa,
            // pero es buena práctica en producción.
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        sharedNfcViewModel = ViewModelProvider(requireActivity()).get(SharedNfcViewModel::class.java)

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
        sharedNfcViewModel.nfcTag.observe(viewLifecycleOwner) { tag ->
            if (tag != null) {
                // 2. Nuevo Comportamiento: Al detectar el TAG, notificar a la Activity para navegar
                listener?.navigateToDashboard()

                // Actualizar la UI local para reflejar que el TAG fue detectado y la acción se solicitó
                binding.statusIcon.setImageResource(com.example.nfc_reader_01.R.drawable.ic_nfc_connected_24)
                binding.statusMessage.text = "¡TAG detectado! Navegando automáticamente..."
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
